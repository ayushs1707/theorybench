package com.schoolproject.analyzer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChordAnalysisEngine {

    // ======== PUBLIC RESULT CLASS ========
    public static class ChordResult {
        public final String name; // standardized chord name
        public final int rootPC;  // pitch class of the root
        public final Set<Integer> pitchClasses; // raw pcs

        public ChordResult(String name, int rootPC, Set<Integer> pcs) {
            this.name = name;
            this.rootPC = rootPC;
            this.pitchClasses = pcs;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // ======== ROOT PRIORITY MAP (from your version) ========
    private static final Map<String, int[]> ROOT_PRIORITIES = new HashMap<>();
    static {
        ROOT_PRIORITIES.put("C",  new int[]{0, 7, 5, 9, 4, 2, 11, 1, 3, 6, 8, 10});
        ROOT_PRIORITIES.put("C#", new int[]{1, 8, 6, 10, 5, 3, 0, 2, 4, 7, 9, 11});
        ROOT_PRIORITIES.put("D",  new int[]{2, 9, 7, 11, 6, 4, 1, 3, 5, 8, 10, 0});
        ROOT_PRIORITIES.put("D#", new int[]{3, 10, 8, 0, 7, 5, 2, 4, 6, 9, 11, 1});
        ROOT_PRIORITIES.put("E",  new int[]{4, 11, 9, 1, 8, 6, 3, 5, 7, 10, 0, 2});
        ROOT_PRIORITIES.put("F",  new int[]{5, 0, 10, 2, 9, 7, 4, 6, 8, 11, 1, 3});
        ROOT_PRIORITIES.put("F#", new int[]{6, 1, 11, 3, 10, 8, 5, 7, 9, 0, 2, 4});
        ROOT_PRIORITIES.put("G",  new int[]{7, 2, 0, 4, 11, 9, 6, 8, 10, 1, 3, 5});
        ROOT_PRIORITIES.put("G#", new int[]{8, 3, 1, 5, 0, 10, 7, 9, 11, 2, 4, 6});
        ROOT_PRIORITIES.put("A",  new int[]{9, 4, 2, 6, 1, 11, 8, 10, 0, 3, 5, 7});
        ROOT_PRIORITIES.put("A#", new int[]{10, 5, 3, 7, 2, 0, 9, 11, 1, 4, 6, 8});
        ROOT_PRIORITIES.put("B",  new int[]{11, 6, 4, 8, 3, 1, 10, 0, 2, 5, 7, 9});
        // minor keys
        ROOT_PRIORITIES.put("Cm", new int[]{0, 5, 7, 2, 9, 4, 11, 1, 3, 6, 8, 10});
        ROOT_PRIORITIES.put("C#m",new int[]{1, 6, 8, 3, 10, 5, 0, 2, 4, 7, 9, 11});
        ROOT_PRIORITIES.put("Dm", new int[]{2, 7, 9, 4, 11, 6, 3, 5, 8, 10, 0, 1});
        ROOT_PRIORITIES.put("D#m",new int[]{3, 8, 10, 5, 0, 7, 4, 6, 9, 11, 1, 2});
        ROOT_PRIORITIES.put("Em", new int[]{4, 9, 11, 6, 1, 8, 5, 7, 10, 0, 2, 3});
        ROOT_PRIORITIES.put("Fm", new int[]{5, 10, 0, 7, 2, 9, 6, 8, 11, 1, 3, 4});
        ROOT_PRIORITIES.put("F#m",new int[]{6, 11, 1, 8, 3, 10, 7, 9, 0, 2, 4, 5});
        ROOT_PRIORITIES.put("Gm", new int[]{7, 0, 2, 9, 4, 11, 8, 10, 1, 3, 5, 6});
        ROOT_PRIORITIES.put("G#m",new int[]{8, 1, 3, 10, 5, 0, 9, 11, 2, 4, 6, 7});
        ROOT_PRIORITIES.put("Am", new int[]{9, 2, 4, 11, 6, 1, 10, 0, 3, 5, 7, 8});
        ROOT_PRIORITIES.put("A#m",new int[]{10, 3, 5, 0, 7, 2, 11, 1, 4, 6, 8, 9});
        ROOT_PRIORITIES.put("Bm", new int[]{11, 4, 6, 1, 8, 3, 0, 2, 5, 7, 9, 10});
    }

    private int[] rootOrder = ROOT_PRIORITIES.get("C"); // default root ordering

    public void setKey(String keyName) {
        if (ROOT_PRIORITIES.containsKey(keyName)) {
            rootOrder = ROOT_PRIORITIES.get(keyName);
        }
    }

    // ======== CHORD TEMPLATES (STANDARDIZED) ========
    private static class Template {
        final String label;
        final int[] intervals; // 0 always the root

        Template(String label, int... intervals) {
            this.label = label;
            this.intervals = intervals;
        }
    }

    private static final List<Template> TEMPLATES = List.of(
            // triads
            new Template("maj", 0,4,7),
            new Template("min", 0,3,7),
            new Template("sus2",0,2,7),
            new Template("sus4",0,5,7),
            new Template("dim",0,3,6),
            new Template("aug",0,4,8),

            // 7th chords
            new Template("7",   0,4,7,10),
            new Template("maj7",0,4,7,11),
            new Template("m7",  0,3,7,10),
            new Template("mMaj7",0,3,7,11),
            new Template("7sus4",0,5,7,10),

            // 6th
            new Template("6",0,4,7,9),
            new Template("m6",0,3,7,9),

            // 9th
            new Template("9",0,4,7,10,14),
            new Template("m9",0,3,7,10,14),
            new Template("maj9",0,4,7,11,14),
            new Template("7b9",0,4,7,10,13),
            new Template("7#9",0,4,7,10,15),

            // 11th
            new Template("11",0,4,7,10,14,17),
            new Template("m11",0,3,7,10,14,17),

            // 13th
            new Template("13",0,4,7,10,14,17,21),
            new Template("m13",0,3,7,10,14,17,21),
            new Template("maj13",0,4,7,11,14,17,21)
    );

    // ======== MAIN DETECTION ========
    public ChordResult detect(Set<Integer> notes) {
        if (notes == null || notes.isEmpty()) return null;

        // Build pitch class boolean array
        boolean[] pc = new boolean[12];
        for (int n : notes) pc[n % 12] = true;

        int pressedCount = count(pc);
        if (pressedCount == 1) return singleNote(pc);

        // Try roots in your priority order
        for (int root : rootOrder) {
            if (!pc[root]) continue; // root needs to be one of the notes

            for (Template temp : TEMPLATES) {
                if (matches(pc, root, temp.intervals)) {
                    return new ChordResult(noteName(root) + temp.label, root, notes);
                }
            }
        }

        // fallback
        if (pressedCount == 2) return new ChordResult("interval", -1, notes);
        if (pressedCount >= 3) return new ChordResult("unknown", -1, notes);
        return null;
    }

    private boolean matches(boolean[] pc, int root, int[] intervals) {
        for (int interval : intervals) {
            int idx = (root + interval) % 12;
            if (!pc[idx]) return false;
        }
        return true;
    }

    private ChordResult singleNote(boolean[] pc) {
        for (int i = 0; i < 12; i++) if (pc[i]) return new ChordResult(noteName(i), i, Set.of(i));
        return null;
    }

    private int count(boolean[] pc) {
        int c = 0;
        for (boolean b : pc) if (b) c++;
        return c;
    }

    private String noteName(int pc) {
        return switch(pc) {
            case 0 -> "C";
            case 1 -> "C#";
            case 2 -> "D";
            case 3 -> "D#";
            case 4 -> "E";
            case 5 -> "F";
            case 6 -> "F#";
            case 7 -> "G";
            case 8 -> "G#";
            case 9 -> "A";
            case 10 -> "A#";
            case 11 -> "B";
            default -> "?";
        };
    }
}
