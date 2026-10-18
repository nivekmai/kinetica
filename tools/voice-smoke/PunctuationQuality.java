package com.kinetica.keyboard.voice;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real pinned CPU inference, through the same text guard and draft used on Android. */
public final class PunctuationQuality {
    static Path root;
    static int runs;
    static void infer(VoiceDraft draft, String label) throws Exception {
        VoiceDraft.PunctuationSnapshot snapshot = Objects.requireNonNull(draft.punctuationSnapshot());
        Path models = root.resolve("assets/punctuation");
        Path cli = root.resolve("host/sherpa-onnx-v1.13.8-linux-x64-shared-no-tts/bin/sherpa-onnx-online-punctuation");
        Path stderr = Files.createTempFile("punctuation-smoke-", ".log");
        Process process = new ProcessBuilder(cli.toString(), "--cnn-bilstm=" + models.resolve("model.int8.onnx"),
            "--bpe-vocab=" + models.resolve("bpe.vocab"), snapshot.getRequest().getInput())
            .redirectError(stderr.toFile()).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int code = process.waitFor();
        String log = Files.readString(stderr); Files.delete(stderr);
        if (code != 0) throw new AssertionError(log);
        if (!draft.applyPunctuation(snapshot, output)) throw new AssertionError("Rejected model output: " + output + "\n" + log);
        String timing = log.lines().filter(s -> s.startsWith("Elapsed seconds:")).findFirst().orElse("");
        System.out.println(label + " | " + timing + " | " + draft.getText());
        runs++;
    }
    static VoiceDraft draft() {
        return new VoiceDraft("", true, "en", FastVoiceFormatting.INSTANCE.getDefaultProtectedWords(), true);
    }
    public static void main(String[] args) throws Exception {
        root = Path.of(args[0]).toAbsolutePath();
        VoiceDraft pause = draft();
        pause.partial("Whenever I pause for");
        infer(pause, "pause fragment");
        if (pause.getText().endsWith("?")) throw new AssertionError("Pinned a live endpoint");
        pause.finish("Whenever I pause for");
        pause.partial("A second");
        infer(pause, "pause continuation");
        if (!pause.getText().contains("pause for a second")) throw new AssertionError("Split continuation");
        pause.finish("A second");
        pause.setPunctuationComplete(true);
        infer(pause, "stopped continuation");
        for (String source : List.of("how are you i am fine thank you", "where are we meeting tomorrow",
            "I am meeting NASA and Alice at 4:15 p.m. tomorrow",
            "this is a test of simple things lets make a list one the first item two the second item three the third item")) {
            VoiceDraft d = draft(); d.finish(source); d.setPunctuationComplete(true);
            infer(d, source);
            if (!d.getOriginal().equals(source)) throw new AssertionError("Changed source");
        }
        VoiceDraft list = draft();
        list.finish("Here is a list one apples two pears");
        if (!list.apply(list.snapshot(3000), "Here is a list.\n1. Apples.\n2. Pears.")) throw new AssertionError("AI fixture rejected");
        list.partial("And some bananas"); infer(list, "preserve rich cleanup");
        if (!list.getText().startsWith("Here is a list.\n1. Apples.\n2. Pears.")) throw new AssertionError("Flattened list");
        VoiceDraft longer = draft();
        longer.finish("we can keep these words together and continue speaking ".repeat(20).trim());
        infer(longer, "longer passage");
        System.out.println("Passed " + runs + " real-model fidelity/context checks. Punctuation choices above are observations, not all correct goldens.");
    }
}
