package com.drone.utils;

import com.drone.model.Location;
import com.drone.model.NoFlyZone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DistanceCalculator {

    public static final double INF = 1e15;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double PAD_LAT = 0.0030;
    private static final double PAD_LNG = 0.0035;
    private static final Map<String, Double> distanceCache = new HashMap<>();
    private static final Map<String, List<double[]>> pathCache = new HashMap<>();

    public static double haversine(Location a, Location b) {
        String key = a.getId() + "-" + b.getId();
        if (distanceCache.containsKey(key)) {
            return distanceCache.get(key);
        }

        double dist = haversine(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        distanceCache.put(key, dist);
        distanceCache.put(b.getId() + "-" + a.getId(), dist);
        return dist;
    }

    public static double haversine(double lat1Deg, double lng1Deg, double lat2Deg, double lng2Deg) {
        double lat1 = Math.toRadians(lat1Deg);
        double lon1 = Math.toRadians(lng1Deg);
        double lat2 = Math.toRadians(lat2Deg);
        double lon2 = Math.toRadians(lng2Deg);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double sa = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(sa), Math.sqrt(1 - sa));
        return EARTH_RADIUS_KM * c;
    }

    public static double[][] buildMatrix(List<Location> locations, List<NoFlyZone> noFlyZones) {
        int n = locations.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = 0;
                    continue;
                }
                List<double[]> path = buildPath(locations.get(i), locations.get(j), noFlyZones);
                matrix[i][j] = path.isEmpty() ? INF : pathDistance(path);
            }
        }
        return matrix;
    }

    public static List<double[]> buildPath(Location from, Location to, List<NoFlyZone> noFlyZones) {
        String cacheKey = buildPathCacheKey(from, to, noFlyZones);
        if (pathCache.containsKey(cacheKey)) {
            return clonePath(pathCache.get(cacheKey));
        }

        List<double[]> path = new ArrayList<>();
        path.add(point(from.getLat(), from.getLng()));
        path.add(point(to.getLat(), to.getLng()));

        if (noFlyZones == null || noFlyZones.isEmpty()) {
            pathCache.put(cacheKey, clonePath(path));
            return path;
        }

        Set<String> seenStates = new HashSet<>();
        while (seenStates.add(pathSignature(path))) {
            NoFlyZone zone = firstIntersectingZone(path, noFlyZones);
            if (zone == null) {
                pathCache.put(cacheKey, clonePath(path));
                return path;
            }
            path = rerouteAroundZone(path, zone, noFlyZones);
            if (path.isEmpty()) {
                pathCache.put(cacheKey, List.of());
                return path;
            }
        }

        pathCache.put(cacheKey, List.of());
        return List.of();
    }

    public static double pathDistance(List<double[]> path) {
        if (path == null || path.size() < 2) {
            return 0.0;
        }

        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            double[] a = path.get(i);
            double[] b = path.get(i + 1);
            total += haversine(a[0], a[1], b[0], b[1]);
        }
        return total;
    }

    public static boolean isBlocked(Location a, Location b, List<NoFlyZone> noFlyZones) {
        return firstIntersectingZone(buildDirectPath(a, b), noFlyZones) != null;
    }

    public static void clearCache() {
        distanceCache.clear();
        pathCache.clear();
    }

    private static List<double[]> buildDirectPath(Location a, Location b) {
        List<double[]> path = new ArrayList<>();
        path.add(point(a.getLat(), a.getLng()));
        path.add(point(b.getLat(), b.getLng()));
        return path;
    }

    private static NoFlyZone firstIntersectingZone(List<double[]> path, List<NoFlyZone> zones) {
        if (zones == null) {
            return null;
        }
        for (int i = 0; i < path.size() - 1; i++) {
            double[] start = path.get(i);
            double[] end = path.get(i + 1);
            for (NoFlyZone zone : zones) {
                if (segmentIntersectsZone(start, end, zone)) {
                    return zone;
                }
            }
        }
        return null;
    }

    private static List<double[]> rerouteAroundZone(List<double[]> path, NoFlyZone zone, List<NoFlyZone> allZones) {
        List<double[]> rerouted = new ArrayList<>();
        rerouted.add(path.get(0));

        for (int i = 0; i < path.size() - 1; i++) {
            double[] start = path.get(i);
            double[] end = path.get(i + 1);
            if (!segmentIntersectsZone(start, end, zone)) {
                rerouted.add(end);
                continue;
            }

            List<double[]> detour = buildDetour(start, end, zone, allZones);
            if (detour.isEmpty()) {
                return List.of();
            }
            for (int j = 1; j < detour.size(); j++) {
                rerouted.add(detour.get(j));
            }
        }

        return dedupeSequentialPoints(rerouted);
    }

    private static List<double[]> buildDetour(double[] start, double[] end, NoFlyZone zone, List<NoFlyZone> allZones) {
        double minLat = zone.getMinLat() - PAD_LAT;
        double maxLat = zone.getMaxLat() + PAD_LAT;
        double minLng = zone.getMinLng() - PAD_LNG;
        double maxLng = zone.getMaxLng() + PAD_LNG;

        double[] sw = point(minLat, minLng);
        double[] se = point(minLat, maxLng);
        double[] nw = point(maxLat, minLng);
        double[] ne = point(maxLat, maxLng);

        List<List<double[]>> candidates = List.of(
                List.of(start, nw, ne, end),
                List.of(start, sw, se, end),
                List.of(start, sw, nw, end),
                List.of(start, se, ne, end)
        );
        List<List<double[]>> orderedCandidates = new ArrayList<>(candidates);
        orderedCandidates.sort(Comparator.comparingDouble(DistanceCalculator::pathDistance));

        List<List<double[]>> valid = new ArrayList<>();
        for (List<double[]> candidate : orderedCandidates) {
            if (firstIntersectingZone(candidate, allZones) == null) {
                valid.add(candidate);
            }
        }

        List<List<double[]>> pool = valid.isEmpty() ? orderedCandidates : valid;
        List<double[]> best = null;
        double bestDistance = INF;
        for (List<double[]> candidate : pool) {
            double candidateDistance = pathDistance(candidate);
            if (candidateDistance < bestDistance) {
                bestDistance = candidateDistance;
                best = candidate;
            }
        }
        return best == null ? List.of() : dedupeSequentialPoints(best);
    }

    private static boolean segmentIntersectsZone(double[] start, double[] end, NoFlyZone zone) {
        if (!segmentBoundingBoxOverlaps(start, end, zone)) {
            return false;
        }
        return zone.intersectsPath(start[1], start[0], end[1], end[0]);
    }

    private static List<double[]> dedupeSequentialPoints(List<double[]> points) {
        List<double[]> deduped = new ArrayList<>();
        for (double[] point : points) {
            if (deduped.isEmpty()) {
                deduped.add(point);
                continue;
            }
            double[] last = deduped.get(deduped.size() - 1);
            if (Math.abs(last[0] - point[0]) > 1e-9 || Math.abs(last[1] - point[1]) > 1e-9) {
                deduped.add(point);
            }
        }
        return deduped;
    }

    private static double[] point(double lat, double lng) {
        return new double[]{lat, lng};
    }

    private static boolean segmentBoundingBoxOverlaps(double[] start, double[] end, NoFlyZone zone) {
        double minLat = Math.min(start[0], end[0]);
        double maxLat = Math.max(start[0], end[0]);
        double minLng = Math.min(start[1], end[1]);
        double maxLng = Math.max(start[1], end[1]);
        return maxLat >= zone.getMinLat() && minLat <= zone.getMaxLat()
                && maxLng >= zone.getMinLng() && minLng <= zone.getMaxLng();
    }

    private static String buildPathCacheKey(Location from, Location to, List<NoFlyZone> zones) {
        StringBuilder builder = new StringBuilder();
        builder.append(from.getId()).append("->").append(to.getId()).append('|');
        if (zones != null) {
            List<NoFlyZone> normalized = new ArrayList<>(zones);
            normalized.sort(Comparator
                    .comparingDouble(NoFlyZone::getMinLat)
                    .thenComparingDouble(NoFlyZone::getMinLng)
                    .thenComparingDouble(NoFlyZone::getMaxLat)
                    .thenComparingDouble(NoFlyZone::getMaxLng));
            for (NoFlyZone zone : normalized) {
                builder.append(zone.getMinLat()).append(',')
                        .append(zone.getMinLng()).append(',')
                        .append(zone.getMaxLat()).append(',')
                        .append(zone.getMaxLng()).append(';');
            }
        }
        return builder.toString();
    }

    private static String pathSignature(List<double[]> path) {
        StringBuilder builder = new StringBuilder();
        for (double[] point : path) {
            builder.append(Math.round(point[0] * 1_000_000)).append(':')
                    .append(Math.round(point[1] * 1_000_000)).append('|');
        }
        return builder.toString();
    }

    private static List<double[]> clonePath(List<double[]> path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        List<double[]> clone = new ArrayList<>(path.size());
        for (double[] point : path) {
            clone.add(new double[]{point[0], point[1]});
        }
        return clone;
    }
}
