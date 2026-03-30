package com.drone.ui;

import com.drone.model.Location;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class LocationEditorDialog extends Stage {

    private static final String BG_DARK = "#0A0E17";
    private static final String PANEL_GLASS = "rgba(18, 25, 45, 0.95)";
    private static final String ACCENT_CYAN = "#4FD1FF";
    private static final String ACCENT_GREEN = "#4ADE80";
    private static final String ACCENT_RED = "#FF6B6B";
    private static final String TEXT_PRIMARY = "#F3FBFF";
    private static final String TEXT_SECONDARY = "#9AB3C3";

    private final List<Location> locations;
    private final List<TextField> readyFields = new ArrayList<>();
    private final List<TextField> dueFields = new ArrayList<>();
    private final List<ComboBox<Location.Priority>> priorityBoxes = new ArrayList<>();
    private boolean confirmed;

    public LocationEditorDialog(Stage owner, List<Location> locations) {
        this.locations = locations;
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Edit Delivery Priorities");
        buildUi();
        setResizable(true);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    private void buildUi() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        Label title = createLabel("Edit Delivery Priorities", 18, FontWeight.BOLD, TEXT_PRIMARY);
        Label hint = createLabel(
                "Adjust ready time, due time, and priority for each stop. Depot settings stay fixed.",
                12,
                FontWeight.NORMAL,
                TEXT_SECONDARY
        );

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.setStyle("-fx-background-color: " + PANEL_GLASS + "; -fx-background-radius: 16;");

        String[] headers = {"Stop", "Ready", "Due", "Priority"};
        for (int i = 0; i < headers.length; i++) {
            Label header = createLabel(headers[i], 12, FontWeight.BOLD, ACCENT_CYAN);
            grid.add(header, i, 0);
        }

        int row = 1;
        for (Location location : locations) {
            if (location.getId() == 0) {
                continue;
            }

            Label nameLabel = createLabel(location.getName(), 12, FontWeight.BOLD, ACCENT_GREEN);
            TextField readyField = createField(String.format("%.0f", location.getReadyTime()));
            TextField dueField = createField(String.format("%.0f", location.getDueTime()));
            ComboBox<Location.Priority> priorityBox = new ComboBox<>();
            priorityBox.getItems().addAll(Location.Priority.values());
            priorityBox.setValue(location.getPriority());
            priorityBox.setMaxWidth(Double.MAX_VALUE);
            priorityBox.setStyle(
                    "-fx-background-color: #122038;" +
                            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
                            "-fx-border-color: rgba(79, 209, 255, 0.22);" +
                            "-fx-background-radius: 10;" +
                            "-fx-border-radius: 10;"
            );

            readyFields.add(readyField);
            dueFields.add(dueField);
            priorityBoxes.add(priorityBox);

            grid.add(nameLabel, 0, row);
            grid.add(readyField, 1, row);
            grid.add(dueField, 2, row);
            grid.add(priorityBox, 3, row);
            row++;
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Label errorLabel = createLabel("", 12, FontWeight.NORMAL, ACCENT_RED);

        Button applyButton = createButton("Apply Changes", ACCENT_CYAN, "#07111F");
        applyButton.setOnAction(e -> {
            try {
                applyChanges();
                confirmed = true;
                close();
            } catch (IllegalArgumentException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });

        Button cancelButton = createButton("Cancel", "rgba(255,255,255,0.08)", TEXT_PRIMARY);
        cancelButton.setOnAction(e -> close());

        HBox actions = new HBox(12, applyButton, cancelButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, hint, scrollPane, errorLabel, actions);
        setScene(new Scene(root, 640, 520));
    }

    private void applyChanges() {
        int editableIndex = 0;
        for (Location location : locations) {
            if (location.getId() == 0) {
                continue;
            }

            double readyTime = parseNonNegative(readyFields.get(editableIndex).getText(), location.getName(), "ready time");
            double dueTime = parseNonNegative(dueFields.get(editableIndex).getText(), location.getName(), "due time");
            if (dueTime < readyTime) {
                throw new IllegalArgumentException(location.getName() + " due time must be greater than or equal to ready time.");
            }

            location.setReadyTime(readyTime);
            location.setDueTime(dueTime);
            location.setPriority(priorityBoxes.get(editableIndex).getValue());
            editableIndex++;
        }
    }

    private double parseNonNegative(String raw, String locationName, String fieldName) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (value < 0) {
                throw new IllegalArgumentException(locationName + " " + fieldName + " must be non-negative.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(locationName + " " + fieldName + " must be a valid number.");
        }
    }

    private TextField createField(String value) {
        TextField field = new TextField(value);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle(
                "-fx-background-color: #122038;" +
                        "-fx-text-fill: " + TEXT_PRIMARY + ";" +
                        "-fx-border-color: rgba(79, 209, 255, 0.22);" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;"
        );
        return field;
    }

    private Button createButton(String text, String background, String textColor) {
        Button button = new Button(text);
        button.setPadding(new Insets(10, 16, 10, 16));
        button.setTextFill(Color.web(textColor));
        button.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        button.setStyle("-fx-background-color: " + background + "; -fx-background-radius: 12;");
        return button;
    }

    private Label createLabel(String text, int size, FontWeight weight, String color) {
        Label label = new Label(text);
        label.setFont(Font.font("Segoe UI", weight, size));
        label.setTextFill(Color.web(color));
        return label;
    }
}
