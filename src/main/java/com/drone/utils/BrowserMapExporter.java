package com.drone.utils;

import com.drone.model.Location;
import com.drone.model.NoFlyZone;
import com.drone.model.Route;
import com.google.gson.Gson;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class BrowserMapExporter {

    private static final Gson GSON = new Gson();

    private BrowserMapExporter() {
    }

    public static Path exportMap(List<Location> locations, List<NoFlyZone> noFlyZones, Route route) {
        String template = readTemplate();
        String html = template.replace("__STATE_JSON__", buildStateJson(locations, noFlyZones, route));

        try {
            Path file = Files.createTempFile("drone-browser-map-", ".html");
            Files.writeString(file, html, StandardCharsets.UTF_8);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to export browser map.", ex);
        }
    }

    public static void openInBrowser(Path file) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Desktop browser launch is not supported on this machine.");
        }
        Desktop.getDesktop().browse(file.toUri());
    }

    private static String buildStateJson(List<Location> locations, List<NoFlyZone> noFlyZones, Route route) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("locations", locations.stream().map(BrowserMapExporter::toLocationMap).collect(Collectors.toList()));
        state.put("noFlyZones", noFlyZones.stream().map(BrowserMapExporter::toZoneMap).collect(Collectors.toList()));
        state.put("graphEdges", DistanceCalculator.getGraphEdges().stream()
                .map(edge -> edge.stream().map(point -> List.of(point[0], point[1])).collect(Collectors.toList()))
                .collect(Collectors.toList()));
        state.put("bridgeUrl", BrowserZoneBridge.getInstance().getBaseUrl());
        state.put("routes", route == null
                ? List.of()
                : route.getDronePaths().stream().map(path -> toRouteMap(path, noFlyZones)).collect(Collectors.toList()));
        return GSON.toJson(state);
    }

    private static Map<String, Object> toLocationMap(Location location) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", location.getId());
        map.put("name", location.getName());
        map.put("lat", location.getLat());
        map.put("lng", location.getLng());
        map.put("readyTime", location.getReadyTime());
        map.put("dueTime", location.getDueTime());
        map.put("priority", location.getPriority().getLabel());
        return map;
    }

    private static Map<String, Object> toZoneMap(NoFlyZone zone) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("minLat", zone.getMinLat());
        map.put("minLng", zone.getMinLng());
        map.put("maxLat", zone.getMaxLat());
        map.put("maxLng", zone.getMaxLng());
        return map;
    }

    private static Map<String, Object> toRouteMap(Route.DronePath path, List<NoFlyZone> noFlyZones) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("droneId", path.getDroneId());
        map.put("distance", path.getDistance());
        map.put("path", path.getPath().stream()
                .map(location -> List.of(location.getLat(), location.getLng()))
                .collect(Collectors.toList()));
        map.put("stops", path.getPath().stream()
                .map(Location::getName)
                .collect(Collectors.toList()));
        map.put("stopPriorities", path.getPath().stream()
                .map(location -> location.getPriority().getLabel())
                .collect(Collectors.toList()));
        map.put("edgePaths", IntStream.range(0, Math.max(0, path.getPath().size() - 1))
                .mapToObj(i -> DistanceCalculator.buildPath(path.getPath().get(i), path.getPath().get(i + 1), noFlyZones).stream()
                        .map(point -> List.of(point[0], point[1]))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList()));
        map.put("edgeDistances", IntStream.range(0, Math.max(0, path.getPath().size() - 1))
                .mapToObj(i -> String.format("%.2f km",
                        DistanceCalculator.pathDistance(DistanceCalculator.buildPath(path.getPath().get(i), path.getPath().get(i + 1), noFlyZones))))
                .collect(Collectors.toList()));
        return map;
    }

    private static String readTemplate() {
        try (InputStream stream = Objects.requireNonNull(
                BrowserMapExporter.class.getResourceAsStream("/browser-map-template.html"),
                "Missing browser map template"
        )) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read browser map template.", ex);
        }
    }
}
