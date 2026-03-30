package com.drone.ui;

import com.drone.algorithm.BruteForce;
import com.drone.algorithm.GeneticAlgorithm;
import com.drone.algorithm.Greedy;
import com.drone.algorithm.TSPAlgorithm;
import com.drone.model.Location;
import com.drone.model.NoFlyZone;
import com.drone.model.Route;
import com.drone.utils.BrowserMapExporter;
import com.drone.utils.BrowserZoneBridge;
import com.drone.utils.DistanceCalculator;
import com.drone.utils.MatrixGenerator;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.geometry.Rectangle2D;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import com.drone.utils.FileManager;

public class MainUI {

    private static final int BF_MAX_NODES = 8;

    private static final String BG_DARK = "#07111F";
    private static final String BG_PANEL = "rgba(10, 22, 38, 0.92)";
    private static final String BG_CARD = "rgba(14, 29, 49, 0.94)";
    private static final String BORDER_SOFT = "rgba(79, 209, 255, 0.18)";
    private static final String ACCENT_CYAN = "#4FD1FF";
    private static final String ACCENT_GREEN = "#4ADE80";
    private static final String ACCENT_ORANGE = "#F59E0B";
    private static final String ACCENT_PINK = "#F472B6";
    private static final String ACCENT_RED = "#FF6B6B";
    private static final String TEXT_PRIMARY = "#F3FBFF";
    private static final String TEXT_SECONDARY = "#9AB3C3";

    private List<Location> locations = new ArrayList<>();
    private List<NoFlyZone> noFlyZones = new ArrayList<>();
    private double[][] distMatrix;
    private Route currentBestRoute;
    private Path latestBrowserMap;

    private final Stage stage;
    private MapPanel map;

    private Spinner<Integer> nodeSpinner;
    private Spinner<Integer> fleetSpinner;
    private CheckBox cbBruteForce;
    private CheckBox cbGreedy;
    private CheckBox cbGenetic;
    private Label bfWarnLabel;
    private Label statusLabel;
    private Button btnRun;
    private TextArea routeArea;
    private TextArea reportArea;
    private Label costLabel;
    private Label timeLabel;
    private Label deadlineStatusLabel;
    private Label nodeCountMetric;
    private Label droneCountMetric;
    private Label nfzMetric;
    private Label solverStatusLabel;
    private VBox comparisonRows;
    private BarChart<String, Number> distChart;
    private BarChart<String, Number> timeChart;
    private ProgressIndicator progressIndicator;

    public MainUI(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BrowserZoneBridge.getInstance().startIfNeeded();
        stage.setTitle("Drone Fleet Command Center");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setResizable(true);
        stage.setMaximized(false);
        stage.setFullScreen(false);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DARK + ";");
        root.setTop(new VBox(createMenuBar(), createHeader()));
        root.setCenter(createMainSplitPane());
        root.setBottom(createStatusBar());

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double sceneWidth = Math.max(1100, Math.min(1500, bounds.getWidth() * 0.9));
        double sceneHeight = Math.max(720, Math.min(920, bounds.getHeight() * 0.9));
        Scene scene = new Scene(root, sceneWidth, sceneHeight);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
        stage.show();

        FadeTransition fade = new FadeTransition(Duration.millis(450), root);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();

        setStatus(map.getMapConfigurationStatus());
    }

    private SplitPane createMainSplitPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.24, 0.67);
        splitPane.getItems().addAll(createControlPanel(), createMapArea(), createAnalyticsPanel());
        return splitPane;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER_SOFT + "; -fx-border-width: 0 0 1 0;");

        Menu fileMenu = new Menu("File");
        MenuItem saveItem = new MenuItem("Save Session...");
        MenuItem loadItem = new MenuItem("Load Session...");
        MenuItem exportCSV = new MenuItem("Export as CSV...");
        saveItem.setOnAction(e -> saveSession());
        loadItem.setOnAction(e -> loadSession());
        exportCSV.setOnAction(e -> exportSessionCsv());
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> stage.close());
        fileMenu.getItems().addAll(saveItem, loadItem, exportCSV, new SeparatorMenuItem(), exitItem);

        Menu viewMenu = new Menu("View");
        CheckMenuItem showNFZ = new CheckMenuItem("Show No-Fly Zones");
        showNFZ.setSelected(true);
        showNFZ.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) {
                map.setNoFlyZones(getAllNoFlyZones());
            } else {
                map.clearNoFlyZones();
            }
        });
        MenuItem fitMap = new MenuItem("Fit Map to Data");
        fitMap.setOnAction(e -> map.fitAll());
        viewMenu.getItems().addAll(showNFZ, fitMap);

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private HBox createHeader() {
        HBox header = new HBox(18);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 28, 20, 28));
        header.setStyle(
                "-fx-background-color: linear-gradient(to right, rgba(7,17,31,0.98), rgba(10,22,38,0.94));" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-border-width: 0 0 1 0;"
        );

        VBox titleBox = new VBox(6);
        Label eyebrow = createLabel("Real-Time Operations", 11, FontWeight.BOLD, ACCENT_CYAN);
        eyebrow.setStyle(eyebrow.getStyle() + "-fx-letter-spacing: 1.4px;");
        Label title = createLabel("Drone Fleet Command Center", 24, FontWeight.BOLD, TEXT_PRIMARY);
        Label subtitle = createLabel(
                "Plan routes, compare solvers, and review coverage on a live interactive map surface.",
                13,
                FontWeight.NORMAL,
                TEXT_SECONDARY
        );
        titleBox.getChildren().addAll(eyebrow, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox metrics = new HBox(12,
                createMetricCard("Nodes", "0"),
                createMetricCard("Drones", "0"),
                createMetricCard("No-Fly Zones", "0")
        );

        header.getChildren().addAll(titleBox, spacer, metrics);
        return header;
    }

    private VBox createControlPanel() {
        VBox shell = new VBox();
        shell.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER_SOFT + "; -fx-border-width: 0 1 0 0;");

        VBox content = new VBox(18);
        content.setPadding(new Insets(24));

        Label sectionTitle = createLabel("Mission Controls", 18, FontWeight.BOLD, TEXT_PRIMARY);
        Label sectionCopy = createLabel(
                "Configure the scenario, choose the solvers, then dispatch the fleet against the active map.",
                12,
                FontWeight.NORMAL,
                TEXT_SECONDARY
        );

        TitledPane fleetPane = createPane("Scenario Setup", buildFleetContent());
        TitledPane solverPane = createPane("Solver Selection", buildSolverContent());
        VBox runCard = buildDispatchCard();

        content.getChildren().addAll(sectionTitle, sectionCopy, fleetPane, solverPane, runCard);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        shell.getChildren().add(scrollPane);
        return shell;
    }

    private VBox buildFleetContent() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(10, 0, 0, 0));

        nodeSpinner = createIntegerSpinner(3, 100, 10);
        fleetSpinner = createIntegerSpinner(1, 10, 3);

        Button btnGenerate = createPrimaryButton("Generate Map Data", ACCENT_CYAN);
        btnGenerate.setOnAction(e -> onGenerate());

        Button btnEditDeliveries = createSecondaryButton("Edit Deliveries", ACCENT_ORANGE);
        btnEditDeliveries.setOnAction(e -> editDeliveries());

        Button btnAddNFZ = createSecondaryButton("Add No-Fly Zone", ACCENT_RED);
        btnAddNFZ.setOnAction(e -> addNoFlyZone());

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(12);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(48);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(52);
        fields.getColumnConstraints().addAll(left, right);
        fields.add(createFieldLabel("Delivery Nodes"), 0, 0);
        fields.add(nodeSpinner, 1, 0);
        fields.add(createFieldLabel("Fleet Size"), 0, 1);
        fields.add(fleetSpinner, 1, 1);

        content.getChildren().addAll(fields, btnGenerate, btnEditDeliveries, btnAddNFZ);
        return content;
    }

    private VBox buildSolverContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10, 0, 0, 0));

        cbBruteForce = createSolverCheckBox("Brute Force", "Exact, best for very small node counts.", ACCENT_ORANGE);
        cbGreedy = createSolverCheckBox("Greedy", "Fast heuristic baseline for quick dispatch runs.", ACCENT_CYAN);
        cbGenetic = createSolverCheckBox("Genetic", "Scales better across larger fleets and scenarios.", ACCENT_PINK);

        cbGreedy.setSelected(true);
        cbGenetic.setSelected(true);

        bfWarnLabel = createLabel("Brute force is limited to 8 nodes.", 11, FontWeight.NORMAL, ACCENT_ORANGE);
        updateBruteForceState();
        nodeSpinner.valueProperty().addListener((obs, old, value) -> updateBruteForceState());

        content.getChildren().addAll(cbBruteForce, cbGreedy, cbGenetic, bfWarnLabel);
        return content;
    }

    private VBox buildDispatchCard() {
        VBox card = createCard();

        Label title = createLabel("Dispatch", 16, FontWeight.BOLD, TEXT_PRIMARY);
        Label copy = createLabel(
                "Run the selected solvers, compare route quality, and push the best plan to the live map.",
                12,
                FontWeight.NORMAL,
                TEXT_SECONDARY
        );

        btnRun = createPrimaryButton("Dispatch Fleet", ACCENT_GREEN);
        btnRun.setOnAction(e -> onRunOptimization());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(34, 34);
        progressIndicator.setStyle("-fx-progress-color: " + ACCENT_CYAN + ";");

        solverStatusLabel = createLabel("", 12, FontWeight.NORMAL, ACCENT_CYAN);

        HBox actionRow = new HBox(12, btnRun, progressIndicator);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, copy, actionRow, solverStatusLabel);
        return card;
    }

    private VBox createMapArea() {
        map = new MapPanel();

        VBox shell = new VBox(14);
        shell.setPadding(new Insets(16));
        shell.setStyle("-fx-background-color: " + BG_DARK + ";");

        StackPane mapFrame = new StackPane(map);
        mapFrame.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #091423, #102744);" +
                        "-fx-background-radius: 24;" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-border-radius: 24;" +
                        "-fx-border-width: 1;"
        );
        mapFrame.setPadding(new Insets(2));
        VBox.setVgrow(mapFrame, Priority.ALWAYS);

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox infoCard = createCard();
        infoCard.setMaxWidth(320);
        Label panelTitle = createLabel("Live Map Surface", 15, FontWeight.BOLD, TEXT_PRIMARY);
        Label panelCopy = createLabel(
                "Use the browser map for the full road map and highlighted optimal routes. The in-app panel stays as a lightweight preview.",
                12,
                FontWeight.NORMAL,
                TEXT_SECONDARY
        );
        infoCard.getChildren().addAll(panelTitle, panelCopy);

        Button resetView = createSecondaryButton("Center on Depot", ACCENT_CYAN);
        resetView.setOnAction(e -> map.resetView());

        Button fitView = createSecondaryButton("Fit Active Data", ACCENT_GREEN);
        fitView.setOnAction(e -> map.fitAll());

        Button openBrowserMap = createPrimaryButton("Open Full Browser Map", ACCENT_ORANGE);
        openBrowserMap.setOnAction(e -> openBrowserMap(true));

        Button refreshBrowserZones = createSecondaryButton("Refresh Browser Zones", ACCENT_PINK);
        refreshBrowserZones.setOnAction(e -> syncBrowserZones());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getChildren().addAll(infoCard, spacer, openBrowserMap, refreshBrowserZones, resetView, fitView);

        shell.getChildren().addAll(toolbar, mapFrame);
        return shell;
    }

    private VBox createAnalyticsPanel() {
        VBox panel = new VBox(0);
        panel.setMinWidth(430);
        panel.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER_SOFT + "; -fx-border-width: 0 0 0 1;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabs.getTabs().addAll(
                new Tab("Summary", buildSummaryTab()),
                new Tab("Charts", buildChartsTab()),
                new Tab("Logs", buildLogsTab())
        );

        VBox.setVgrow(tabs, Priority.ALWAYS);
        panel.getChildren().add(tabs);
        return panel;
    }

    private VBox buildSummaryTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(22));

        routeArea = new TextArea("Dispatch waiting...");
        routeArea.setEditable(false);
        routeArea.setWrapText(true);
        routeArea.setStyle(
                "-fx-control-inner-background: #081322;" +
                        "-fx-text-fill: " + ACCENT_GREEN + ";" +
                        "-fx-highlight-fill: " + ACCENT_CYAN + ";" +
                        "-fx-highlight-text-fill: #07111F;" +
                        "-fx-font-family: 'Consolas';" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-radius: 16;" +
                        "-fx-border-color: " + BORDER_SOFT + ";"
        );
        VBox.setVgrow(routeArea, Priority.ALWAYS);

        HBox statRow = new HBox(10,
                createInfoTile("Best Distance"),
                createInfoTile("Execution Time"),
                createInfoTile("Deadline State")
        );
        HBox.setHgrow(statRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(statRow.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(statRow.getChildren().get(2), Priority.ALWAYS);

        comparisonRows = new VBox(6);
        comparisonRows.setPadding(new Insets(14));
        comparisonRows.setStyle(
                "-fx-background-color: " + BG_CARD + ";" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-border-radius: 18;"
        );

        content.getChildren().addAll(
                createSectionHeader("Fleet Assignments", ACCENT_GREEN),
                routeArea,
                statRow,
                createSectionHeader("Solver Comparison", ACCENT_CYAN),
                comparisonRows
        );
        return content;
    }

    private VBox buildChartsTab() {
        VBox content = new VBox(18);
        content.setPadding(new Insets(22));
        distChart = createChart("Fleet Distance by Solver", "km");
        timeChart = createChart("Execution Time by Solver", "ms");
        VBox.setVgrow(distChart, Priority.ALWAYS);
        VBox.setVgrow(timeChart, Priority.ALWAYS);
        content.getChildren().addAll(distChart, timeChart);
        return content;
    }

    private VBox buildLogsTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(22));
        reportArea = new TextArea();
        reportArea.setEditable(false);
        reportArea.setWrapText(true);
        reportArea.setStyle(
                "-fx-control-inner-background: #081322;" +
                        "-fx-text-fill: " + TEXT_SECONDARY + ";" +
                        "-fx-font-family: 'Consolas';" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-radius: 16;" +
                        "-fx-border-color: " + BORDER_SOFT + ";"
        );
        VBox.setVgrow(reportArea, Priority.ALWAYS);
        content.getChildren().addAll(createSectionHeader("Solver Logs", ACCENT_ORANGE), reportArea);
        return content;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: rgba(7,17,31,0.98); -fx-border-color: " + BORDER_SOFT + "; -fx-border-width: 1 0 0 0;");
        statusLabel = createLabel("", 12, FontWeight.NORMAL, TEXT_SECONDARY);
        bar.getChildren().add(statusLabel);
        return bar;
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Drone Fleet Command Center");
        alert.setContentText(
                "Multi-drone routing with time windows, no-fly zones, and live map visualization.\n" +
                        "Algorithms included: Brute Force, Greedy, and Genetic."
        );
        alert.showAndWait();
    }

    private void addNoFlyZone() {
        NoFlyZoneDialog dialog = new NoFlyZoneDialog(stage);
        dialog.showAndWait();
        if (dialog.isConfirmed()) {
            noFlyZones.add(dialog.getResult());
            map.setNoFlyZones(getAllNoFlyZones());
            updateMissionMetrics();
            setStatus("No-fly zone added. Re-run dispatch to apply it to path planning.");
        }
    }

    private void saveSession() {
        if (locations.isEmpty()) {
            setStatus("Generate or load data before saving a session.");
            return;
        }

        FileChooser chooser = createFileChooser("Save Session", "Drone Session (*.json)", "*.json");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try {
            List<Route> results = currentBestRoute == null ? List.of() : List.of(currentBestRoute);
            FileManager.save(locations, getAllNoFlyZones(), results, ensureExtension(file, ".json"));
            setStatus("Session saved successfully.");
        } catch (Exception ex) {
            setStatus("Could not save session: " + ex.getMessage());
        }
    }

    private void loadSession() {
        FileChooser chooser = createFileChooser("Load Session", "Drone Session (*.json)", "*.json");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        try {
            FileManager.LoadResult result = FileManager.load(file);
            locations = new ArrayList<>(result.locations());
            noFlyZones = new ArrayList<>(result.noFlyZones());
            currentBestRoute = null;
            DistanceCalculator.clearCache();
            map.setLocations(locations, getAllNoFlyZones());
            map.setNoFlyZones(getAllNoFlyZones());
            updateMissionMetrics();
            updateBrowserMap(false);
            routeArea.setText("Session loaded. Review priorities and run dispatch to compute a fresh plan.");
            reportArea.appendText("[" + LocalTime.now() + "] Loaded session from " + file.getName() + ".\n");
            setStatus("Session loaded successfully.");
        } catch (Exception ex) {
            setStatus("Could not load session: " + ex.getMessage());
        }
    }

    private void exportSessionCsv() {
        if (locations.isEmpty()) {
            setStatus("Generate or load data before exporting CSV.");
            return;
        }

        FileChooser chooser = createFileChooser("Export CSV", "CSV File (*.csv)", "*.csv");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try {
            FileManager.exportToCSV(locations, getAllNoFlyZones(), ensureExtension(file, ".csv"));
            setStatus("CSV exported successfully.");
        } catch (Exception ex) {
            setStatus("Could not export CSV: " + ex.getMessage());
        }
    }

    private void editDeliveries() {
        if (locations.isEmpty() || locations.size() <= 1) {
            setStatus("Generate map data before editing delivery priorities.");
            return;
        }

        LocationEditorDialog dialog = new LocationEditorDialog(stage, locations);
        dialog.showAndWait();
        if (!dialog.isConfirmed()) {
            return;
        }

        currentBestRoute = null;
        DistanceCalculator.clearCache();
        map.setLocations(locations, getAllNoFlyZones());
        updateBrowserMap(false);
        routeArea.setText("Delivery settings updated. Re-run dispatch to apply the edited priorities and time windows.");
        reportArea.appendText("[" + LocalTime.now() + "] Updated delivery priorities and time windows.\n");
        setStatus("Delivery priorities updated.");
    }

    private void updateBruteForceState() {
        boolean tooMany = nodeSpinner.getValue() > BF_MAX_NODES;
        cbBruteForce.setDisable(tooMany);
        if (tooMany) {
            cbBruteForce.setSelected(false);
        }
        bfWarnLabel.setVisible(tooMany);
        bfWarnLabel.setManaged(tooMany);
    }

    private void onRunOptimization() {
        if (locations.isEmpty()) {
            setStatus("Generate map data before running a dispatch.");
            return;
        }

        List<TSPAlgorithm> algorithms = new ArrayList<>();
        if (cbBruteForce.isSelected() && !cbBruteForce.isDisabled()) {
            algorithms.add(new BruteForce());
        }
        if (cbGreedy.isSelected()) {
            algorithms.add(new Greedy());
        }
        if (cbGenetic.isSelected()) {
            algorithms.add(new GeneticAlgorithm());
        }

        if (algorithms.isEmpty()) {
            setStatus("Select at least one solver before dispatching the fleet.");
            return;
        }

        btnRun.setDisable(true);
        btnRun.setText("Optimizing...");
        progressIndicator.setVisible(true);
        solverStatusLabel.setText("Preparing solver run...");

        distMatrix = DistanceCalculator.buildMatrix(locations, getAllNoFlyZones());
        int fleetSize = fleetSpinner.getValue();

        new Thread(() -> {
            try {
                List<Route> results = new ArrayList<>();
                for (TSPAlgorithm algorithm : algorithms) {
                    Platform.runLater(() -> solverStatusLabel.setText("Running " + algorithm.getName() + "..."));
                    results.add(algorithm.solve(distMatrix, locations, fleetSize));
                }

                Platform.runLater(() -> {
                    displayResults(results);
                    resetRunState();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    resetRunState();
                    setStatus("Optimization failed: " + ex.getMessage());
                    reportArea.appendText("[" + LocalTime.now() + "] ERROR " + ex.getMessage() + "\n");
                });
            }
        }, "route-optimizer").start();
    }

    private void resetRunState() {
        btnRun.setDisable(false);
        btnRun.setText("Dispatch Fleet");
        progressIndicator.setVisible(false);
        solverStatusLabel.setText("");
    }

    private void displayResults(List<Route> results) {
        Route best = results.stream()
                .min((left, right) -> Double.compare(left.getObjectiveScore(), right.getObjectiveScore()))
                .orElse(null);
        if (best == null) {
            return;
        }

        routeArea.setText(best.getRouteString());
        costLabel.setText(String.format("%.2f km", best.getTotalDistance()));
        timeLabel.setText(best.getExecutionTimeMs() + " ms");

        boolean deadlinesMet = best.isTimeWindowsMet();
        deadlineStatusLabel.setText(deadlinesMet ? "All deadlines met" : "Delivery deadline missed");
        deadlineStatusLabel.setTextFill(Color.web(deadlinesMet ? ACCENT_GREEN : ACCENT_RED));

        map.setRoute(best);
        map.setNoFlyZones(getAllNoFlyZones());
        currentBestRoute = best;
        updateBrowserMap(false);

        distChart.getData().clear();
        timeChart.getData().clear();

        XYChart.Series<String, Number> distanceSeries = new XYChart.Series<>();
        XYChart.Series<String, Number> timeSeries = new XYChart.Series<>();

        comparisonRows.getChildren().clear();
        for (Route route : results) {
            String shortName = route.getAlgorithmName().split(" ")[0];
            distanceSeries.getData().add(new XYChart.Data<>(shortName, route.getTotalDistance()));
            timeSeries.getData().add(new XYChart.Data<>(shortName, route.getExecutionTimeMs()));

            Label row = createLabel(
                    String.format("%-12s  %7.2f km   score %8.2f   %5d ms",
                            shortName, route.getTotalDistance(), route.getObjectiveScore(), route.getExecutionTimeMs()),
                    12,
                    FontWeight.NORMAL,
                    TEXT_SECONDARY
            );
            row.setStyle(row.getStyle() + "-fx-font-family: 'Consolas';");
            comparisonRows.getChildren().add(row);
        }

        distChart.getData().add(distanceSeries);
        timeChart.getData().add(timeSeries);

        reportArea.appendText(
                "[" + LocalTime.now() + "] " + best.getAlgorithmName() +
                        " | Distance " + String.format("%.2f", best.getTotalDistance()) +
                        " km | Time " + best.getExecutionTimeMs() + " ms\n"
        );
        setStatus("Best dispatch computed with " + best.getAlgorithmName() + ".");
        openBrowserMap(true);
    }

    private void onGenerate() {
        locations = MatrixGenerator.generateLocations(nodeSpinner.getValue());
        currentBestRoute = null;
        map.setLocations(locations, getAllNoFlyZones());
        updateMissionMetrics();
        updateBrowserMap(false);
        routeArea.setText("Locations generated. Run a dispatch to compare solver output.");
        setStatus("Generated " + locations.size() + " Manhattan delivery locations.");
        reportArea.appendText("[" + LocalTime.now() + "] Generated " + locations.size() + " locations.\n");
    }

    private void updateMissionMetrics() {
        nodeCountMetric.setText(String.valueOf(locations.size()));
        droneCountMetric.setText(String.valueOf(fleetSpinner.getValue()));
        nfzMetric.setText(String.valueOf(getAllNoFlyZones().size()));
    }

    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private List<NoFlyZone> getAllNoFlyZones() {
        List<NoFlyZone> combined = new ArrayList<>(noFlyZones);
        combined.addAll(BrowserZoneBridge.getInstance().getAdditionalZones());
        return combined;
    }

    private void updateBrowserMap(boolean openAfterExport) {
        if (locations.isEmpty()) {
            return;
        }

        try {
            latestBrowserMap = BrowserMapExporter.exportMap(locations, getAllNoFlyZones(), currentBestRoute);
            if (openAfterExport) {
                BrowserMapExporter.openInBrowser(latestBrowserMap);
                setStatus("Opened the full browser map.");
            }
        } catch (Exception ex) {
            setStatus("Browser map export failed: " + ex.getMessage());
        }
    }

    private void syncBrowserZones() {
        updateMissionMetrics();
        if (!locations.isEmpty()) {
            map.setNoFlyZones(getAllNoFlyZones());
            updateBrowserMap(false);
        }
        setStatus("Browser-drawn no-fly zones synced into the desktop app.");
    }

    private void openBrowserMap(boolean regenerateIfNeeded) {
        if (locations.isEmpty()) {
            setStatus("Generate map data before opening the browser map.");
            return;
        }

        try {
            if (regenerateIfNeeded || latestBrowserMap == null) {
                latestBrowserMap = BrowserMapExporter.exportMap(locations, getAllNoFlyZones(), currentBestRoute);
            }
            BrowserMapExporter.openInBrowser(latestBrowserMap);
            setStatus("Opened the full browser map.");
        } catch (Exception ex) {
            setStatus("Could not open browser map: " + ex.getMessage());
        }
    }

    private TitledPane createPane(String title, VBox content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(true);
        pane.setCollapsible(true);
        pane.setStyle(
                "-fx-text-fill: " + TEXT_PRIMARY + ";" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-color: " + BG_CARD + ";" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-border-radius: 18;" +
                        "-fx-background-radius: 18;"
        );
        return pane;
    }

    private VBox createCard() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.setStyle(
                "-fx-background-color: " + BG_CARD + ";" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-border-radius: 20;"
        );
        return box;
    }

    private VBox createMetricCard(String label, String value) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setMinWidth(118);
        box.setStyle(
                "-fx-background-color: rgba(255,255,255,0.04);" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-border-radius: 16;"
        );

        Label title = createLabel(label, 11, FontWeight.NORMAL, TEXT_SECONDARY);
        Label metric = createLabel(value, 18, FontWeight.BOLD, TEXT_PRIMARY);
        box.getChildren().addAll(title, metric);

        if ("Nodes".equals(label)) {
            nodeCountMetric = metric;
        } else if ("Drones".equals(label)) {
            droneCountMetric = metric;
        } else if ("No-Fly Zones".equals(label)) {
            nfzMetric = metric;
        }

        return box;
    }

    private VBox createInfoTile(String title) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(14));
        box.setStyle(
                "-fx-background-color: " + BG_CARD + ";" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-border-radius: 18;"
        );

        Label heading = createLabel(title, 11, FontWeight.NORMAL, TEXT_SECONDARY);
        Label value = createLabel("--", 16, FontWeight.BOLD, TEXT_PRIMARY);
        box.getChildren().addAll(heading, value);

        if ("Best Distance".equals(title)) {
            costLabel = value;
        } else if ("Execution Time".equals(title)) {
            timeLabel = value;
        } else if ("Deadline State".equals(title)) {
            deadlineStatusLabel = value;
        }

        return box;
    }

    private Label createSectionHeader(String text, String color) {
        return createLabel(text, 14, FontWeight.BOLD, color);
    }

    private Label createFieldLabel(String text) {
        return createLabel(text, 12, FontWeight.NORMAL, TEXT_SECONDARY);
    }

    private Label createLabel(String text, int size, FontWeight weight, String color) {
        Label label = new Label(text);
        label.setFont(Font.font("Segoe UI", weight, size));
        label.setTextFill(Color.web(color));
        return label;
    }

    private CheckBox createSolverCheckBox(String name, String description, String color) {
        CheckBox checkBox = new CheckBox(name + "  |  " + description);
        checkBox.setTextFill(Color.web(color));
        checkBox.setWrapText(true);
        checkBox.setContentDisplay(ContentDisplay.LEFT);
        return checkBox;
    }

    private Spinner<Integer> createIntegerSpinner(int min, int max, int initial) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial);
        spinner.setEditable(true);
        spinner.setMaxWidth(Double.MAX_VALUE);
        spinner.setStyle(
                "-fx-background-color: #0B1B30;" +
                        "-fx-text-fill: " + TEXT_PRIMARY + ";" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-radius: 12;"
        );
        return spinner;
    }

    private Button createPrimaryButton(String text, String color) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPadding(new Insets(12, 16, 12, 16));
        button.setTextFill(Color.web("#07111F"));
        button.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        button.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-background-radius: 14;"
        );
        return button;
    }

    private Button createSecondaryButton(String text, String color) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPadding(new Insets(11, 15, 11, 15));
        button.setTextFill(Color.web(TEXT_PRIMARY));
        button.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        button.setStyle(
                "-fx-background-color: rgba(255,255,255,0.04);" +
                        "-fx-background-radius: 14;" +
                        "-fx-border-color: " + color + "66;" +
                        "-fx-border-radius: 14;"
        );
        return button;
    }

    private BarChart<String, Number> createChart(String title, String yLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setTickLabelFill(Color.web(TEXT_SECONDARY));
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        yAxis.setTickLabelFill(Color.web(TEXT_SECONDARY));

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setCategoryGap(16);
        chart.setBarGap(6);
        chart.setStyle(
                "-fx-background-color: " + BG_CARD + ";" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: " + BORDER_SOFT + ";" +
                        "-fx-border-radius: 18;"
        );
        chart.setAnimated(false);
        return chart;
    }

    private FileChooser createFileChooser(String title, String description, String pattern) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, pattern));
        return chooser;
    }

    private File ensureExtension(File file, String extension) {
        if (file.getName().toLowerCase().endsWith(extension)) {
            return file;
        }
        return new File(file.getParentFile(), file.getName() + extension);
    }
}
