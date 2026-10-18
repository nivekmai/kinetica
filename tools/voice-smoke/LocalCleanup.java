package com.kinetica.keyboard.voice;

/** JNI-only host shim. Keep before Android classes on the host classpath. */
public final class LocalCleanup {
    native long nextRequest();
    native void releaseEngine();
    native byte[] generate(long ticket, String path, byte[] system, byte[] prompt,
                           int inputLimit, int outputLimit, long timeoutMs);
    public static void main(String[] args) throws Exception { ContextCleanup.main(args); }
}
