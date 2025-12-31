package com.schoolproject;

import java.nio.file.Files;
import java.nio.file.Path;

public class MidiDbSmokeTest {
    public static void main(String[] args) throws Exception {
        // 1) Point to a MIDI file your app produced (or any .mid on disk)
        Path src = Path.of("C:\\Users\\Jahleel\\Desktop\\CS Projects\\CS334\\midi-experiment\\test.mid"); // <-- change this
        if (!Files.exists(src)) {
            System.err.println("Missing test file: " + src.toAbsolutePath());
            return;
        }

        // 2) Wire up DB
        MidiDBConnector connector = new MidiDBConnector();
        MidiDBOperations repo = new MidiDBOperations(connector);

        // 3) Save to DB
        String nameInDb = "test.mid"; // case-sensitive name in DB
        repo.save(nameInDb, src);
        System.out.println("Saved to DB: " + nameInDb);

        // 4) Load back and compare sizes
        byte[] fromDb = repo.load(nameInDb);
        if (fromDb == null) {
            System.err.println("Load failed: not found in DB");
            return;
        }
        long srcSize = Files.size(src);
        System.out.println("Local size: " + srcSize + " bytes | DB size: " + fromDb.length + " bytes");

        // 5) Download to a new location
        Path out = Path.of(System.getProperty("user.home"), "Downloads", "test_copy.mid");
        boolean wrote = repo.download(nameInDb, out);
        System.out.println("Downloaded to: " + out.toAbsolutePath() + " (ok=" + wrote + ")");

        // Optional quick integrity check (same length is enough for smoke)
        if (srcSize == fromDb.length) System.out.println("✅ Smoke test passed");
        else System.out.println("⚠️ Size mismatch (still might be ok, just check playback)");
    }
}
