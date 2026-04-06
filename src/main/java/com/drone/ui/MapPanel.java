package com.drone.ui;

import com.drone.model.Location;
import com.drone.model.NoFlyZone;
import com.drone.model.Route;
import com.drone.utils.DistanceCalculator;
import com.google.gson.Gson;
import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Leaflet-backed visualizer rendered inside a JavaFX WebView.
 */
public class MapPanel extends StackPane {

    private static final String DEFAULT_STATUS = "Leaflet map ready. No API key or billing setup required.";
    private static final String[] ROUTE_COLORS = {
            "#4ade80", "#4fd1ff", "#fbbf24", "#f472b6",
            "#c084fc", "#fb923c", "#60a5fa", "#e879f9"
    };

    private final WebView webView;
    private final WebEngine engine;
    private final Gson gson = new Gson();

    private boolean isMapLoaded;
    private List<Location> locations;
    private List<NoFlyZone> noFlyZones;

    public MapPanel() {
        webView = new WebView();
        webView.setMinSize(0, 0);
        webView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        webView.prefWidthProperty().bind(widthProperty());
        webView.prefHeightProperty().bind(heightProperty());
        engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);

        String url = getClass().getResource("/map.html").toExternalForm();
        engine.load(url);

        engine.getLoadWorker().stateProperty().addListener((obs, oldV, newV) -> {
            if (newV == Worker.State.SUCCEEDED) {
                isMapLoaded = true;
                if (locations != null) {
                    updateMarkers();
                }
                if (noFlyZones != null) {
                    drawNoFlyZones();
                }
                drawGraphEdges();
            }
        });

        getChildren().add(webView);
        widthProperty().addListener((obs, oldV, newV) -> requestMapResize());
        heightProperty().addListener((obs, oldV, newV) -> requestMapResize());
    }

    public void setLocations(List<Location> locs, List<NoFlyZone> nfzs) {
        this.locations = locs;
        this.noFlyZones = nfzs;
        if (isMapLoaded) {
            updateMarkers();
            drawNoFlyZones();
            drawGraphEdges();
        }
    }

    public void setRoute(Route route) {
        if (!isMapLoaded) {
            return;
        }

        executeScript("clearRoutes();");
        if (route == null) {
            executeScript("setStatus('Routes cleared.');");
            return;
        }

        for (int i = 0; i < route.getDronePaths().size(); i++) {
            Route.DronePath dronePath = route.getDronePaths().get(i);
            String color = ROUTE_COLORS[i % ROUTE_COLORS.length];
            List<double[]> coords = buildRenderedPath(dronePath.getPath());
            List<String> edgeDistances = buildEdgeDistances(dronePath.getPath());

            String coordsJson = gson.toJson(coords);
            executeScript("drawPath(" + toJsString(coordsJson) + ", " + toJsString(color) + ");");
            executeScript("drawEdgeDistances(" + toJsString(gson.toJson(dronePath.getPath().stream()
                    .map(location -> new double[]{location.getLat(), location.getLng()})
                    .collect(Collectors.toList()))) + ", " + toJsString(gson.toJson(edgeDistances)) + ");");
        }

        executeScript("fitAll();");
    }

    public void setNoFlyZones(List<NoFlyZone> nfzs) {
        this.noFlyZones = nfzs;
        if (isMapLoaded) {
            drawNoFlyZones();
        }
    }

    public void drawGraphEdges() {
        if (!isMapLoaded) {
            return;
        }
        executeScript("drawGraphEdges(" + toJsString(gson.toJson(DistanceCalculator.getGraphEdges())) + ");");
    }

    public void resetView() {
        if (!isMapLoaded || locations == null || locations.isEmpty()) {
            return;
        }
        Location base = locations.get(0);
        executeScript(String.format("centerMap(%s, %s, 13);", formatNumber(base.getLat()), formatNumber(base.getLng())));
    }

    public void fitAll() {
        if (isMapLoaded) {
            executeScript("fitAll();");
        }
    }

    public void clearNoFlyZones() {
        if (isMapLoaded) {
            executeScript("clearNFZ();");
            executeScript("setStatus('No-fly zones hidden.');");
        }
    }

    public String getMapConfigurationStatus() {
        return DEFAULT_STATUS;
    }

    private void updateMarkers() {
        if (!isMapLoaded || locations == null) {
            return;
        }

        executeScript("clearMarkers();");
        for (Location location : locations) {
            String description = location.getId() == 0
                    ? location.getName() + " | Depot"
                    : String.format("%s | %s Priority | Ready %.0f min | Due %.0f min",
                    location.getName(), location.getPriority().getLabel(), location.getReadyTime(), location.getDueTime());

            executeScript(String.format(
                    "addMarker(%d, %s, %s, %s, %s);",
                    location.getId(),
                    formatNumber(location.getLat()),
                    formatNumber(location.getLng()),
                    toJsString(description),
                    location.getId() == 0
            ));
        }

        if (!locations.isEmpty()) {
            resetView();
        }
    }

    private void drawNoFlyZones() {
        if (!isMapLoaded) {
            return;
        }

        executeScript("clearNFZ();");
        if (noFlyZones == null) {
            return;
        }

        for (NoFlyZone zone : noFlyZones) {
            executeScript(String.format(
                    "drawNoFlyZone(%s, %s, %s, %s, %s);",
                    formatNumber(zone.getMinLng()),
                    formatNumber(zone.getMinLat()),
                    formatNumber(zone.getMaxLng()),
                    formatNumber(zone.getMaxLat()),
                    toJsString("#ff6b6b")
            ));
        }
    }

    private void executeScript(String script) {
        engine.executeScript(script);
    }

    private void requestMapResize() {
        if (!isMapLoaded) {
            return;
        }
        executeScript("resizeMap();");
    }

    private List<String> buildEdgeDistances(List<Location> path) {
        return java.util.stream.IntStream.range(0, Math.max(0, path.size() - 1))
                .mapToObj(i -> String.format("%.2f km",
                        DistanceCalculator.pathDistance(DistanceCalculator.buildPath(path.get(i), path.get(i + 1), null))))
                .collect(Collectors.toList());
    }

    private List<double[]> buildRenderedPath(List<Location> stops) {
        List<double[]> fullPath = new java.util.ArrayList<>();
        for (int i = 0; i < Math.max(0, stops.size() - 1); i++) {
            List<double[]> segment = DistanceCalculator.buildPath(stops.get(i), stops.get(i + 1), noFlyZones);
            if (segment.isEmpty()) {
                continue;
            }
            List<double[]> normalized = new java.util.ArrayList<>(segment);
            if (!fullPath.isEmpty()) {
                normalized.remove(0);
            }
            fullPath.addAll(normalized);
        }
        return fullPath.isEmpty()
                ? stops.stream().map(location -> new double[]{location.getLat(), location.getLng()}).collect(Collectors.toList())
                : fullPath;
    }

    private String toJsString(String value) {
        return gson.toJson(value == null ? "" : value);
    }

    private String formatNumber(double value) {
        return Double.toString(value);
    }
}
