package com.drone.utils;

import com.drone.model.Location;
import com.drone.model.NoFlyZone;
import com.drone.model.Route;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 4; // Incremented for new format

    /**
     * Saves locations, no-fly zones, and algorithm results to a JSON file.
     */
    public static void save(List<Location> locations, List<NoFlyZone> noFlyZones,
                            List<Route> results, File file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);

        // Locations (full data)
        JsonArray locsArr = new JsonArray();
        for (Location l : locations) {
            JsonObject o = new JsonObject();
            o.addProperty("id",        l.getId());
            o.addProperty("name",      l.getName());
            o.addProperty("lat",       l.getLat());
            o.addProperty("lng",       l.getLng());
            o.addProperty("readyTime", l.getReadyTime());
            o.addProperty("dueTime",   l.getDueTime());
            o.addProperty("priority",  l.getPriority().name());
            // Legacy X/Y not stored – no longer needed
            locsArr.add(o);
        }
        root.add("locations", locsArr);

        // No-fly zones (new lat/lng format)
        JsonArray nfzArr = new JsonArray();
        for (NoFlyZone z : noFlyZones) {
            JsonObject o = new JsonObject();
            o.addProperty("minLng", z.getMinLng());
            o.addProperty("maxLng", z.getMaxLng());
            o.addProperty("minLat", z.getMinLat());
            o.addProperty("maxLat", z.getMaxLat());
            nfzArr.add(o);
        }
        root.add("noFlyZones", nfzArr);

        // Results (optional)
        if (results != null && !results.isEmpty()) {
            JsonArray resArr = new JsonArray();
            for (Route r : results) {
                JsonObject o = new JsonObject();
                o.addProperty("algorithm",     r.getAlgorithmName());
                o.addProperty("totalDistance", r.getTotalDistance());
                o.addProperty("executionMs",   r.getExecutionTimeMs());
                o.addProperty("route",         r.getRouteString());
                resArr.add(o);
            }
            root.add("results", resArr);
        }

        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        }
    }

    /**
     * Loads locations, no-fly zones, and previous results from a JSON file.
     */
    public static LoadResult load(File file) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            int version = root.has("version") ? root.get("version").getAsInt() : 1;

            List<Location> locs = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("locations")) {
                JsonObject o = el.getAsJsonObject();
                // Support old format (with x,y) and new format (lat,lng,readyTime,dueTime)
                if (o.has("lat") && o.has("lng")) {
                    double ready = o.has("readyTime") ? o.get("readyTime").getAsDouble() : 0.0;
                    double due   = o.has("dueTime")   ? o.get("dueTime").getAsDouble()   : 1440.0;
                    Location.Priority priority = o.has("priority")
                            ? Location.Priority.fromString(o.get("priority").getAsString())
                            : Location.Priority.NORMAL;
                    locs.add(new Location(
                            o.get("id").getAsInt(),
                            o.get("name").getAsString(),
                            o.get("lat").getAsDouble(),
                            o.get("lng").getAsDouble(),
                            ready, due, priority
                    ));
                } else {
                    // Legacy pixel-based location – convert to approximate lat/lng (dummy)
                    double x = o.get("x").getAsDouble();
                    double y = o.get("y").getAsDouble();
                    double lat = 40.7128 - (y - 300) / 111000.0; // crude conversion
                    double lng = -74.0060 + (x - 400) / 85000.0;
                    locs.add(new Location(
                            o.get("id").getAsInt(),
                            o.get("name").getAsString(),
                            lat, lng, 0.0, 1440.0, Location.Priority.NORMAL
                    ));
                }
            }

            List<NoFlyZone> nfzs = new ArrayList<>();
            if (root.has("noFlyZones")) {
                for (JsonElement el : root.getAsJsonArray("noFlyZones")) {
                    JsonObject o = el.getAsJsonObject();
                    // Support both old (x,y,w,h) and new (minLng,minLat,maxLng,maxLat) formats
                    if (o.has("minLng")) {
                        nfzs.add(new NoFlyZone(
                                o.get("minLng").getAsDouble(),
                                o.get("minLat").getAsDouble(),
                                o.get("maxLng").getAsDouble(),
                                o.get("maxLat").getAsDouble()
                        ));
                    } else {
                        // Legacy pixel zone – convert to lat/lng (rough approximation)
                        double x = o.get("x").getAsDouble();
                        double y = o.get("y").getAsDouble();
                        double w = o.get("w").getAsDouble();
                        double h = o.get("h").getAsDouble();
                        double minLng = -74.0060 + (x - 400) / 85000.0;
                        double maxLng = minLng + w / 85000.0;
                        double maxLat = 40.7128 - (y - 300) / 111000.0;
                        double minLat = maxLat - h / 111000.0;
                        nfzs.add(new NoFlyZone(minLng, minLat, maxLng, maxLat));
                    }
                }
            }

            List<Route> results = new ArrayList<>();
            if (root.has("results")) {
                for (JsonElement el : root.getAsJsonArray("results")) {
                    JsonObject o = el.getAsJsonObject();
                    results.add(new Route(
                            null,
                            o.get("totalDistance").getAsDouble(),
                            o.get("executionMs").getAsLong(),
                            o.get("algorithm").getAsString()
                    ));
                }
            }

            return new LoadResult(locs, nfzs, results);
        }
    }

    /**
     * Exports current session data to a CSV file (locations + no-fly zones).
     */
    public static void exportToCSV(List<Location> locations, List<NoFlyZone> noFlyZones, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.println("Type,ID,Name,Priority,Lat,Lng,ReadyTime,DueTime,MinLng,MinLat,MaxLng,MaxLat");
            for (Location l : locations) {
                pw.printf("Location,%d,%s,%s,%.6f,%.6f,%.1f,%.1f,,,,\n",
                        l.getId(), l.getName(), l.getPriority().name(), l.getLat(), l.getLng(), l.getReadyTime(), l.getDueTime());
            }
            for (NoFlyZone z : noFlyZones) {
                pw.printf("NoFlyZone,,,,,,,,%.6f,%.6f,%.6f,%.6f\n",
                        z.getMinLng(), z.getMinLat(), z.getMaxLng(), z.getMaxLat());
            }
        }
    }

    public record LoadResult(List<Location> locations, List<NoFlyZone> noFlyZones, List<Route> lastResults) {}
}
