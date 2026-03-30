package com.drone.algorithm;

import com.drone.model.Location;
import com.drone.model.Route;
import com.drone.utils.DistanceCalculator;
import com.drone.utils.RouteScorer;

import java.util.ArrayList;
import java.util.List;

public class BruteForce implements TSPAlgorithm {

    private double bestCost;
    private int[] bestPerm;
    private double[][] matrix;
    private List<Location> locations;
    private int fleetSize;
    private static final double SPEED_KM_MIN = 1.0;

    @Override
    public String getName() { return "Brute Force (Optimal Fleet)"; }

    @Override
    public Route solve(double[][] matrix, List<Location> locations, int droneCount) {
        long start = System.currentTimeMillis();
        this.matrix = matrix;
        this.locations = locations;
        this.fleetSize = droneCount;
        int n = locations.size();

        if (n <= 1) return new Route(new ArrayList<>(), 0, 0, getName());

        int[] indices = new int[n - 1];
        for (int i = 0; i < n - 1; i++) indices[i] = i + 1;

        bestCost = DistanceCalculator.INF;
        bestPerm = indices.clone();

        permute(indices, 0);

        Route result = decode(bestPerm);
        long elapsed = System.currentTimeMillis() - start;
        return new Route(result.getDronePaths(), result.getTotalDistance(), result.getObjectiveScore(), elapsed, getName());
    }

    private void permute(int[] arr, int k) {
        if (k == arr.length) {
            Route r = decode(arr);
            double cost = r.getObjectiveScore();
            if (cost < bestCost) {
                bestCost = cost;
                bestPerm = arr.clone();
            }
            return;
        }
        for (int i = k; i < arr.length; i++) {
            swap(arr, i, k);
            permute(arr, k + 1);
            swap(arr, i, k);
        }
    }

    private Route decode(int[] chrom) {
        List<Route.DronePath> paths = new ArrayList<>();
        double totalDist = 0;
        int n = chrom.length;
        int baseChunkSize = (int) Math.ceil((double) n / fleetSize);

        int chromIdx = 0;
        for (int d = 0; d < fleetSize; d++) {
            if (chromIdx >= n) break;

            List<Location> p = new ArrayList<>();
            List<Double> times = new ArrayList<>();
            double dDist = 0;
            double curTime = 0;

            p.add(locations.get(0));
            times.add(0.0);
            int current = 0;

            int count = 0;
            while (count < baseChunkSize && chromIdx < n) {
                int next = chrom[chromIdx++];
                double step = matrix[current][next];
                if (step >= DistanceCalculator.INF) {
                    totalDist = DistanceCalculator.INF;
                    break;
                }
                dDist += step;
                curTime += (step / SPEED_KM_MIN);
                curTime = Math.max(curTime, locations.get(next).getReadyTime());
                p.add(locations.get(next));
                times.add(curTime);
                current = next;
                count++;
            }

            // Skip drones with no deliveries
            if (p.size() <= 1) continue;

            double back = matrix[current][0];
            if (back >= DistanceCalculator.INF) totalDist = DistanceCalculator.INF;
            else {
                dDist += back;
                curTime += (back / SPEED_KM_MIN);
                p.add(locations.get(0));
                times.add(curTime);
            }

            paths.add(new Route.DronePath(d, p, dDist, times));
            totalDist += dDist;
        }
        double objectiveScore = RouteScorer.score(paths, totalDist);
        return new Route(paths, totalDist, objectiveScore, 0, getName());
    }

    private void swap(int[] arr, int i, int j) {
        int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
    }
}
