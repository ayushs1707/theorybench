package com.schoolproject;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Handles database connectivity for the MIDI application.
 * Loads PostgreSQL credentials from application.properties.
 */
public class MidiDBConnector {

    private static final String URL;
    private static final String USER;
    private static final String PASS;

    static {
        Properties props = new Properties();

        try (InputStream in = MidiDBConnector.class.getResourceAsStream("/application.properties")) {

            if (in == null) {
                throw new RuntimeException(
                    "Missing configuration file: application.properties " +
                    "(must be under src/main/resources/)"
                );
            }

            props.load(in);

        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load database configuration: " + e.getMessage(), e
            );
        }

        URL  = props.getProperty("db.url");
        USER = props.getProperty("db.user");
        PASS = props.getProperty("db.pass");

        if (URL == null || USER == null || PASS == null) {
            throw new RuntimeException(
                "Missing required DB properties in application.properties: " +
                "db.url, db.user, db.pass"
            );
        }
    }

    /**
     * Opens and returns a JDBC connection using loaded properties.
     */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /**
     * Manual test entry point.
     */
    public static void main(String[] args) {
        try (Connection conn = new MidiDBConnector().connect()) {
            System.out.println("✅ Connection successful: " + conn.getMetaData().getURL());
        } catch (SQLException e) {
            System.err.println("❌ Connection failed: " + e.getMessage());
        }
    }
}
