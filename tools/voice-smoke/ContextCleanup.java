package com.kinetica.keyboard.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.kinetica.keyboard.engine.KineticaConstants;

/** Actual app request builder, native backend and acceptance guard. No formatting postprocessor. */
public final class ContextCleanup {
    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        var backend = new LocalCleanup();
        String instruction = Files.readString(Path.of(args[2]));
        String[] cases = {
            "This is a test of me saying a time like 4 15 PM",
            "if I Pause once and then start speaking again,It's unable to fix the capitalization From the first pause",
            "a sentence should and with a period or a question mark",
            "can we meet tomorrow,or would Friday be better",
            "I like bread and butter",
            "my name is May and I work at NASA",
            "I was going to",
            "I was relieved because It arrived And I could leave",
            "make a shopping list one apples two bananas three milk",
            "meet at four fifteen"
        };
        long stale = backend.nextRequest();
        backend.nextRequest();
        var initial = CleanupInstructions.INSTANCE.request(instruction, cases[0], new CleanupContext("", ""), cases[0]);
        if (!decode(generate(backend, stale, args[1], initial)).getCode().equals("CANCELLED")) throw new AssertionError("Stale native request ran");
        if (!decode(generate(backend, backend.nextRequest(), "/nonexistent/model.gguf", initial)).getCode().equals("MODEL_ERROR"))
            throw new AssertionError("Missing model accepted");
        for (String raw : cases) {
            var request = CleanupInstructions.INSTANCE.request(instruction, raw, new CleanupContext("", ""), raw);
            long start = System.nanoTime();
            byte[] output = generate(backend, backend.nextRequest(), args[1], request);
            CleanupResult result = decode(output);
            String text = result.getCode() + ": " + result.getPayload();
            System.out.printf("Raw: %s%nOutput (%.2fs): %s%nAccepted: %s%n%n", raw,
                (System.nanoTime()-start)/1e9, text, result.getText() != null && CleanupGuard.INSTANCE.accepts(raw, result.getText()));
        }
        byte[] system = initial.getSystem().getBytes(StandardCharsets.UTF_8);
        byte[] user = initial.getUser().getBytes(StandardCharsets.UTF_8);
        assertCode("INPUT_LIMIT", backend.generate(backend.nextRequest(), args[1], system, user, 1, 100, 30000));
        assertCode("OUTPUT_LIMIT", backend.generate(backend.nextRequest(), args[1], system, user, 3072, 1, 30000));
        assertCode("TIMEOUT", backend.generate(backend.nextRequest(), args[1], system, user, 3072, 1536, 1));
        long cancellable = backend.nextRequest();
        Thread cancellation = new Thread(() -> { try { Thread.sleep(100); } catch (InterruptedException e) { throw new RuntimeException(e); } backend.nextRequest(); });
        cancellation.start();
        assertCode("CANCELLED", generate(backend, cancellable, args[1], initial));
        cancellation.join();
        assertCode("SUCCESS", generate(backend, backend.nextRequest(), args[1], initial));
        backend.releaseEngine();
        System.out.println("Native status, cancellation, context reuse and release checks passed.");
    }
    private static CleanupResult decode(byte[] bytes) { return CleanupResult.Companion.decode(bytes); }
    private static void assertCode(String code, byte[] bytes) {
        if (!decode(bytes).getCode().equals(code)) throw new AssertionError("Expected " + code + ", got " + decode(bytes));
    }
    private static byte[] generate(LocalCleanup backend, long ticket, String path, CleanupRequest request) {
        return backend.generate(ticket, path, request.getSystem().getBytes(StandardCharsets.UTF_8),
            request.getUser().getBytes(StandardCharsets.UTF_8), KineticaConstants.VOICE_LLM_INPUT_TOKENS,
            KineticaConstants.VOICE_LLM_OUTPUT_TOKENS, KineticaConstants.VOICE_LLM_TIMEOUT_MS);
    }
}
