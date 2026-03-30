package com.drone.utils;

import com.drone.model.NoFlyZone;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public final class BrowserZoneBridge {

    private static final BrowserZoneBridge INSTANCE = new BrowserZoneBridge();
    private static final double LINE_PAD_LAT = 0.0012;
    private static final double LINE_PAD_LNG = 0.0014;

    private final Gson gson = new Gson();
    private HttpServer server;
    private int port;
    private String rawZonesJson = "[]";
    private List<NoFlyZone> additionalZones = new ArrayList<>();

    private BrowserZoneBridge() {
    }

    public static BrowserZoneBridge getInstance() {
        return INSTANCE;
    }

    public synchronized void startIfNeeded() {
        if (server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/browser-zones", new ZoneHandler());
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            port = server.getAddress().getPort();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to start browser zone bridge.", ex);
        }
    }

    public synchronized String getBaseUrl() {
        startIfNeeded();
        return "http://127.0.0.1:" + port;
    }

    public synchronized List<NoFlyZone> getAdditionalZones() {
        return new ArrayList<>(additionalZones);
    }

    public synchronized String getRawZonesJson() {
        return rawZonesJson;
    }

    private synchronized void updateZones(String body) {
        rawZonesJson = body == null || body.isBlank() ? "[]" : body;
        additionalZones = parseZones(rawZonesJson);
    }

    private List<NoFlyZone> parseZones(String json) {
        List<NoFlyZone> zones = new ArrayList<>();
        JsonArray array = gson.fromJson(json, JsonArray.class);
        if (array == null) {
            return zones;
        }

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject feature = element.getAsJsonObject();
            JsonObject geometry = feature.has("geometry") ? feature.getAsJsonObject("geometry") : feature;
            if (geometry == null || !geometry.has("type") || !geometry.has("coordinates")) {
                continue;
            }

            String type = geometry.get("type").getAsString();
            if ("Polygon".equals(type)) {
                addPolygonZone(geometry.getAsJsonArray("coordinates"), zones);
            } else if ("LineString".equals(type)) {
                addLineZone(geometry.getAsJsonArray("coordinates"), zones);
            }
        }

        return zones;
    }

    private void addPolygonZone(JsonArray coordinates, List<NoFlyZone> zones) {
        if (coordinates == null || coordinates.isEmpty()) {
            return;
        }
        JsonArray ring = coordinates.get(0).getAsJsonArray();
        double minLng = Double.POSITIVE_INFINITY;
        double maxLng = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;

        for (JsonElement pointElement : ring) {
            JsonArray point = pointElement.getAsJsonArray();
            double lng = point.get(0).getAsDouble();
            double lat = point.get(1).getAsDouble();
            minLng = Math.min(minLng, lng);
            maxLng = Math.max(maxLng, lng);
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
        }

        if (Double.isFinite(minLng)) {
            zones.add(new NoFlyZone(minLng, minLat, maxLng, maxLat));
        }
    }

    private void addLineZone(JsonArray coordinates, List<NoFlyZone> zones) {
        if (coordinates == null || coordinates.isEmpty()) {
            return;
        }

        double minLng = Double.POSITIVE_INFINITY;
        double maxLng = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;

        for (JsonElement pointElement : coordinates) {
            JsonArray point = pointElement.getAsJsonArray();
            double lng = point.get(0).getAsDouble();
            double lat = point.get(1).getAsDouble();
            minLng = Math.min(minLng, lng);
            maxLng = Math.max(maxLng, lng);
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
        }

        if (Double.isFinite(minLng)) {
            zones.add(new NoFlyZone(
                    minLng - LINE_PAD_LNG,
                    minLat - LINE_PAD_LAT,
                    maxLng + LINE_PAD_LNG,
                    maxLat + LINE_PAD_LAT
            ));
        }
    }

    private final class ZoneHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] body = getRawZonesJson().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body;
                try (InputStream input = exchange.getRequestBody()) {
                    body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                updateZones(body);
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        }

        private void addCors(HttpExchange exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        }
    }
}
