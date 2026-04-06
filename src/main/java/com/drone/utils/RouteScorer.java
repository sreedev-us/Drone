package com.drone.utils;

import com.drone.model.Location;
import com.drone.model.Route;

import java.util.List;

public final class RouteScorer {

    private static final double BATTERY_LIMIT_KM = 35.0;
    private static final int MAX_DELIVERIES_PER_DRONE = 5;
    private static final double CONSTRAINT_PENALTY = 1_000_000.0;
    private static final double PRIORITY_ARRIVAL_WEIGHT = 3.5;
    private static final double LATE_PENALTY_WEIGHT = 1000.0;
    private static final double BALANCE_WEIGHT = 30.0;

    private RouteScorer() {
    }

    public static double score(List<Route.DronePath> dronePaths, double totalDistance) {
        if (totalDistance >= DistanceCalculator.INF) {
            return DistanceCalculator.INF;
        }

        double weightedArrivalPenalty = 0.0;
        double weightedLatePenalty = 0.0;
        double batteryPenalty = 0.0;
        double capacityPenalty = 0.0;
        double maxDistance = 0.0;
        double minDistance = Double.MAX_VALUE;
        int activeDrones = 0;

        for (Route.DronePath dronePath : dronePaths) {
            if (dronePath == null || dronePath.getPath() == null || dronePath.getPath().size() <= 2) {
                continue;
            }

            activeDrones++;
            maxDistance = Math.max(maxDistance, dronePath.getDistance());
            minDistance = Math.min(minDistance, dronePath.getDistance());
            int deliveries = Math.max(0, dronePath.getPath().size() - 2);
            if (dronePath.getDistance() > BATTERY_LIMIT_KM) {
                batteryPenalty += CONSTRAINT_PENALTY + ((dronePath.getDistance() - BATTERY_LIMIT_KM) * 10_000.0);
            }
            if (deliveries > MAX_DELIVERIES_PER_DRONE) {
                capacityPenalty += (deliveries - MAX_DELIVERIES_PER_DRONE) * CONSTRAINT_PENALTY;
            }

            for (int i = 1; i < dronePath.getPath().size() - 1; i++) {
                Location stop = dronePath.getPath().get(i);
                double arrival = dronePath.getArrivalTimes().get(i);
                double priorityWeight = stop.getPriorityWeight();
                weightedArrivalPenalty += arrival * priorityWeight * PRIORITY_ARRIVAL_WEIGHT;
                if (arrival > stop.getDueTime()) {
                    weightedLatePenalty += (arrival - stop.getDueTime()) * priorityWeight * LATE_PENALTY_WEIGHT;
                }
            }
        }

        double imbalancePenalty = activeDrones <= 1 ? 0.0 : (maxDistance - minDistance) * BALANCE_WEIGHT;
        return totalDistance + weightedArrivalPenalty + weightedLatePenalty + batteryPenalty + capacityPenalty + imbalancePenalty;
    }

    public static double getBatteryLimitKm() {
        return BATTERY_LIMIT_KM;
    }

    public static int getMaxDeliveriesPerDrone() {
        return MAX_DELIVERIES_PER_DRONE;
    }
}
