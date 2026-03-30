package com.drone.ui;

import com.drone.model.NoFlyZone;
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

/**
 * Modern dialog to add a no-fly zone using geographic coordinates (longitude/latitude).
 */
public class NoFlyZoneDialog extends Stage {

    private NoFlyZone result;
    private boolean confirmed = false;

    private final TextField minLngField = createInputField("-74.02");
    private final TextField minLatField = createInputField("40.70");
    private final TextField maxLngField = createInputField("-73.98");
    private final TextField maxLatField = createInputField("40.72");

    private static final String BG_DARK = "#0A0E17";
    private static final String PANEL_GLASS = "rgba(18, 25, 45, 0.95)";
    private static final String ACCENT_RED = "#FF5252";
    private static final String TEXT_PRIMARY = "#FFFFFF";
    private static final String TEXT_SECONDARY = "#B0BEC5";

    public NoFlyZoneDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("⛔ Add No-Fly Zone");
        buildUI();
        setResizable(false);
    }

    private void buildUI() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        Label title = new Label("⛔ Add No-Fly Zone (Geographic)");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(ACCENT_RED));

        Label hint = new Label("Define a rectangle by its South-West and North-East corners.");
        hint.setFont(Font.font("Segoe UI", 11));
        hint.setTextFill(Color.web(TEXT_SECONDARY));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.setStyle("-fx-background-color: " + PANEL_GLASS + "; -fx-background-radius: 12;");

        addRow(grid, "Min Longitude (West):", minLngField, 0);
        addRow(grid, "Min Latitude (South):",  minLatField, 1);
        addRow(grid, "Max Longitude (East):", maxLngField, 2);
        addRow(grid, "Max Latitude (North):",  maxLatField, 3);

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: " + ACCENT_RED + "; -fx-font-size: 12px;");

        Button ok = createButton("✓ Add Zone", ACCENT_RED);
        Button cancel = createButton("✗ Cancel", TEXT_SECONDARY);

        ok.setOnAction(e -> {
            try {
                double minLng = Double.parseDouble(minLngField.getText().trim());
                double minLat = Double.parseDouble(minLatField.getText().trim());
                double maxLng = Double.parseDouble(maxLngField.getText().trim());
                double maxLat = Double.parseDouble(maxLatField.getText().trim());

                if (minLng >= maxLng || minLat >= maxLat) {
                    throw new IllegalArgumentException("Min values must be less than max values.");
                }
                // Optional: add range checks for Manhattan area
                if (minLng < -180 || maxLng > 180 || minLat < -90 || maxLat > 90) {
                    throw new IllegalArgumentException("Coordinates out of valid range.");
                }

                result = new NoFlyZone(minLng, minLat, maxLng, maxLat);
                confirmed = true;
                close();
            } catch (Exception ex) {
                errorLabel.setText("⚠ " + ex.getMessage());
            }
        });
        cancel.setOnAction(e -> close());

        HBox buttons = new HBox(12, ok, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, hint, grid, errorLabel, buttons);
        Scene scene = new Scene(root, 450, 380);
        scene.setFill(Color.web(BG_DARK));
        setScene(scene);
    }

    private void addRow(GridPane grid, String labelText, TextField field, int row) {
        Label label = new Label(labelText);
        label.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        label.setTextFill(Color.web(TEXT_SECONDARY));
        label.setMinWidth(140);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }

    private TextField createInputField(String defaultValue) {
        TextField tf = new TextField(defaultValue);
        tf.setStyle("-fx-background-color: #1A2340; -fx-text-fill: " + TEXT_PRIMARY + "; " +
                "-fx-border-color: #2A3A60; -fx-border-radius: 6; -fx-background-radius: 6; " +
                "-fx-pref-width: 170;");
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

    public boolean isConfirmed() {
        return confirmed;
    }

    public NoFlyZone getResult() {
        return result;
    }
}