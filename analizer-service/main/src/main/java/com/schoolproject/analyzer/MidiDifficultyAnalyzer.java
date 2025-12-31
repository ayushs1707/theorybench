package com.schoolproject.analyzer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

public class MidiDifficultyAnalyzer {

    private final ChordAnalysisEngine chordEngine = new ChordAnalysisEngine();

    private String lastPrintedChord;
    private long lastPrintedTick;
    private double lastPrintedTime;

    // -------------------------------------------------------
    // RESULT CLASS
    // -------------------------------------------------------
    public static class AnalysisResult {
        public int maxPolyphony;
        public int noteCount;
        public int chordDifficulty;
        public int rhythmDifficulty;
        public int totalDifficulty;
        public List<String> chordTimeline = new ArrayList<>();

        @Override
        public String toString() {
            return "AnalysisResult{\n" +
                    "  maxPolyphony=" + maxPolyphony + ",\n" +
                    "  noteCount=" + noteCount + ",\n" +
                    "  chordDifficulty=" + chordDifficulty + ",\n" +
                    "  rhythmDifficulty=" + rhythmDifficulty + ",\n" +
                    "  totalDifficulty=" + totalDifficulty + "\n" +
                    "}\n";
        }
    }

    // -------------------------------------------------------
    // FILE ENTRYPOINT
    // -------------------------------------------------------
    public AnalysisResult analyze(File midiFile) {
        try {
            Sequence seq = MidiSystem.getSequence(midiFile);
            return analyzeSequence(seq);
        } catch (Exception e) {
            e.printStackTrace();
            return new AnalysisResult();
        }
    }

    // -------------------------------------------------------
    // BYTE ARRAY ENTRYPOINT (USED BY WEB SERVER)
    // -------------------------------------------------------
    public AnalysisResult analyzeBytes(byte[] midiData) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(midiData);
            Sequence seq = MidiSystem.getSequence(bais);
            return analyzeSequence(seq);
        } catch (Exception e) {
            e.printStackTrace();
            return new AnalysisResult();
        }
    }

    // -------------------------------------------------------
    // INTERNAL CORE ANALYZER
    // -------------------------------------------------------
    private AnalysisResult analyzeSequence(Sequence seq) {

        lastPrintedChord = null;
        lastPrintedTick = -1;
        lastPrintedTime = -1;

        AnalysisResult result = new AnalysisResult();

        int ppq = seq.getResolution();
        long usPerQuarter = detectTempoUSPerQuarter(seq);
        int beatsPerBar = 4;

        Set<Integer> activeNotes = new HashSet<>();
        long lastEventTick = -1;
        int rapidChanges = 0;

        for (Track track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {

                MidiEvent event = track.get(i);
                long tick = event.getTick();
                MidiMessage msg = event.getMessage();

                if (!(msg instanceof ShortMessage sm)) continue;

                // NOTE ON
                if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {

                    int note = sm.getData1();
                    activeNotes.add(note);
                    result.noteCount++;

                    result.maxPolyphony = Math.max(result.maxPolyphony, activeNotes.size());

                    if (lastEventTick != -1 && (tick - lastEventTick) <= 15)
                        rapidChanges++;

                    lastEventTick = tick;

                    if (activeNotes.size() >= 2) {
                        var chord = chordEngine.detect(activeNotes);
                        if (chord != null) {
                            processChordEvent(result, chord.name, tick, ppq, beatsPerBar, usPerQuarter);
                        }
                    }
                }

                // NOTE OFF
                if (sm.getCommand() == ShortMessage.NOTE_OFF ||
                        (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {

                    activeNotes.remove(sm.getData1());
                }
            }
        }

        result.rhythmDifficulty = Math.min(10, rapidChanges / 30);
        result.totalDifficulty =
                result.chordDifficulty +
                        result.rhythmDifficulty +
                        Math.min(10, result.maxPolyphony * 2);

        return result;
    }

    // -------------------------------------------------------
    // TIMELINE
    // -------------------------------------------------------
    private void processChordEvent(AnalysisResult result, String chordName, long tick,
                                   int ppq, int beatsPerBar, long usPerQuarter) {

        if (chordName.equals("unknown") || chordName.equals("interval"))
            return;

        double seconds = tickToSeconds(tick, usPerQuarter, ppq);

        if (tick == lastPrintedTick && chordName.equals(lastPrintedChord))
            return;

        if (lastPrintedTime >= 0 && (seconds - lastPrintedTime) < 0.03)
            return;

        double beat = (double) tick / ppq;
        int bar = (int) (beat / beatsPerBar) + 1;
        double beatInBar = (beat % beatsPerBar) + 1;

        String label = String.format(
                "t=%.2fs, Bar %d, Beat %.2f: %s",
                round2(seconds), bar, round2(beatInBar), chordName
        );

        result.chordTimeline.add(label);
        result.chordDifficulty += computeChordDifficultyName(chordName);

        lastPrintedChord = chordName;
        lastPrintedTick = tick;
        lastPrintedTime = seconds;
    }

    private int computeChordDifficultyName(String name) {
        if (name.endsWith("maj") || name.endsWith("min")) return 1;
        if (name.contains("sus")) return 2;
        if (name.contains("7") && !name.contains("9") &&
                !name.contains("11") && !name.contains("13")) return 3;
        if (name.contains("9")) return 4;
        if (name.contains("11")) return 5;
        if (name.contains("13")) return 6;
        if (name.contains("dim") || name.contains("aug") ||
                name.contains("#") || name.contains("b")) return 5;

        return 1;
    }

    private long detectTempoUSPerQuarter(Sequence seq) {
        long defaultUsPerQuarter = 500_000; // 120 BPM
        try {
            for (Track track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);
                    if (event.getMessage() instanceof MetaMessage meta &&
                            meta.getType() == 0x51) {

                        byte[] d = meta.getData();
                        return ((d[0] & 0xFF) << 16)
                                | ((d[1] & 0xFF) << 8)
                                | (d[2] & 0xFF);
                    }
                }
            }
        } catch (Exception ignore) {}
        return defaultUsPerQuarter;
    }

    private double tickToSeconds(long tick, long usPerQuarter, int ppq) {
        return (tick / (double) ppq) * (usPerQuarter / 1_000_000.0);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
