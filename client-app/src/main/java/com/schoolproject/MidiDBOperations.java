package com.schoolproject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository class for managing MIDI files in the PostgreSQL database.
 * Provides save, delete, load, download, exists, and list methods.
 */
public class MidiDBOperations {

    private final MidiDBConnector db;

    public MidiDBOperations(MidiDBConnector dbConnector) {
        this.db = dbConnector;
        ensureTableExists();
    }

    /**
     * Creates the table if it doesn't already exist (and enforces unique
     * filenames).
     */
    private void ensureTableExists() {
        String ddl = """
                    CREATE TABLE IF NOT EXISTS midi_files (
                        id SERIAL PRIMARY KEY,
                        filename TEXT NOT NULL UNIQUE,      -- enforce unique names
                        data BYTEA NOT NULL,
                        uploaded_at TIMESTAMPTZ DEFAULT NOW()
                    );
                    CREATE INDEX IF NOT EXISTS idx_midi_uploaded_at ON midi_files (uploaded_at DESC);
                """;
        try (Connection conn = db.connect(); Statement st = conn.createStatement()) {
            st.execute(ddl);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure table: " + e.getMessage(), e);
        }
    }

    // ---------- HELPERS ----------

    /** Returns true if a row with this exact (case-sensitive) filename exists. */
    public boolean exists(String filename) {
        String sql = "SELECT 1 FROM midi_files WHERE filename = ? LIMIT 1";
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Exists check failed: " + e.getMessage(), e);
        }
    }

    // ---------- CRUD ----------

    /**
     * Saves a MIDI file to the database with the given name. Throws if the name
     * already exists.
     */
    public void save(String filename, Path midiPath) {
        try {
            byte[] bytes = Files.readAllBytes(midiPath);
            String sql = "INSERT INTO midi_files (filename, data, uploaded_at) VALUES (?, ?, ?)";
            try (Connection conn = db.connect();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, filename);
                ps.setBytes(2, bytes);
                ps.setTimestamp(3, Timestamp.from(Instant.now()));
                ps.executeUpdate();
                System.out.println("✅ Saved '" + filename + "' to DB (" + bytes.length + " bytes).");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + midiPath + "\n" + e.getMessage(), e);
        } catch (SQLException e) {
            // 23505 = unique_violation in PostgreSQL
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalStateException("A file named '" + filename + "' already exists.", e);
            }
            throw new RuntimeException("Database insert failed: " + e.getMessage(), e);
        }
    }

    /** Deletes a file by its exact (case-sensitive) name. */
    public boolean delete(String filename) {
        String sql = "DELETE FROM midi_files WHERE filename = ?";
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            int rows = ps.executeUpdate();
            if (rows > 0)
                System.out.println("🗑️ Deleted '" + filename + "' from DB.");
            else
                System.out.println("⚠️ No file named '" + filename + "' found.");
            return rows > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Database delete failed: " + e.getMessage(), e);
        }
    }

    /** Loads a file’s binary data by exact name (case-sensitive). */
    public byte[] load(String filename) {
        String sql = "SELECT data FROM midi_files WHERE filename = ? LIMIT 1";
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] bytes = rs.getBytes("data");
                    System.out.println("📥 Loaded '" + filename + "' (" + bytes.length + " bytes).");
                    return bytes;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database load failed: " + e.getMessage(), e);
        }
        System.out.println("⚠️ No file named '" + filename + "' found.");
        return null;
    }

    /** Downloads the specified MIDI file from DB and saves it locally. */
    public boolean download(String filename, Path destination) {
        byte[] data = load(filename);
        if (data == null)
            return false;
        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.write(destination, data);
            System.out.println("💾 Downloaded '" + filename + "' → " + destination);
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + destination + "\n" + e.getMessage(), e);
        }
    }

    // Search functionality
    public List<String> search(String query, int limit) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT filename FROM midi_files WHERE filename ILIKE ? ORDER BY uploaded_at DESC LIMIT ?";
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    result.add(rs.getString("filename"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
        return result;
    }

    /** Lists all stored MIDI filenames (newest first). */
    public List<String> listAll() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT filename FROM midi_files ORDER BY uploaded_at DESC";
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                result.add(rs.getString("filename"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list files: " + e.getMessage(), e);
        }
        return result;
    }
}
