package com.drone.algorithm;

import com.drone.model.Location;
import com.drone.model.Route;
import com.drone.utils.DistanceCalculator;
import com.drone.utils.RouteScorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Multi-Vehicle Greedy Nearest-Neighbor VRP solver.
 * Each drone is dispatched sequentially to visit the nearest unvisited nodes.
 */
public class Greedy implements TSPAlgorithm {

    private static final double DRONE_SPEED_KM_MIN = 1.0; // 60 km/h

    @Override
    public String getName() { return "Greedy Fleet Dispatch (VRP)"; }

    @Override
    public Route solve(double[][] matrix, List<Location> locations, int droneCount) {
        long startTimeMs = System.currentTimeMillis();
        int n = locations.size();
        if (n <= 1) return new Route(new ArrayList<>(), 0, 0, getName());

        int clusterCount = Math.min(Math.max(droneCount, 1), n - 1);
        List<List<Integer>> clusters = buildBalancedClusters(matrix, locations, clusterCount);
        List<Route.DronePath> dronePaths = new ArrayList<>();
        double totalFleetDistance = 0;

        for (int d = 0; d < droneCount; d++) {
            if (d >= clusters.size() || clusters.get(d).isEmpty()) {
                continue;
            }

            List<Location> path = new ArrayList<>();
            List<Double> arrivalTimes = new ArrayList<>();
            double droneDist = 0;
            double currentTime = 0;
            int currentIdx = 0; // Start at Base
            path.add(locations.get(0));
            arrivalTimes.add(0.0);

            List<Integer> remaining = new ArrayList<>(clusters.get(d));
            while (!remaining.isEmpty()) {
                int nextNode = pickBestNextNode(matrix, locations, currentIdx, currentTime, remaining);
                double step = matrix[currentIdx][nextNode];
                if (step >= DistanceCalculator.INF) {
                    return new Route(new ArrayList<>(), DistanceCalculator.INF,
                            System.currentTimeMillis() - startTimeMs, getName());
                }

                droneDist += step;
                currentTime += (step / DRONE_SPEED_KM_MIN);
                currentTime = Math.max(currentTime, locations.get(nextNode).getReadyTime());

                path.add(locations.get(nextNode));
                arrivalTimes.add(currentTime);
                currentIdx = nextNode;
                remaining.remove(Integer.valueOf(nextNode));
            }

            double backToBase = matrix[currentIdx][0];
            if (backToBase >= DistanceCalculator.INF) {
                return new Route(new ArrayList<>(), DistanceCalculator.INF,
                        System.currentTimeMillis() - startTimeMs, getName());
            }
            droneDist += backToBase;
            currentTime += (backToBase / DRONE_SPEED_KM_MIN);
            path.add(locations.get(0));
            arrivalTimes.add(currentTime);

            dronePaths.add(new Route.DronePath(d, path, droneDist, arrivalTimes));
            totalFleetDistance += droneDist;
        }

        double objectiveScore = RouteScorer.score(dronePaths, totalFleetDistance);
        return new Route(dronePaths, totalFleetDistance, objectiveScore,
                System.currentTimeMillis() - startTimeMs, getName());
    }

    private List<List<Integer>> buildBalancedClusters(double[][] matrix, List<Location> locations, int clusterCount) {
        List<Integer> deliveryNodes = new ArrayList<>();
        for (int i = 1; i < locations.size(); i++) {
            deliveryNodes.add(i);
        }

        deliveryNodes.sort(Comparator.comparingDouble((Integer idx) -> angleFromDepot(locations.get(idx), locations.get(0))).thenComparingInt(Integer::intValue));

        List<double[]> centroids = new ArrayList<>();
        for (int i = 0; i < clusterCount; i++) {
            int seedIndex = deliveryNodes.get((int) Math.floor((double) i * deliveryNodes.size() / clusterCount));
            centroids.add(pointOf(locations.get(seedIndex)));
        }

        List<List<Integer>> assignments = new ArrayList<>();
        for (int i = 0; i < clusterCount; i++) {
            assignments.add(new ArrayList<>());
        }

        int baseCapacity = deliveryNodes.size() / clusterCount;
        int remainder = deliveryNodes.size() % clusterCount;

        for (int iter = 0; iter < 8; iter++) {
            assignments.forEach(List::clear);
            int[] capacities = new int[clusterCount];
            for (int i = 0; i < clusterCount; i++) {
                capacities[i] = baseCapacity + (i < remainder ? 1 : 0);
            }

            List<Integer> orderedNodes = new ArrayList<>(deliveryNodes);
            orderedNodes.sort(Comparator
                    .comparingDouble((Integer idx) -> -locations.get(idx).getPriorityWeight())
                    .thenComparingDouble(idx -> -matrix[0][idx]));

            for (int nodeIdx : orderedNodes) {
                int bestCluster = -1;
                double bestScore = Double.MAX_VALUE;
                for (int cluster = 0; cluster < clusterCount; cluster++) {
                    if (assignments.get(cluster).size() >= capacities[cluster]) {
                        continue;
                    }
                    double score = distanceToCentroid(locations.get(nodeIdx), centroids.get(cluster));
                    if (score < bestScore) {
                        bestScore = score;
                        bestCluster = cluster;
                    }
                }
                if (bestCluster >= 0) {
                    assignments.get(bestCluster).add(nodeIdx);
                }
            }

            for (int cluster = 0; cluster < clusterCount; cluster++) {
                if (!assignments.get(cluster).isEmpty()) {
                    centroids.set(cluster, centroidOf(assignments.get(cluster), locations));
                }
            }
        }

        return assignments;
    }

    private int pickBestNextNode(double[][] matrix, List<Location> locations, int currentIdx, double currentTime, List<Integer> candidates) {
        int bestNode = -1;
        double bestScore = Double.MAX_VALUE;

        for (int candidate : candidates) {
            double step = matrix[currentIdx][candidate];
            if (step >= DistanceCalculator.INF) {
                continue;
            }
            double arrival = Math.max(currentTime + (step / DRONE_SPEED_KM_MIN), locations.get(candidate).getReadyTime());
            double lateness = Math.max(0.0, arrival - locations.get(candidate).getDueTime());
            double score = (step / locations.get(candidate).getPriorityWeight()) + (lateness * 2000.0);
            if (score < bestScore) {
                bestScore = score;
                bestNode = candidate;
            }
        }
        return bestNode;
    }

    private double angleFromDepot(Location location, Location depot) {
        return Math.atan2(location.getLat() - depot.getLat(), location.getLng() - depot.getLng());
    }

    private double distanceToCentroid(Location location, double[] centroid) {
        return DistanceCalculator.haversine(location.getLat(), location.getLng(), centroid[0], centroid[1]);
    }

    private double[] centroidOf(List<Integer> cluster, List<Location> locations) {
        double latSum = 0.0;
        double lngSum = 0.0;
        double weightSum = 0.0;
        for (int idx : cluster) {
            Location location = locations.get(idx);
            double weight = location.getPriorityWeight();
            latSum += location.getLat() * weight;
            lngSum += location.getLng() * weight;
            weightSum += weight;
        }
        return new double[]{latSum / weightSum, lngSum / weightSum};
    }

    private double[] pointOf(Location location) {
        return new double[]{location.getLat(), location.getLng()};
    }
}
