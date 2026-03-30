package com.drone.ui;

import com.drone.model.Location;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Modern modal dialog for manually entering X/Y coordinates for each location.
 */
public class MatrixInputDialog extends Stage {

    private final int nodeCount;
    private final List<TextField> xFields = new ArrayList<>();
    private final List<TextField> yFields = new ArrayList<>();
    private List<Location> result;
    private boolean confirmed = false;

    // Modern color scheme
    private static final String BG_DARK = "#0A0E17";
    private static final String PANEL_GLASS = "rgba(18, 25, 45, 0.95)";
    private static final String ACCENT_CYAN = "#00E5FF";
    private static final String ACCENT_RED = "#FF5252";
    private static final String TEXT_PRIMARY = "#FFFFFF";
    private static final String TEXT_SECONDARY = "#B0BEC5";

    public MatrixInputDialog(Stage owner, int nodeCount) {
        this.nodeCount = nodeCount;
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("📍 Manual Location Entry");
        buildUI();
        setResizable(false);
    }

    private void buildUI() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        Label title = new Label("📍 Enter Location Coordinates");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(ACCENT_CYAN));

        Label hint = new Label("Base (id=0) is the Drone HQ. V1–Vn are delivery points. Range: X[0–800], Y[0–600].");
        hint.setFont(Font.font("Segoe UI", 11));
        hint.setTextFill(Color.web(TEXT_SECONDARY));

        // --- Coordinate grid ---
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.setStyle("-fx-background-color: " + PANEL_GLASS + "; -fx-background-radius: 12;");

        // Headers
        String[] headers = {"Location", "X (0–800)", "Y (0–600)"};
        for (int i = 0; i < headers.length; i++) {
            Label header = new Label(headers[i]);
            header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            header.setTextFill(Color.web(ACCENT_CYAN));
            grid.add(header, i, 0);
        }

        Random rng = new Random();
        for (int i = 0; i < nodeCount; i++) {
            boolean isBase = (i == 0);
            String name = isBase ? "Base" : "V" + i;
            String color = isBase ? ACCENT_RED : ACCENT_CYAN;

            Label nameLabel = new Label(name);
            nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            nameLabel.setTextFill(Color.web(color));
            nameLabel.setMinWidth(60);
            grid.add(nameLabel, 0, i + 1);

            int defX = isBase ? 400 : 80 + rng.nextInt(640);
            int defY = isBase ? 300 : 60 + rng.nextInt(480);
            TextField xf = createInputField(String.valueOf(defX));
            TextField yf = createInputField(String.valueOf(defY));
            xFields.add(xf);
            yFields.add(yf);
            grid.add(xf, 1, i + 1);
            grid.add(yf, 2, i + 1);
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(400);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: " + ACCENT_RED + "; -fx-font-size: 12px;");

        // Buttons
        Button ok = createButton("✓ Confirm", ACCENT_CYAN);
        Button cancel = createButton("✗ Cancel", TEXT_SECONDARY);

        ok.setOnAction(e -> {
            try {
                buildResult();
                confirmed = true;
                close();
            } catch (Exception ex) {
                errorLabel.setText("⚠ " + ex.getMessage());
            }
        });
        cancel.setOnAction(e -> close());

        HBox buttons = new HBox(12, ok, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, hint, scroll, errorLabel, buttons);
        Scene scene = new Scene(root, 520, Math.min(650, 200 + nodeCount * 48));
        scene.setFill(Color.web(BG_DARK));
        setScene(scene);
    }

    private TextField createInputField(String defaultValue) {
        TextField tf = new TextField(defaultValue);
        tf.setStyle("-fx-background-color: #1A2340; -fx-text-fill: " + TEXT_PRIMARY + "; " +
                "-fx-border-color: #2A3A60; -fx-border-radius: 6; -fx-background-radius: 6; " +
                "-fx-pref-width: 100;");
        return tf;
    }

    private Button createButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: #0A0E17; " +
                "-fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 6;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + color + "dd; -fx-text-fill: #0A0E17; " +
                "-fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 6;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: #0A0E17; " +
                "-fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 6;"));
        return btn;
    }

    private void buildResult() {
        List<Location> locs = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            double x = parseField(xFields.get(i), i == 0 ? "Base" : "V" + i, "X", 0, 800);
            double y = parseField(yFields.get(i), i == 0 ? "Base" : "V" + i, "Y", 0, 600);
            locs.add(new Location(i, i == 0 ? "Base" : "V" + i, x, y));
        }
        result = locs;
    }

    private double parseField(TextField tf, String name, String axis, double min, double max) {
        String raw = tf.getText().trim();
        double val;
        try {
            val = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " " + axis + " is not a valid number: \"" + raw + "\"");
        }
        if (val < min || val > max)
            throw new IllegalArgumentException(name + " " + axis + " must be between " + (int) min + " and " + (int) max);
        return val;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<Location> getResult() {
        return result;
    }
}