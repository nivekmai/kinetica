package com.kinetica.keyboard.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Compare cached inference to a fresh context, including prompt changes and recovery. */
public final class PrefixReuse {
    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        var backend = new LocalCleanup();
        String instruction = Files.readString(Path.of(args[2]));
        String[] texts = {
            "This is a test of me saying a time like 4 15 PM",
            "This is a test of me saying a time like 4 15 PM And I would like to meet tomorrow",
            "This is a test of me saying a time like 4 15 PM",
            "This is a test of me saying a time like 4 15 PM",
            "can we meet tomorrow or would Friday be better"
        };
        byte[][] expected = new byte[texts.length][];
        double[] cold = new double[texts.length];
        try {
            for (int i = 0; i < texts.length; i++) {
                backend.releaseEngine();
                long start = System.nanoTime();
                expected[i] = run(backend, args[1], instructionFor(instruction, i), texts[i], 30000);
                cold[i] = (System.nanoTime() - start) / 1e9;
                requireSuccess(expected[i]);
            }
            backend.releaseEngine();
            for (int i = 0; i < texts.length; i++) {
                long start = System.nanoTime();
                byte[] actual = run(backend, args[1], instructionFor(instruction, i), texts[i], 30000);
                double seconds = (System.nanoTime() - start) / 1e9;
                if (!Arrays.equals(expected[i], actual)) throw new AssertionError("Cached output differs for case " + i +
                    "\nFresh: " + new String(expected[i], StandardCharsets.UTF_8) +
                    "\nCached: " + new String(actual, StandardCharsets.UTF_8));
                System.out.printf("Case %d exact output PASS: fresh %.2fs, sequential %.2fs%n", i, cold[i], seconds);
            }
            var timedOut = CleanupResult.Companion.decode(run(backend, args[1], instruction, texts[0], 1));
            if (!timedOut.getCode().equals("TIMEOUT")) throw new AssertionError(timedOut);
            if (!Arrays.equals(expected[0], run(backend, args[1], instruction, texts[0], 30000)))
                throw new AssertionError("Output changed after timed-out decode");
            System.out.println("Timeout recovery exact output PASS. Fresh times include model loading; these are host measurements.");
        } finally { backend.releaseEngine(); }
    }
    private static String instructionFor(String instruction, int i) {
        return i == 4 ? "Fix punctuation and capitalization. Return only the edited transcript." : instruction;
    }
    private static byte[] run(LocalCleanup backend, String path, String instruction, String raw, long timeout) {
        var request = CleanupInstructions.INSTANCE.request(instruction, raw, new CleanupContext("", ""), raw);
        return backend.generate(backend.nextRequest(), path, request.getSystem().getBytes(StandardCharsets.UTF_8),
            request.getUser().getBytes(StandardCharsets.UTF_8), 3072, 1536, timeout);
    }
    private static void requireSuccess(byte[] result) {
        if (CleanupResult.Companion.decode(result).getText() == null)
            throw new AssertionError(new String(result, StandardCharsets.UTF_8));
    }
}
