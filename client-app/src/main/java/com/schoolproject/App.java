package com.schoolproject;

import java.sql.Connection;
import java.sql.SQLException;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Stage;

public class App extends Application {

    // DB connector + operations shared for the whole app
    private MidiDBConnector connector;
    private MidiDBOperations dbOps;

    @Override
    public void start(Stage stage) {
        // Create connector once
        connector = new MidiDBConnector();

        // Try to create DB ops; if it fails (DB offline / bad creds),
        // keep dbOps = null and let the UI still start.
        try {
            dbOps = new MidiDBOperations(connector);
        } catch (RuntimeException ex) {
            System.err.println("WARNING: DB initialization failed: " + ex.getMessage());
            dbOps = null; // app can still run; DB features will show errors when used
        }

        // === Main Menu Layout ===
        VBox menuLayout = new VBox(20);
        menuLayout.setAlignment(Pos.CENTER);
        menuLayout.setPadding(new Insets(40));

        // Background Gradient
        BackgroundFill bgFill = new BackgroundFill(
                new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#1e1e2e")),
                        new Stop(1, Color.web("#2f2f4f"))),
                CornerRadii.EMPTY, Insets.EMPTY);
        menuLayout.setBackground(new Background(bgFill));

        // Buttons
        Button startBtn = new Button("🎹 Start Keyboard");
        Button quitBtn  = new Button("❌ Quit");

        String btnStyle = "-fx-font-size: 16px; -fx-background-color: #333; "
                + "-fx-text-fill: white; -fx-background-radius: 8px;";
        startBtn.setStyle(btnStyle);
        quitBtn.setStyle(btnStyle);

        // DB Status Label
        Label dbStatus = new Label("Checking database status...");
        dbStatus.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        // Run a lightweight connectivity check
        updateDbStatus(dbStatus);

        // Start: create a controller wired to the (possibly null) dbOps
        startBtn.setOnAction(e -> {
            KeyController controller = new KeyController(dbOps);
            new KeyboardUI(stage, controller).show();
        });

        quitBtn.setOnAction(e -> stage.close());

        menuLayout.getChildren().addAll(startBtn, quitBtn, dbStatus);

        // === Scene Setup ===
        Scene scene = new Scene(menuLayout, 800, 400);
        stage.setScene(scene);
        stage.setTitle("🎼 MIDI Keyboard - Main Menu");
        stage.show();
    }

    /** Try a short-lived DB connection and update the label. */
    private void updateDbStatus(Label statusLabel) {
        try (Connection conn = connector.connect()) {
            if (conn != null && !conn.isClosed()) {
                statusLabel.setText("Database: Online");
                statusLabel.setStyle("-fx-text-fill: #55dd55; -fx-font-size: 12px;");
            } else {
                statusLabel.setText("Database: Offline");
                statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
            }
        } catch (SQLException ex) {
            statusLabel.setText("Database: Offline");
            statusLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12px;");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
