package com.kinetica.keyboard.voice;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Replay exported callbacks through the real draft implementation, without Android or an LLM. */
public final class ReplayTrace {
    public static void main(String[] args) throws Exception {
        VoiceDraft draft = new VoiceDraft("");
        Map<String, VoiceDraft.Snapshot> jobs = new HashMap<>();
        String before = "";
        int line = 0;
        for (String row : Files.readAllLines(Path.of(args[0]))) {
            line++;
            String[] fields = row.split("\t", -1);
            for (int i = 1; i < fields.length; i++) fields[i] = new String(Base64.getDecoder().decode(fields[i]), StandardCharsets.UTF_8);
            switch (fields[0]) {
                case "BEFORE": before = fields[1]; draft = new VoiceDraft(before); break;
                case "FORMATTING": draft = fields.length > 4
                    ? new VoiceDraft(before, Boolean.parseBoolean(fields[1]), fields[2], FastVoiceFormatting.INSTANCE.protectedWords(fields[3]), Boolean.parseBoolean(fields[4]))
                    : fields.length > 3
                    ? new VoiceDraft(before, Boolean.parseBoolean(fields[1]), fields[2], FastVoiceFormatting.INSTANCE.protectedWords(fields[3]))
                    : new VoiceDraft(before, Boolean.parseBoolean(fields[1]), fields[2]); break;
                case "PARTIAL": draft.partial(fields[1]); break;
                case "PUNCTUATION_COMPLETE": draft.setPunctuationComplete(Boolean.parseBoolean(fields[1])); break;
                case "PUNCTUATION": {
                    PunctuationText.Request request = new PunctuationText.Request(fields[1], fields[2], fields[3], Integer.parseInt(fields[4]));
                    VoiceDraft.PunctuationSnapshot snapshot = new VoiceDraft.PunctuationSnapshot(request, Long.parseLong(fields[5]));
                    boolean accepted = draft.applyPunctuation(snapshot, fields[6]);
                    if (accepted != Boolean.parseBoolean(fields[7])) throw new AssertionError("Punctuation mismatch at line " + line);
                    break;
                }
                case "FINAL": draft.finish(fields[1]); break;
                case "SNAPSHOT": {
                    VoiceDraft.Snapshot s = draft.snapshot(3000);
                    if (s == null || !s.getInput().equals(fields[2]) || !s.getOriginal().equals(fields[3]))
                        throw new AssertionError("Snapshot mismatch at line " + line);
                    jobs.put(fields[1], s); break;
                }
                case "APPLY": {
                    VoiceDraft.Snapshot s = jobs.get(fields[1]);
                    boolean accepted = s != null && draft.apply(s, fields[2]);
                    if (accepted != Boolean.parseBoolean(fields[3])) throw new AssertionError("Acceptance mismatch at line " + line);
                    break;
                }
                case "UNDO": draft.undo(); break;
                case "EXPECT": if (!draft.getText().equals(fields[1])) throw new AssertionError("Draft mismatch at line " + line); break;
                default: throw new AssertionError("Unknown replay operation");
            }
        }
        System.out.println("Replayed " + line + " operations successfully.");
    }
}
