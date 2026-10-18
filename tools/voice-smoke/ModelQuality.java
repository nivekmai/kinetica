package com.kinetica.keyboard.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;

/** Small model-quality gate: actual shipped request builder, model, decoder and guard. */
public final class ModelQuality {
    private record Case(String name, String raw, Predicate<String> expected) {}
    private static boolean numbered(String text, String... items) {
        for (int i = 0; i < items.length; i++) {
            if (!java.util.regex.Pattern.compile("(?im)^" + (i + 1) + "\\.\\s+" +
                    java.util.regex.Pattern.quote(items[i]) + "\\.?\\s*$").matcher(text).find()) return false;
        }
        return true;
    }
    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        var backend = new LocalCleanup();
        String instruction = Files.readString(Path.of(args[2]));
        Case[] cases = {
            new Case("recorded numbered list",
                "This is a test of simple things let's make a list One the first item Two the second item Three the third item",
                s -> s.startsWith("This is a test of simple things. Let's make a list.") &&
                    numbered(s, "The first item", "The second item", "The third item")),
            new Case("different list", "make a shopping list one apples two bananas three milk",
                s -> s.startsWith("Make a shopping list") && numbered(s, "Apples", "Bananas", "Milk")),
            new Case("quantities are not list markers", "I bought one apple two bananas and three oranges",
                s -> !s.contains("\n") && s.toLowerCase(Locale.ROOT).contains("one apple") &&
                    s.contains("two bananas") && s.contains("three oranges")),
            new Case("time with introduction", "This is a test of me saying a time like 4 15 PM",
                s -> s.startsWith("This is a test of me saying a time like ") && s.contains("4:15")),
            new Case("sentence continuation", "I was relieved because It arrived And I could leave",
                s -> s.contains("because it arrived") && s.contains("and I could leave")),
            new Case("recognition error", "a sentence should and with a period or a question mark",
                s -> s.contains("should end with a period or a question mark")),
            new Case("question", "can we meet tomorrow,or would Friday be better",
                s -> s.endsWith("?") && s.contains(", or")),
            new Case("names and acronyms", "my name is May and I work at NASA",
                s -> s.contains("May") && s.contains("NASA")),
        };
        int failures = 0;
        try {
            for (Case c : cases) {
                var request = CleanupInstructions.INSTANCE.request(instruction, c.raw, new CleanupContext("", ""), c.raw);
                long start = System.nanoTime();
                var result = CleanupResult.Companion.decode(backend.generate(backend.nextRequest(), args[1],
                    request.getSystem().getBytes(StandardCharsets.UTF_8), request.getUser().getBytes(StandardCharsets.UTF_8),
                    3072, 1536, 30000));
                String output = result.getText();
                var draft = new VoiceDraft("", true, "en-US");
                draft.finish(c.raw);
                boolean accepted = output != null && draft.apply(draft.snapshot(3000), output);
                boolean pass = accepted && c.expected.test(draft.getText()) &&
                    Character.isUpperCase(draft.getText().charAt(0));
                System.out.printf("%s: %s (%.2fs, accepted=%s)%n%s%n%n", c.name, pass ? "PASS" : "FAIL",
                    (System.nanoTime() - start) / 1e9, accepted, result.getPayload());
                if (accepted && !draft.getText().equals(output)) System.out.println("Displayed: " + draft.getText() + "\n");
                if (!pass) failures++;
            }
        } finally { backend.releaseEngine(); }
        if (failures != 0) throw new AssertionError(failures + " model-quality cases failed");
        System.out.println("All " + cases.length + " model-quality cases passed.");
    }
}
