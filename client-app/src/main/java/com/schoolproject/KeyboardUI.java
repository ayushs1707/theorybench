package com.schoolproject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class KeyboardUI {

    private Label chordLabel;

    private final KeyController controller;
    private final Stage stage;

    private final List<Rectangle> whiteKeys = new ArrayList<>();
    private final List<Rectangle> blackKeys = new ArrayList<>();

    private final HBox chordHistoryRow = new HBox(10); // scrollable log row
    private final ScrollPane chordScroll = new ScrollPane(chordHistoryRow);

    // Piano key layout constants
    private static final int WHITE_KEYS = 25;
    private static final int WHITE_KEY_WIDTH = 40;
    private static final int WHITE_KEY_HEIGHT = 200;
    private static final int BLACK_KEY_WIDTH = 25;
    private static final int BLACK_KEY_HEIGHT = 120;
    private static final int BASE_MIDI_NOTE = 60; // middle C (C4)

    private static final boolean[] HAS_BLACK_KEY = {
            true, true, false, true, true, true, false,
            true, true, false, true, true, true, false,
            true, true, false, true, true, true, false,
            true, true, false, false
    };

    public KeyboardUI(Stage stage, KeyController controller) {
        this.stage = stage;
        this.controller = controller;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setPrefSize(1100, 550);

        // Gradient background
        BackgroundFill bgFill = new BackgroundFill(
                new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#1e1e2e")),
                        new Stop(1, Color.web("#2f2f4f"))),
                CornerRadii.EMPTY, Insets.EMPTY);
        root.setBackground(new Background(bgFill));

        // Top Bar
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);

        chordLabel = new Label("🎵 Current Chord: —");
        chordLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #FFD700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backButton     = new Button("⬅ Back");
        Button saveButton     = new Button("💾 Save");
        Button showFilesButton = new Button("📂 Show Files");
        Button downloadButton = new Button("⬇️ Download");
        Button deleteButton   = new Button("🗑 Delete");
        Button quitButton     = new Button("❌ Quit");
        Button recordButton   = new Button("⏺️ Record");
        Button keyButton      = new Button("🎼 Key");

        // Key-selection context menu
        ContextMenu keyMenu = new ContextMenu();
        String[] keys = {
                "C", "C#", "D", "D#", "E", "F", "F#", "G",
                "G#", "A", "A#", "B",
                "Cm", "C#m", "Dm", "D#m", "Em", "Fm", "F#m",
                "Gm", "G#m", "Am", "A#m", "Bm"
        };
        for (String k : keys) {
            MenuItem item = new MenuItem(k);
            item.setOnAction(e -> {
                keyButton.setText("Key: " + k);
                controller.setKey(k);
            });
            keyMenu.getItems().add(item);
        }

        keyButton.setOnAction(e -> keyMenu.show(keyButton, Side.BOTTOM, 0, 0));

        // Sustain
        ToggleButton sustainButton = new ToggleButton("🎹 Sustain");
        sustainButton.setStyle("-fx-font-size: 14px; -fx-background-color: #333; -fx-text-fill: white;");
        sustainButton.setOnAction(e -> controller.setSustainEnabled(sustainButton.isSelected()));
        topBar.getChildren().add(sustainButton);

        String buttonStyle = "-fx-font-size: 14px; -fx-background-color: #333; "
                + "-fx-text-fill: white; -fx-background-radius: 6px;";
        backButton.setStyle(buttonStyle);
        saveButton.setStyle(buttonStyle);
        quitButton.setStyle(buttonStyle);
        recordButton.setStyle(buttonStyle);
        showFilesButton.setStyle(buttonStyle);
        downloadButton.setStyle(buttonStyle);
        deleteButton.setStyle(buttonStyle);
        keyButton.setStyle(buttonStyle);

        backButton.setOnAction(e -> new App().start(stage));
        quitButton.setOnAction(e -> stage.close());
        recordButton.setOnAction(e -> controller.startRecording());

        // Save with name + DB/local feedback
        saveButton.setOnAction(e -> {
            while (true) {
                TextInputDialog dlg = new TextInputDialog("take_" + System.currentTimeMillis());
                dlg.setTitle("Save Recording");
                dlg.setHeaderText("Enter a unique name for your MIDI file");
                dlg.setContentText("Filename:");

                Optional<String> result = dlg.showAndWait();
                if (result.isEmpty())
                    return; // user pressed cancel

                try {
                    controller.saveRecordingWithName(result.get());

                    String finalName = result.get().endsWith(".mid")
                            ? result.get()
                            : result.get() + ".mid";

                    Alert ok = new Alert(AlertType.INFORMATION);
                    ok.setHeaderText(null);

                    if (controller.isDatabaseAvailable()) {
                        ok.setContentText("Saved and uploaded to cloud as: " + finalName);
                    } else {
                        ok.setContentText(
                                "Saved locally only (cloud database unavailable).\nFile name: " + finalName);
                    }

                    ok.showAndWait();
                    break; // success, exit the loop
                } catch (RuntimeException ex) {
                    Alert err = new Alert(AlertType.ERROR);
                    err.setHeaderText("Save failed");
                    err.setContentText(ex.getMessage());
                    err.showAndWait();
                    // loop again automatically for a new name
                }
            }
        });

        // Show files dialog (DB)
        showFilesButton.setOnAction(e -> {
            TextInputDialog q = new TextInputDialog("");
            q.setTitle("Show Files");
            q.setHeaderText("Enter a search term (leave blank for recent 10)");
            q.setContentText("Search:");
            Optional<String> qres = q.showAndWait();
            if (qres.isEmpty())
                return;

            List<String> names;
            try {
                String Q = qres.get().trim();
                names = Q.isEmpty() ? controller.listRecentFiles(10)
                        : controller.searchFiles(Q, 10);
            } catch (RuntimeException ex) {
                Alert err = new Alert(AlertType.ERROR);
                err.setHeaderText("Error loading files");
                err.setContentText(ex.getMessage());
                err.showAndWait();
                return;
            }

            String body = names.isEmpty() ? "(none found)" : String.join("\n", names);
            Alert info = new Alert(AlertType.INFORMATION);
            info.setTitle("Files in Database");
            info.setHeaderText("Results" + (names.isEmpty() ? "" : " (" + names.size() + ")"));
            info.setContentText(body);
            info.showAndWait();
        });

        // Download from DB to chosen local path
        downloadButton.setOnAction(e -> {
            try {
                List<String> recent = controller.listRecentFiles(10);
                String chosenName;

                if (recent.isEmpty()) {
                    TextInputDialog nameDlg = new TextInputDialog("");
                    nameDlg.setTitle("Download MIDI");
                    nameDlg.setHeaderText("No recent files found.\nEnter the exact filename to download from DB:");
                    nameDlg.setContentText("Filename (e.g., my_take.mid):");
                    Optional<String> typed = nameDlg.showAndWait();
                    if (typed.isEmpty() || typed.get().trim().isEmpty())
                        return;
                    chosenName = typed.get().trim();
                } else {
                    final String CUSTOM = "⎆ Enter another name…";
                    List<String> options = new java.util.ArrayList<>(recent);
                    options.add(CUSTOM);

                    ChoiceDialog<String> chooser = new ChoiceDialog<>(options.get(0), options);
                    chooser.setTitle("Download MIDI");
                    chooser.setHeaderText("Choose a recent file or enter a different name");
                    chooser.setContentText("Select file:");

                    Optional<String> picked = chooser.showAndWait();
                    if (picked.isEmpty())
                        return;

                    if (picked.get().equals(CUSTOM)) {
                        TextInputDialog nameDlg = new TextInputDialog("");
                        nameDlg.setTitle("Download MIDI");
                        nameDlg.setHeaderText("Enter the exact filename to download from DB:");
                        nameDlg.setContentText("Filename (e.g., my_take.mid):");
                        Optional<String> typed = nameDlg.showAndWait();
                        if (typed.isEmpty() || typed.get().trim().isEmpty())
                            return;
                        chosenName = typed.get().trim();
                    } else {
                        chosenName = picked.get();
                    }
                }

                FileChooser fc = new FileChooser();
                fc.setTitle("Save MIDI As");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MIDI Files (*.mid)", "*.mid"));

                String suggested = chosenName.endsWith(".mid") ? chosenName : chosenName + ".mid";
                fc.setInitialFileName(suggested);

                java.io.File downloads = new java.io.File(System.getProperty("user.home"), "Downloads");
                if (downloads.exists() && downloads.isDirectory()) {
                    fc.setInitialDirectory(downloads);
                }

                java.io.File destFile = fc.showSaveDialog(stage);
                if (destFile == null)
                    return;

                String finalName = destFile.getName().endsWith(".mid")
                        ? destFile.getName()
                        : destFile.getName() + ".mid";
                java.nio.file.Path destPath = destFile.toPath();
                if (!destPath.getFileName().toString().endsWith(".mid")) {
                    destPath = destPath.resolveSibling(finalName);
                }

                controller.downloadFromDBTo(chosenName, destPath);

                Alert ok = new Alert(AlertType.INFORMATION);
                ok.setHeaderText("Download Complete");
                ok.setContentText("Downloaded '" + chosenName + "'\n→ " + destPath);
                ok.showAndWait();

            } catch (RuntimeException ex) {
                Alert err = new Alert(AlertType.ERROR);
                err.setHeaderText("Download failed");
                err.setContentText(ex.getMessage());
                err.showAndWait();
            }
        });

        // Delete from DB
        deleteButton.setOnAction(e -> {
            try {
                List<String> recent = controller.listRecentFiles(10);
                String chosenName;

                if (recent.isEmpty()) {
                    TextInputDialog nameDlg = new TextInputDialog("");
                    nameDlg.setTitle("Delete MIDI");
                    nameDlg.setHeaderText("No recent files found.\nEnter the exact filename to delete from DB:");
                    nameDlg.setContentText("Filename (e.g., my_take.mid):");
                    Optional<String> typed = nameDlg.showAndWait();
                    if (typed.isEmpty() || typed.get().trim().isEmpty())
                        return;
                    chosenName = typed.get().trim();
                } else {
                    final String CUSTOM = "⎆ Enter another name…";
                    List<String> options = new java.util.ArrayList<>(recent);
                    options.add(CUSTOM);

                    ChoiceDialog<String> chooser = new ChoiceDialog<>(options.get(0), options);
                    chooser.setTitle("Delete MIDI");
                    chooser.setHeaderText("Choose a recent file or enter a different name to delete");
                    chooser.setContentText("Select file:");

                    Optional<String> picked = chooser.showAndWait();
                    if (picked.isEmpty())
                        return;

                    if (picked.get().equals(CUSTOM)) {
                        TextInputDialog nameDlg = new TextInputDialog("");
                        nameDlg.setTitle("Delete MIDI");
                        nameDlg.setHeaderText("Enter the exact filename to delete from DB:");
                        nameDlg.setContentText("Filename (e.g., my_take.mid):");
                        Optional<String> typed = nameDlg.showAndWait();
                        if (typed.isEmpty() || typed.get().trim().isEmpty())
                            return;
                        chosenName = typed.get().trim();
                    } else {
                        chosenName = picked.get();
                    }
                }

                Alert confirm = new Alert(AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Delete");
                confirm.setHeaderText("Delete from Database");
                confirm.setContentText("Are you sure you want to delete '" + chosenName + "' from the database?");
                Optional<javafx.scene.control.ButtonType> res = confirm.showAndWait();
                if (res.isEmpty() || res.get() != javafx.scene.control.ButtonType.OK)
                    return;

                boolean removed = controller.deleteFromDB(chosenName);

                Alert result = new Alert(AlertType.INFORMATION);
                result.setHeaderText(null);
                if (removed) {
                    result.setTitle("Deleted");
                    result.setContentText("Deleted '" + chosenName + "' from the database.");
                } else {
                    result.setTitle("Not Found");
                    result.setContentText("No file named '" + chosenName + "' was found in the database.");
                }
                result.showAndWait();

            } catch (RuntimeException ex) {
                Alert err = new Alert(AlertType.ERROR);
                err.setHeaderText("Delete failed");
                err.setContentText(ex.getMessage());
                err.showAndWait();
            }
        });

        topBar.getChildren().addAll(
                chordLabel, spacer,
                backButton, saveButton, showFilesButton,
                downloadButton, deleteButton,
                keyButton,
                quitButton, recordButton
        );

        // Chord Log Section (scrolls horizontally)
        Label historyLabel = new Label("Chord Log:");
        historyLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white; -fx-font-weight: bold;");

        chordHistoryRow.setPadding(new Insets(5, 10, 5, 10));
        chordHistoryRow.setAlignment(Pos.CENTER_LEFT);

        chordScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        chordScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chordScroll.setPannable(true);
        chordScroll.setFitToHeight(true);
        chordScroll.setStyle(
                "-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");

        VBox logSection = new VBox(5, historyLabel, chordScroll);
        logSection.setPadding(new Insets(10, 0, 10, 20));

        // Piano setup
        Pane keyboardPane = new Pane();
        keyboardPane.setPadding(new Insets(0, 0, 0, 40));
        keyboardPane.setPrefHeight(WHITE_KEY_HEIGHT + 20);
        keyboardPane.setStyle("-fx-background-color: black; -fx-border-color: transparent;");
        keyboardPane.setMaxWidth(Double.MAX_VALUE);

        whiteKeys.clear();
        blackKeys.clear();

        // White keys
        for (int i = 0; i < WHITE_KEYS; i++) {
            final int keyIndex = i;
            Rectangle whiteKey = new Rectangle(i * WHITE_KEY_WIDTH, 0, WHITE_KEY_WIDTH, WHITE_KEY_HEIGHT);
            whiteKey.setFill(Color.WHITE);
            whiteKey.setStroke(Color.BLACK);

            whiteKey.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                whiteKey.setFill(Color.LIGHTGRAY);
                controller.noteOnWhite(keyIndex);
                updateChordDisplay(chordLabel);
            });
            whiteKey.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                whiteKey.setFill(Color.WHITE);
                controller.noteOffWhite(keyIndex);
                updateChordDisplay(chordLabel);
            });

            whiteKeys.add(whiteKey);
            keyboardPane.getChildren().add(whiteKey);
        }

        // Black keys
        for (int i = 0; i < WHITE_KEYS; i++) {
            if (HAS_BLACK_KEY[i]) {
                final int keyIndex = i;
                Rectangle blackKey = new Rectangle(
                        i * WHITE_KEY_WIDTH + (WHITE_KEY_WIDTH - BLACK_KEY_WIDTH / 2.0),
                        0,
                        BLACK_KEY_WIDTH,
                        BLACK_KEY_HEIGHT);
                blackKey.setFill(Color.BLACK);
                blackKey.setStroke(Color.BLACK);

                blackKey.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                    blackKey.setFill(Color.DARKGRAY);
                    controller.noteOnBlack(keyIndex);
                    updateChordDisplay(chordLabel);
                });
                blackKey.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                    blackKey.setFill(Color.BLACK);
                    controller.noteOffBlack(keyIndex);
                    updateChordDisplay(chordLabel);
                });

                blackKeys.add(blackKey);
            }
        }

        keyboardPane.getChildren().addAll(blackKeys);

        VBox mainLayout = new VBox(15, topBar, logSection, keyboardPane);
        mainLayout.setPadding(new Insets(10));
        mainLayout.setFillWidth(true);
        mainLayout.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#1e1e2e")),
                        new Stop(1, Color.web("#2f2f4f"))),
                CornerRadii.EMPTY, Insets.EMPTY)));

        VBox.setVgrow(keyboardPane, Priority.NEVER);
        keyboardPane.setTranslateY(0);

        root.setCenter(mainLayout);

        Scene scene = new Scene(root);

        // Keyboard mapping (new QWERTY layout)
        scene.setOnKeyPressed(e -> {
            int midiNote = keyCharToMidiNote(e.getCode());
            if (midiNote != -1) {
                if (controller.getPressedNotes().add(midiNote)) {
                    controller.playNoteOn(midiNote);
                    updateKeyColor(midiNote, true);
                    updateChordDisplayFromController();
                }
            }
        });

        scene.setOnKeyReleased(e -> {
            int midiNote = keyCharToMidiNote(e.getCode());
            if (midiNote != -1) {
                if (controller.getPressedNotes().remove(midiNote)) {
                    controller.playNoteOff(midiNote);
                    updateKeyColor(midiNote, false);
                    updateChordDisplayFromController();
                }
            }
        });

        stage.setScene(scene);
        setupExternalMidiInput();
        stage.setTitle("🎹 Scrollable Chord Log + Fixed Keyboard");
        stage.show();
    }

    //functions for external midi device connection
    private void setupExternalMidiInput() {
        try {
            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
            boolean found = false;

            for (MidiDevice.Info info : infos) {
                System.out.println("Checking device: " + info.getName());

                MidiDevice device = MidiSystem.getMidiDevice(info);

                //check for transmitters
                if (device.getMaxTransmitters() != 0) {
                    try {
                        device.open();
                        Transmitter transmitter = device.getTransmitter();
                        transmitter.setReceiver(new ExternalMidiReceiver());
                        System.out.println("🎹 Connected to MIDI device: " + info.getName());
                        found = true;
                    } catch (MidiUnavailableException e) {
                        System.out.println("Failed to open device: " + info.getName());
                    }
                }
            }

            if (!found) {
                System.out.println("⚠️ No MIDI input devices found.");
            }

        } catch (MidiUnavailableException e) {
            e.printStackTrace();
        }
    }


    private class ExternalMidiReceiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (!(message instanceof ShortMessage sm)) return;

            int cmd = sm.getCommand();
            int note = sm.getData1();
            int vel  = sm.getData2();

            // Note ON
            if (cmd == ShortMessage.NOTE_ON && vel > 0) {
                Platform.runLater(() -> {
                    if (controller.getPressedNotes().add(note)) {
                        controller.playNoteOn(note);
                        updateKeyColor(note, true);
                        updateChordDisplayFromController();
                    }
                });
            }

            // Note OFF (or velocity 0)
            else if (cmd == ShortMessage.NOTE_OFF ||
                (cmd == ShortMessage.NOTE_ON && vel == 0)) {
                    Platform.runLater(() -> {
                    if (controller.getPressedNotes().remove(note)) {
                        controller.playNoteOff(note);
                        updateKeyColor(note, false);
                        updateChordDisplayFromController();
                    }
                });
            }
        }

        @Override
        public void close() {}
    }



    // Helper method: map KeyCode to MIDI note (new mapping)
    private int keyCharToMidiNote(javafx.scene.input.KeyCode keyCode) {
        return switch (keyCode) {
            case Q      -> 60; // C4
            case DIGIT2 -> 61;
            case W      -> 62;
            case DIGIT3 -> 63;
            case E      -> 64;
            case R      -> 65;
            case DIGIT5 -> 66;
            case T      -> 67;
            case DIGIT6 -> 68;
            case Y      -> 69;
            case DIGIT7 -> 70;
            case U      -> 71;
            case Z      -> 72; // C5
            case S      -> 73;
            case X      -> 74;
            case D      -> 75;
            case C      -> 76;
            case V      -> 77;
            case G      -> 78;
            case B      -> 79;
            case H      -> 80;
            case N      -> 81;
            case J      -> 82;
            case M      -> 83;
            default     -> -1;
        };
    }

    // Update key color by MIDI note
    private void updateKeyColor(int midiNote, boolean pressed) {
        int whiteIndex = midiNoteToWhiteKeyIndex(midiNote);
        if (whiteIndex != -1) {
            Rectangle key = whiteKeys.get(whiteIndex);
            key.setFill(pressed ? Color.LIGHTGRAY : Color.WHITE);
            return;
        }

        int blackIndex = midiNoteToBlackKeyIndex(midiNote);
        if (blackIndex != -1) {
            Rectangle key = blackKeys.get(blackIndex);
            key.setFill(pressed ? Color.DARKGRAY : Color.BLACK);
        }
    }

    // Helper: Convert midiNote to white key index
    private int midiNoteToWhiteKeyIndex(int midiNote) {
        int offset = midiNote - BASE_MIDI_NOTE;
        int octave = offset / 12;
        int pc = offset % 12;

        int[] whitePcToIndex = {0, -1, 1, -1, 2, 3, -1, 4, -1, 5, -1, 6};

        if (pc < 0 || pc > 11) return -1;
        if (whitePcToIndex[pc] == -1) return -1;
        return octave * 7 + whitePcToIndex[pc];
    }

    // Helper: Convert midiNote to black key index
    private int midiNoteToBlackKeyIndex(int midiNote) {
        int offset = midiNote - BASE_MIDI_NOTE;
        int octave = offset / 12;
        int pc = offset % 12;
        if (pc < 0) pc += 12;

        int[] blackPcToIndex = {-1, 0, -1, 1, -1, -1, 2, -1, 3, -1, 4, -1};

        if (pc < 0 || pc > 11) return -1;
        int idxInOctave = blackPcToIndex[pc];
        if (idxInOctave == -1) return -1;

        return octave * 5 + idxInOctave;
    }

    private void updateChordDisplayFromController() {
        String chord = controller.getCurrentChordLabel();
        chordLabel.setText("🎵 Current Chord: " + chord);

        if (!chord.equals("—")) {
            Label entry = new Label(chord);
            entry.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 18px; -fx-font-weight: bold;");
            chordHistoryRow.getChildren().add(entry);
            chordScroll.setHvalue(1.0);
        }
    }

    private void updateChordDisplay(Label chordLabel) {
        String chord = controller.getCurrentChordLabel();
        chordLabel.setText("🎵 Current Chord: " + chord);

        if (!chord.equals("—")) {
            Label entry = new Label(chord);
            entry.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 18px; -fx-font-weight: bold;");
            chordHistoryRow.getChildren().add(entry);
            chordScroll.setHvalue(1.0);
        }
    }
}
