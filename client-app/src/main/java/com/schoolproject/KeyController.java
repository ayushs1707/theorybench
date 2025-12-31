package com.schoolproject;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;

public class KeyController {

    // ---- DB injected from App ----
    private final MidiDBOperations dbOps;

    // ---- MIDI / State ----
    private static final int BASE_MIDI_NOTE = 60; // Middle C
    private final Set<Integer> pressedMidiNotes = new HashSet<>();
    private boolean sustainEnabled = false;
    private final Set<Integer> sustainedNotes = new HashSet<>();

    private Synthesizer synth;
    private MidiChannel channel;

    // Recording fields
    private boolean recording = false;
    private Sequence sequence;
    private Track track;
    private long startTime;

    // Key / chord detection
    private String currentKey;
    private static final Map<String, int[]> ROOT_PRIORITIES = new HashMap<>();
    private int[] rootOrder = new int[]{0, 7, 5, 9, 4, 2, 11, 1, 3, 6, 8, 10}; // C major default

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

    // App will call this: new KeyController(dbOps)
    public KeyController(MidiDBOperations dbOps) {
        this.dbOps = dbOps; // do NOT create/connect DB here
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            channel = synth.getChannels()[0]; // channel 0
            channel.programChange(0); // Acoustic Grand Piano
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Optional: keep backward compatibility if anything still calls new KeyController()
    public KeyController() {
        this(null);
    }

    // --- Key selection for chord detection ---
    public void setKey(String key) {
        this.currentKey = key;
        if (ROOT_PRIORITIES.containsKey(key)) {
            rootOrder = ROOT_PRIORITIES.get(key);
        } else {
            // Chromatic fallback
            rootOrder = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        }
    }

    // ---------------- Recording ----------------
    public void startRecording() {
        try {
            sequence = new Sequence(Sequence.PPQ, 480);
            track = sequence.createTrack();
            startTime = System.currentTimeMillis();
            recording = true;
            System.out.println("🎙️ Recording started...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Legacy-style stop & save (kept in case you use it anywhere)
    public void stopAndSaveRecording(String filename) {
        if (!recording)
            return;
        recording = false;
        try {
            File out = new File(filename.endsWith(".mid") ? filename : filename + ".mid");
            MidiSystem.write(sequence, 1, out);
            System.out.println("💾 Saved MIDI file: " + out.getAbsolutePath());

            if (dbOps != null) {
                dbOps.save(out.getName(), out.toPath());
                System.out.println("✅ Uploaded to DB as: " + out.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // DB-aware save with name (used by the UI dialog)
    public void saveRecordingWithName(String name) {
        if (!recording)
            return; // nothing to save
        if (name == null || name.isBlank())
            throw new RuntimeException("Filename cannot be empty.");

        String filename = name.endsWith(".mid") ? name : name + ".mid";
        try {
            // Optional UX check (DB also enforces UNIQUE)
            if (dbOps != null && dbOps.exists(filename)) {
                throw new RuntimeException("A file named '" + filename + "' already exists. Choose another name.");
            }

            recording = false;
            File out = new File(filename);
            MidiSystem.write(sequence, 1, out);
            System.out.println("💾 Saved MIDI file: " + out.getAbsolutePath());

            if (dbOps != null) {
                dbOps.save(out.getName(), out.toPath()); // stores raw bytes in DB
                System.out.println("✅ Uploaded to DB as: " + out.getName());
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            throw new RuntimeException(msg, e);
        }
    }

    // ---- DB helpers exposed to UI ----
    public java.util.List<String> listRecentFiles(int limit) {
        if (dbOps == null)
            throw new RuntimeException("Database not connected.");
        return dbOps.listAll().stream().limit(Math.max(1, limit)).toList();
    }

    public java.util.List<String> searchFiles(String query, int limit) {
        if (dbOps == null)
            throw new RuntimeException("Database not connected.");
        return dbOps.search(query, limit);
    }

    public void downloadFromDB(String filename) {
        if (dbOps == null)
            throw new RuntimeException("Database not connected.");
        Path dest = Path.of(filename.endsWith(".mid") ? filename : filename + ".mid");
        boolean ok = dbOps.download(filename, dest);
        if (!ok)
            throw new RuntimeException("File not found in DB: " + filename);
    }

    public void downloadFromDBTo(String filename, Path destination) {
        if (dbOps == null)
            throw new RuntimeException("Database not connected.");
        boolean ok = dbOps.download(filename, destination);
        if (!ok)
            throw new RuntimeException("File not found in DB: " + filename);
    }

    public boolean deleteFromDB(String filename) {
        if (dbOps == null)
            throw new RuntimeException("Database not connected.");
        return dbOps.delete(filename);
    }

    public boolean isDatabaseAvailable() {
        return dbOps != null;
    }

    // ---------------- Note / Playback ----------------
    public void noteOnWhite(int whiteIndex) {
        int note = BASE_MIDI_NOTE + whiteKeyToMidiOffset(whiteIndex);
        playNoteOn(note);
    }

    public void noteOffWhite(int whiteIndex) {
        int note = BASE_MIDI_NOTE + whiteKeyToMidiOffset(whiteIndex);
        playNoteOff(note);
    }

    public void noteOnBlack(int whiteIndexBeforeBlack) {
        int note = BASE_MIDI_NOTE + blackKeyToMidiOffset(whiteIndexBeforeBlack);
        playNoteOn(note);
    }

    public void noteOffBlack(int whiteIndexBeforeBlack) {
        int note = BASE_MIDI_NOTE + blackKeyToMidiOffset(whiteIndexBeforeBlack);
        playNoteOff(note);
    }

    public Set<Integer> getPressedNotes() {
        return pressedMidiNotes;
    }

    public void playNoteOn(int note) {
        pressedMidiNotes.add(note);
        if (channel != null) {
            channel.noteOn(note, 90);
        }
        recordEvent(ShortMessage.NOTE_ON, note, 90);
    }

    public void playNoteOff(int note) {
        pressedMidiNotes.remove(note);
        if (sustainEnabled) {
            // keep note in sustained list until pedal released
            sustainedNotes.add(note);
        } else {
            if (channel != null) {
                channel.noteOff(note);
            }
        }
        recordEvent(ShortMessage.NOTE_OFF, note, 90);
    }

    private void recordEvent(int command, int note, int velocity) {
        if (!recording || track == null)
            return;
        try {
            long time = System.currentTimeMillis() - startTime;
            // More tempo-accurate tick calculation (as in your newer version)
            int tick = (int) ((time * 480) / 500); // adjust factor as desired
            ShortMessage msg = new ShortMessage();
            msg.setMessage(command, 0, note, velocity);
            track.add(new MidiEvent(msg, tick));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------- Chords ----------------
    public String getCurrentChordLabel() {
        if (pressedMidiNotes.isEmpty())
            return "—";
        String chord = detectChordName();
        return chord.isEmpty() ? "—" : chord;
    }

    // Full advanced chord-detection logic (merged from your "epic" version)
    private String detectChordName() {
        boolean[] pc = new boolean[12];
        for (int n : pressedMidiNotes) {
            pc[n % 12] = true;
        }

        int pressedCount = countPressed(pc);

        String bestChord = null;
        int bestMatchCount = 0;

        // 3 or fewer notes: triads and basic variants
        if (pressedCount <= 3) {
            for (int i = 0; i < 12; i++) {
                int root = rootOrder[i];

                // Major
                if (isTriad(pc, root, 4, 7)) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + " Major";
                    }
                }
                // Minor
                if (isTriad(pc, root, 3, 7)) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 7});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "m";
                    }
                }
                // Sus2
                if (isTriad(pc, root, 2, 7)) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 2, 7});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "sus2";
                    }
                }
                // Sus4
                if (isTriad(pc, root, 5, 7)) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 5, 7});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "sus4";
                    }
                }
                // Aug
                if (isTriad(pc, root, 4, 8)) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 8});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "aug";
                    }
                }
                // Dim
                if (isTriad(pc, root, 3, 6)) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 6});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "dim";
                    }
                }
            }
            if (bestChord != null) return bestChord;
        }

        // 4-note chords
        else if (pressedCount == 4) {
            for (int i = 0; i < 12; i++) {
                int root = rootOrder[i];
                // maj7
                if (isTriad(pc, root, 4, 7) && pc[(root + 11) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 11});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "maj7";
                    }
                }
                // 7
                if (isTriad(pc, root, 4, 7) && pc[(root + 10) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 10});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "7";
                    }
                }
                // m7
                if (isTriad(pc, root, 3, 7) && pc[(root + 10) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 7, 10});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "m7";
                    }
                }
                // 6
                if (isTriad(pc, root, 4, 7) && pc[(root + 9) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 9});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "6";
                    }
                }
                // m6
                if (isTriad(pc, root, 3, 7) && pc[(root + 9) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 7, 9});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "m6";
                    }
                }
                // add9
                if (isTriad(pc, root, 4, 7) && pc[(root + 14) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 14});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "add9";
                    }
                }
                // madd9
                if (isTriad(pc, root, 3, 7) && pc[(root + 14) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 7, 14});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "madd9";
                    }
                }
                // 7sus4
                if (isTriad(pc, root, 5, 7) && pc[(root + 10) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 5, 7, 10});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "7sus4";
                    }
                }
                // add11
                if (isTriad(pc, root, 4, 7) && pc[(root + 17) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 17});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "add11";
                    }
                }
                // aug7
                if (isTriad(pc, root, 4, 8) && pc[(root + 10) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 8, 10});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "aug7";
                    }
                }
                // m7b5
                if (isTriad(pc, root, 3, 6) && pc[(root + 10) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 6, 10});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "m7b5";
                    }
                }
                // m7#5
                if (isTriad(pc, root, 3, 8) && pc[(root + 10) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 8, 10});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "m7#5";
                    }
                }
                // 7b5
                if (isTriad(pc, root, 4, 6) && pc[(root + 10) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 6, 10});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "7b5";
                    }
                }
                // dim7
                if (isTriad(pc, root, 3, 6) && pc[(root + 9) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 6, 9});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "dim7";
                    }
                }
                // 7#5
                if (isTriad(pc, root, 4, 8) && pc[(root + 10) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 8, 10});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "7#5";
                    }
                }
                // mMaj7
                if (isTriad(pc, root, 3, 7) && pc[(root + 11) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 7, 11});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "mMaj7";
                    }
                }
            }
            if (bestChord != null) return bestChord;
        }

        // 5-note chords
        else if (pressedCount == 5) {
            for (int i = 0; i < 12; i++) {
                int root = rootOrder[i];
                // 9
                if (isTriad(pc, root, 4, 7) && pc[(root + 10) % 12] && pc[(root + 14) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 10, 14});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "9";
                    }
                }
                // 9sus4
                if (isTriad(pc, root, 5, 7) && pc[(root + 10) % 12] && pc[(root + 14) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 5, 7, 10, 14});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "9sus4";
                    }
                }
                // m9
                if (isTriad(pc, root, 3, 7) && pc[(root + 10) % 12] && pc[(root + 14) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 7, 10, 14});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "m9";
                    }
                }
                // maj9
                if (isTriad(pc, root, 4, 7) && pc[(root + 11) % 12] && pc[(root + 14) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 11, 14});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "maj9";
                    }
                }
                // 7#9
                if (isTriad(pc, root, 4, 7) && pc[(root + 10) % 12] && pc[(root + 15) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 10, 15});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "7#9";
                    }
                }
                // 7b9
                if (isTriad(pc, root, 4, 7) && pc[(root + 10) % 12] && pc[(root + 13) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 10, 13});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "7b9";
                    }
                }
            }
            if (bestChord != null) return bestChord;
        }

        // 6-note chords
        else if (pressedCount == 6) {
            for (int i = 0; i < 12; i++) {
                int root = rootOrder[i];
                // 11
                if (isTriad(pc, root, 4, 7)
                        && pc[(root + 10) % 12] && pc[(root + 14) % 12] && pc[(root + 17) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 10, 14, 17});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "11";
                    }
                }
                // m11
                if (isTriad(pc, root, 3, 7)
                        && pc[(root + 10) % 12] && pc[(root + 14) % 12] && pc[(root + 17) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 7, 10, 14, 17});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "m11";
                    }
                }
                // 13 (omit 11th)
                if (isTriad(pc, root, 4, 7)
                        && pc[(root + 10) % 12] && pc[(root + 14) % 12] && pc[(root + 21) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 10, 14, 21});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "13 (omit 11th)";
                    }
                }
            }
            if (bestChord != null) return bestChord;
        }

        // 7-note chords
        else if (pressedCount == 7) {
            for (int i = 0; i < 12; i++) {
                int root = rootOrder[i];
                // 13
                if (isTriad(pc, root, 4, 7)
                        && pc[(root + 10) % 12]
                        && pc[(root + 14) % 12]
                        && pc[(root + 17) % 12]
                        && pc[(root + 21) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 10, 14, 17, 21});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "13";
                    }
                }
                // m13
                if (isTriad(pc, root, 3, 7)
                        && pc[(root + 10) % 12]
                        && pc[(root + 14) % 12]
                        && pc[(root + 17) % 12]
                        && pc[(root + 21) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 3, 7, 10, 14, 17, 21});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "m13";
                    }
                }
                // maj13
                if (isTriad(pc, root, 4, 7)
                        && pc[(root + 11) % 12]
                        && pc[(root + 14) % 12]
                        && pc[(root + 17) % 12]
                        && pc[(root + 21) % 12]) {
                    int matchCount = countMatchingNotes(pc, root, new int[]{0, 4, 7, 11, 14, 17, 21});
                    if (matchCount > bestMatchCount) {
                        bestMatchCount = matchCount;
                        bestChord = noteName(root) + "maj13";
                    }
                }
            }
            if (bestChord != null) return bestChord;
        }

        // Fallback
        int count = countPressed(pc);
        if (count == 1) return noteName(firstPitchClass(pc));
        if (count == 2) return "Interval";
        if (count >= 3) return "Unknown";

        return "";
    }

    // Helper method to count how many notes from chord intervals match pressed notes
    private int countMatchingNotes(boolean[] pc, int root, int[] intervals) {
        int count = 0;
        for (int interval : intervals) {
            if (pc[(root + interval) % 12]) count++;
        }
        return count;
    }

    private boolean isTriad(boolean[] pc, int root, int third, int fifth) {
        return pc[root] && pc[(root + third) % 12] && pc[(root + fifth) % 12];
    }

    private int countPressed(boolean[] pc) {
        int c = 0;
        for (boolean b : pc) if (b) c++;
        return c;
    }

    private int firstPitchClass(boolean[] pc) {
        for (int i = 0; i < 12; i++) if (pc[i]) return i;
        return 0;
    }

    private String noteName(int pc) {
        return switch (pc) {
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

    // ---------------- Sustain ----------------
    public void setSustainEnabled(boolean enabled) {
        sustainEnabled = enabled;
        if (!sustainEnabled) {
            // Turn off any notes that were sustained
            for (int note : sustainedNotes) {
                if (channel != null) {
                    channel.noteOff(note);
                }
            }
            sustainedNotes.clear();
        }
    }

    public boolean isSustainEnabled() {
        return sustainEnabled;
    }

    // ---------------- Helpers ----------------
    private int whiteKeyToMidiOffset(int whiteIndex) {
        int octave = whiteIndex / 7;
        int pos = whiteIndex % 7;
        int[] pattern = {0, 2, 4, 5, 7, 9, 11};
        return octave * 12 + pattern[pos];
    }

    private int blackKeyToMidiOffset(int whiteIndex) {
        int octave = whiteIndex / 7;
        int pos = whiteIndex % 7;
        int[] pattern = {1, 3, -1, 6, 8, 10, -1};
        int semitone = pattern[pos];
        return octave * 12 + semitone;
    }
}
