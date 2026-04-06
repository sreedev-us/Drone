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
    private static final int ROAD_NEIGHBOR_COUNT = 4;
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double PAD_LAT = 0.0030;
    private static final double PAD_LNG = 0.0035;
    private static final Map<String, Double> distanceCache = new HashMap<>();
    private static final Map<String, List<double[]>> pathCache = new HashMap<>();
    private static final Map<String, RoadEdge> roadEdgeCache = new HashMap<>();
    private static GraphContext graphContext;

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
        if (n == 0) {
            graphContext = null;
            return new double[0][0];
        }

        String zoneSignature = zoneSignature(noFlyZones);
        double[][] adjacency = new double[n][n];
        int[][] next = new int[n][n];
        Map<Long, List<double[]>> edgePaths = new HashMap<>();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                adjacency[i][j] = i == j ? 0.0 : INF;
                next[i][j] = i == j ? j : -1;
            }
        }

        for (int i = 0; i < n; i++) {
            List<Integer> neighbors = selectRoadNeighbors(locations, i);
            for (int neighbor : neighbors) {
                addRoadEdge(locations, i, neighbor, noFlyZones, adjacency, next, edgePaths);
                addRoadEdge(locations, neighbor, i, noFlyZones, adjacency, next, edgePaths);
            }
        }

        for (int via = 0; via < n; via++) {
            for (int from = 0; from < n; from++) {
                if (adjacency[from][via] >= INF) {
                    continue;
                }
                for (int to = 0; to < n; to++) {
                    if (adjacency[via][to] >= INF) {
                        continue;
                    }
                    double candidate = adjacency[from][via] + adjacency[via][to];
                    if (candidate < adjacency[from][to]) {
                        adjacency[from][to] = candidate;
                        next[from][to] = next[from][via];
                    }
                }
            }
        }

        graphContext = new GraphContext(locations, adjacency, next, edgePaths, zoneSignature);
        return adjacency;
    }

    public static List<double[]> buildPath(Location from, Location to, List<NoFlyZone> noFlyZones) {
        if (graphContext != null && graphContext.matches(from, to, noFlyZones)) {
            List<double[]> graphPath = graphContext.buildPath(from.getId(), to.getId());
            if (!graphPath.isEmpty()) {
                return graphPath;
            }
        }

        String cacheKey = buildPathCacheKey(from, to, noFlyZones);
        if (pathCache.containsKey(cacheKey)) {
            return clonePath(pathCache.get(cacheKey));
        }

        RoadEdge roadEdge = fetchRoadEdge(from, to, noFlyZones);
        List<double[]> path = roadEdge.path();
        if (path.isEmpty()) {
            path = buildFallbackPath(from, to, noFlyZones);
        }

        pathCache.put(cacheKey, clonePath(path));
        return path;
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
        roadEdgeCache.clear();
        graphContext = null;
    }

    public static List<List<double[]>> getGraphEdges() {
        return graphContext == null ? List.of() : graphContext.getAllEdgePaths();
    }

    /**
     * Returns a list of [fromLat, fromLng, toLat, toLng] tuples, one per directed
     * edge in the current graph. Used by the JS map to fetch real road geometry
     * from OSRM rather than rendering pre-computed L-shaped paths.
     */
    public static List<double[]> getGraphEdgeEndpoints() {
        return graphContext == null ? List.of() : graphContext.getAllEdgeEndpoints();
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

    private static List<Integer> selectRoadNeighbors(List<Location> locations, int sourceIndex) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            if (i != sourceIndex) {
                candidates.add(i);
            }
        }

        candidates.sort(Comparator.comparingDouble((Integer idx) -> haversine(locations.get(sourceIndex), locations.get(idx))));
        if (sourceIndex == 0) {
            return candidates;
        }

        List<Integer> selected = new ArrayList<>();
        selected.add(0);
        for (int candidate : candidates) {
            if (candidate == 0) {
                continue;
            }
            selected.add(candidate);
            if (selected.size() >= ROAD_NEIGHBOR_COUNT + 1) {
                break;
            }
        }
        return selected;
    }

    private static void addRoadEdge(List<Location> locations, int from, int to, List<NoFlyZone> noFlyZones,
                                    double[][] adjacency, int[][] next, Map<Long, List<double[]>> edgePaths) {
        if (from == to) {
            return;
        }

        Location source = locations.get(from);
        Location target = locations.get(to);
        RoadEdge edge = fetchRoadEdge(source, target, noFlyZones);
        if (edge.path().isEmpty()) {
            return;
        }

        adjacency[from][to] = edge.distanceKm();
        next[from][to] = to;
        edgePaths.put(edgeKey(from, to), clonePath(edge.path()));
    }

    private static RoadEdge fetchRoadEdge(Location from, Location to, List<NoFlyZone> noFlyZones) {
        String key = from.getId() + "->" + to.getId() + "|" + zoneSignature(noFlyZones);
        RoadEdge cached = roadEdgeCache.get(key);
        if (cached != null) {
            return cached;
        }

        List<List<double[]>> candidates = buildRoadCandidates(from, to);
        List<double[]> bestPath = List.of();
        double bestDistance = INF;
        for (List<double[]> candidate : candidates) {
            List<double[]> resolved = resolveNoFlyConflicts(candidate, noFlyZones);
            if (resolved.isEmpty()) {
                continue;
            }
            double candidateDistance = pathDistance(resolved);
            if (candidateDistance < bestDistance) {
                bestDistance = candidateDistance;
                bestPath = resolved;
            }
        }

        if (bestPath.isEmpty()) {
            return cacheRoadEdge(key, new RoadEdge(INF, List.of()));
        }
        return cacheRoadEdge(key, new RoadEdge(bestDistance, bestPath));
    }

    private static RoadEdge cacheRoadEdge(String key, RoadEdge edge) {
        roadEdgeCache.put(key, edge);
        return edge;
    }

    private static List<double[]> buildFallbackPath(Location from, Location to, List<NoFlyZone> noFlyZones) {
        List<double[]> path = new ArrayList<>();
        path.add(point(from.getLat(), from.getLng()));
        path.add(point(to.getLat(), to.getLng()));

        if (noFlyZones == null || noFlyZones.isEmpty()) {
            return path;
        }

        Set<String> seenStates = new HashSet<>();
        while (seenStates.add(pathSignature(path))) {
            NoFlyZone zone = firstIntersectingZone(path, noFlyZones);
            if (zone == null) {
                return path;
            }
            path = rerouteAroundZone(path, zone, noFlyZones);
            if (path.isEmpty()) {
                return path;
            }
        }
        return List.of();
    }

    private static List<List<double[]>> buildRoadCandidates(Location from, Location to) {
        double[] start = point(from.getLat(), from.getLng());
        double[] end = point(to.getLat(), to.getLng());
        double[] viaLatFirst = point(to.getLat(), from.getLng());
        double[] viaLngFirst = point(from.getLat(), to.getLng());

        List<List<double[]>> candidates = new ArrayList<>();
        candidates.add(dedupeSequentialPoints(List.of(start, viaLatFirst, end)));
        candidates.add(dedupeSequentialPoints(List.of(start, viaLngFirst, end)));

        double midLat = (from.getLat() + to.getLat()) / 2.0;
        double midLng = (from.getLng() + to.getLng()) / 2.0;
        candidates.add(dedupeSequentialPoints(List.of(start, point(midLat, from.getLng()), point(midLat, to.getLng()), end)));
        candidates.add(dedupeSequentialPoints(List.of(start, point(from.getLat(), midLng), point(to.getLat(), midLng), end)));
        return candidates;
    }

    private static List<double[]> resolveNoFlyConflicts(List<double[]> candidate, List<NoFlyZone> noFlyZones) {
        if (noFlyZones == null || noFlyZones.isEmpty()) {
            return candidate;
        }

        List<double[]> path = new ArrayList<>(candidate);
        Set<String> seenStates = new HashSet<>();
        while (seenStates.add(pathSignature(path))) {
            NoFlyZone zone = firstIntersectingZone(path, noFlyZones);
            if (zone == null) {
                return dedupeSequentialPoints(path);
            }
            path = rerouteAroundZone(path, zone, noFlyZones);
            if (path.isEmpty()) {
                return List.of();
            }
        }
        return List.of();
    }

    private static boolean pathIntersectsZones(List<double[]> path, List<NoFlyZone> zones) {
        return firstIntersectingZone(path, zones) != null;
    }

    private static long edgeKey(int from, int to) {
        return (((long) from) << 32) | (to & 0xffffffffL);
    }

    private static String zoneSignature(List<NoFlyZone> zones) {
        if (zones == null || zones.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
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
        return builder.toString();
    }

    private record RoadEdge(double distanceKm, List<double[]> path) {
    }

    private static final class GraphContext {
        private final Map<Integer, Location> locationsById = new HashMap<>();
        private final double[][] distances;
        private final int[][] next;
        private final Map<Long, List<double[]>> edgePaths;
        private final String zoneSignature;

        private GraphContext(List<Location> locations, double[][] distances, int[][] next,
                             Map<Long, List<double[]>> edgePaths, String zoneSignature) {
            for (Location location : locations) {
                locationsById.put(location.getId(), location);
            }
            this.distances = distances;
            this.next = next;
            this.edgePaths = edgePaths;
            this.zoneSignature = zoneSignature;
        }

        private boolean matches(Location from, Location to, List<NoFlyZone> zones) {
            return locationsById.containsKey(from.getId())
                    && locationsById.containsKey(to.getId())
                    && (zones == null || this.zoneSignature.equals(zoneSignature(zones)));
        }

        private List<double[]> buildPath(int fromId, int toId) {
            if (fromId < 0 || toId < 0 || fromId >= next.length || toId >= next.length) {
                return List.of();
            }
            if (next[fromId][toId] == -1 || distances[fromId][toId] >= INF) {
                return List.of();
            }

            List<double[]> fullPath = new ArrayList<>();
            int current = fromId;
            while (current != toId) {
                int following = next[current][toId];
                if (following == -1) {
                    return List.of();
                }
                List<double[]> segment = edgePaths.getOrDefault(edgeKey(current, following), List.of());
                if (segment.isEmpty()) {
                    return List.of();
                }
                List<double[]> normalized = clonePath(segment);
                if (!fullPath.isEmpty() && !normalized.isEmpty()) {
                    normalized.remove(0);
                }
                fullPath.addAll(normalized);
                current = following;
            }
            return fullPath;
        }

        private List<List<double[]>> getAllEdgePaths() {
            List<List<double[]>> edges = new ArrayList<>();
            for (List<double[]> edgePath : edgePaths.values()) {
                if (!edgePath.isEmpty()) {
                    edges.add(clonePath(edgePath));
                }
            }
            return edges;
        }

        /**
         * Returns [fromLat, fromLng, toLat, toLng] for every direct edge stored
         * in the graph. Duplicate (bidirectional) entries are deduplicated by
         * keeping only edges where fromId < toId so the JS map draws each road
         * segment once.
         */
        private List<double[]> getAllEdgeEndpoints() {
            List<double[]> endpoints = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (Map.Entry<Long, List<double[]>> entry : edgePaths.entrySet()) {
                long key = entry.getKey();
                int from = (int) (key >> 32);
                int to   = (int) (key & 0xffffffffL);
                long canonical = from < to ? key : edgeKey(to, from);
                if (!seen.add(canonical)) continue;
                List<double[]> path = entry.getValue();
                if (path.size() < 2) continue;
                Location fLoc = locationsById.get(from);
                Location tLoc = locationsById.get(to);
                if (fLoc == null || tLoc == null) continue;
                endpoints.add(new double[]{fLoc.getLat(), fLoc.getLng(), tLoc.getLat(), tLoc.getLng()});
            }
            return endpoints;
        }
    }
}
