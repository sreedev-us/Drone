package com.drone.model;

import com.drone.utils.DistanceCalculator;
import com.drone.utils.RouteScorer;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Route model for Vehicle Routing Problem (VRP).
 * Holds multiple paths for multiple drones and checks for time-window violations.
 */
public class Route {
    private final List<DronePath> dronePaths;
    private final double totalDistance;
    private final double objectiveScore;
    private final long executionTimeMs;
    private final String algorithmName;
    private final boolean timeWindowsMet;

    public static class DronePath {
        private final int droneId;
        private final List<Location> path;
        private final double distance;
        private final List<Double> arrivalTimes;

        public DronePath(int droneId, List<Location> path, double distance, List<Double> arrivalTimes) {
            this.droneId = droneId;
            this.path = path;
            this.distance = distance;
            this.arrivalTimes = arrivalTimes;
        }

        public int getDroneId() { return droneId; }
        public List<Location> getPath() { return path; }
        public double getDistance() { return distance; }
        public List<Double> getArrivalTimes() { return arrivalTimes; }
    }

    public Route(List<DronePath> dronePaths, double totalDistance, long executionTimeMs, String algorithmName) {
        this(dronePaths, totalDistance, totalDistance, executionTimeMs, algorithmName);
    }

    public Route(List<DronePath> dronePaths, double totalDistance, double objectiveScore, long executionTimeMs, String algorithmName) {
        this.dronePaths = dronePaths == null ? new ArrayList<>() : dronePaths;
        this.totalDistance = totalDistance;
        this.objectiveScore = objectiveScore;
        this.executionTimeMs = executionTimeMs;
        this.algorithmName = algorithmName;

        boolean met = true;
        for (DronePath dp : this.dronePaths) {
            for (int i = 0; i < dp.path.size(); i++) {
                if (dp.arrivalTimes.get(i) > dp.path.get(i).getDueTime()) {
                    met = false;
                    break;
                }
            }
        }
        this.timeWindowsMet = met;
    }

    public List<DronePath> getDronePaths() { return dronePaths; }
    public double getTotalDistance() { return totalDistance; }
    public double getObjectiveScore() { return objectiveScore; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getAlgorithmName() { return algorithmName; }
    public boolean isTimeWindowsMet() { return timeWindowsMet; }

    public String getRouteString() {
        StringBuilder sb = new StringBuilder();
        if (!timeWindowsMet) {
            sb.append("Deadline missed on at least one stop.\n\n");
        }
        sb.append("Priority-Aware Score: ").append(String.format("%.2f", objectiveScore)).append("\n\n");

        for (DronePath dp : dronePaths) {
            sb.append("Drone ").append(dp.droneId + 1)
                    .append(" | Total Distance: ")
                    .append(String.format("%.2f km", dp.distance))
                    .append(" | Deliveries: ")
                    .append(Math.max(0, dp.path.size() - 2))
                    .append("\n");
            if (dp.distance > RouteScorer.getBatteryLimitKm()) {
                sb.append("    Battery warning: exceeds ")
                        .append(String.format("%.1f km", RouteScorer.getBatteryLimitKm()))
                        .append("\n");
            }
            if (Math.max(0, dp.path.size() - 2) > RouteScorer.getMaxDeliveriesPerDrone()) {
                sb.append("    Capacity warning: exceeds ")
                        .append(RouteScorer.getMaxDeliveriesPerDrone())
                        .append(" deliveries\n");
            }

            for (int i = 0; i < dp.path.size(); i++) {
                Location current = dp.path.get(i);
                double arrival = i < dp.arrivalTimes.size() ? dp.arrivalTimes.get(i) : 0.0;
                sb.append("  ").append(i + 1).append(". ")
                        .append(current.getName())
                        .append(" [").append(current.getPriority().getLabel()).append("]")
                        .append(" | ETA ")
                        .append(String.format("%.0f min", arrival));

                if (i < dp.path.size() - 1) {
                    Location next = dp.path.get(i + 1);
                    double edgeDistance = DistanceCalculator.pathDistance(DistanceCalculator.buildPath(current, next, null));
                    sb.append(" | Edge to next: ")
                            .append(String.format("%.2f km", edgeDistance));
                }
                sb.append("\n");
            }

            sb.append("  Path: ");
            for (int i = 0; i < dp.path.size(); i++) {
                if (i > 0) {
                    sb.append(" -> ");
                }
                sb.append(dp.path.get(i).getName());
            }
            sb.append("\n\n");
        }

        return sb.toString();
    }

    public List<Location> getPath() {
        return dronePaths.isEmpty() ? new ArrayList<>() : dronePaths.get(0).getPath();
    }
}
