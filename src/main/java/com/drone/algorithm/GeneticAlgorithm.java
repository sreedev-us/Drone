package com.drone.algorithm;

import com.drone.model.Location;
import com.drone.model.Route;
import com.drone.utils.DistanceCalculator;
import com.drone.utils.RouteScorer;

import java.util.*;

public class GeneticAlgorithm implements TSPAlgorithm {

    private static final int POP_SIZE          = 200;
    private static final int MAX_GENERATIONS   = 600;
    private static final double MUTATION_RATE  = 0.07;
    private static final int ELITISM_COUNT     = 10;
    private static final double SPEED_KM_MIN   = 1.0;

    private double[][] matrix;
    private List<Location> locations;
    private int fleetSize;
    private final Random rng = new Random();

    @Override
    public String getName() { return "Genetic Fleet Optimizer (VRP)"; }

    @Override
    public Route solve(double[][] matrix, List<Location> locations, int droneCount) {
        long startTime = System.currentTimeMillis();
        this.matrix = matrix;
        this.locations = locations;
        this.fleetSize = droneCount;
        int geneLength = locations.size() - 1;

        if (geneLength <= 0) return new Route(new ArrayList<>(), 0, 0, getName());

        List<Chromosome> pop = initPopulation(geneLength);
        Chromosome bestChrom = null;
        double bestFitness = -1.0;

        for (int gen = 0; gen < MAX_GENERATIONS; gen++) {
            double[] fitness = new double[pop.size()];
            for (int i = 0; i < pop.size(); i++) {
                fitness[i] = calculateFitness(pop.get(i));
                if (fitness[i] > bestFitness) {
                    bestFitness = fitness[i];
                    bestChrom = pop.get(i).copy();
                }
            }

            List<Chromosome> nextPop = new ArrayList<>();
            List<Integer> sorted = getSortedIndices(fitness);
            for (int i = 0; i < ELITISM_COUNT; i++) nextPop.add(pop.get(sorted.get(i)));

            while (nextPop.size() < POP_SIZE) {
                Chromosome p1 = tournamentSelect(pop, fitness);
                Chromosome p2 = tournamentSelect(pop, fitness);
                Chromosome child = crossover(p1, p2);
                if (rng.nextDouble() < MUTATION_RATE) mutate(child);
                nextPop.add(child);
            }
            pop = nextPop;
        }

        Route result = decode(bestChrom);
        return new Route(result.getDronePaths(), result.getTotalDistance(), result.getObjectiveScore(),
                System.currentTimeMillis() - startTime, getName());
    }

    private double calculateFitness(Chromosome chrom) {
        Route r = decode(chrom);
        double score = r.getObjectiveScore();
        if (score >= DistanceCalculator.INF) return 0.0;
        return 1.0 / (score + 1e-6);
    }

    private Route decode(Chromosome chrom) {
        List<Route.DronePath> paths = new ArrayList<>();
        double totalDist = 0;
        int chromIdx = 0;
        int[] routeEnds = chrom.routeEnds();

        for (int d = 0; d < fleetSize; d++) {
            int routeEnd = d < routeEnds.length ? routeEnds[d] : chrom.order().length;
            if (chromIdx >= routeEnd) {
                continue;
            }

            List<Location> p = new ArrayList<>();
            List<Double> times = new ArrayList<>();
            double dDist = 0;
            double curTime = 0;

            p.add(locations.get(0));
            times.add(0.0);
            int current = 0;

            while (chromIdx < routeEnd) {
                int next = chrom.order()[chromIdx++];
                double step = matrix[current][next];
                if (step >= DistanceCalculator.INF) {
                    return new Route(new ArrayList<>(), DistanceCalculator.INF, DistanceCalculator.INF, 0, getName());
                }
                dDist += step;
                curTime += (step / SPEED_KM_MIN);
                curTime = Math.max(curTime, locations.get(next).getReadyTime());
                p.add(locations.get(next));
                times.add(curTime);
                current = next;
            }

            if (p.size() <= 1) continue;

            double back = matrix[current][0];
            if (back >= DistanceCalculator.INF) {
                return new Route(new ArrayList<>(), DistanceCalculator.INF, DistanceCalculator.INF, 0, getName());
            }
            dDist += back;
            curTime += (back / SPEED_KM_MIN);
            p.add(locations.get(0));
            times.add(curTime);

            paths.add(new Route.DronePath(d, p, dDist, times));
            totalDist += dDist;
        }
        double objectiveScore = RouteScorer.score(paths, totalDist);
        return new Route(paths, totalDist, objectiveScore, 0, getName());
    }

    private List<Chromosome> initPopulation(int len) {
        List<Chromosome> pop = new ArrayList<>();
        int[] base = new int[len];
        for (int i = 0; i < len; i++) base[i] = i + 1;
        for (int i = 0; i < POP_SIZE; i++) {
            int[] order = base.clone();
            for (int j = 0; j < len; j++) {
                int r = rng.nextInt(len);
                int tmp = order[j]; order[j] = order[r]; order[r] = tmp;
            }
            pop.add(new Chromosome(order, randomRouteEnds(len)));
        }
        return pop;
    }

    private List<Integer> getSortedIndices(double[] f) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < f.length; i++) idx.add(i);
        idx.sort((a, b) -> Double.compare(f[b], f[a]));
        return idx;
    }

    private Chromosome tournamentSelect(List<Chromosome> pop, double[] fitness) {
        int bIdx = rng.nextInt(pop.size());
        for (int i = 0; i < 5; i++) {
            int r = rng.nextInt(pop.size());
            if (fitness[r] > fitness[bIdx]) bIdx = r;
        }
        return pop.get(bIdx).copy();
    }

    private Chromosome crossover(Chromosome p1, Chromosome p2) {
        int n = p1.order().length;
        int[] childOrder = new int[n];
        int[] childEnds = new int[Math.max(fleetSize - 1, 0)];
        Arrays.fill(childOrder, -1);
        int start = rng.nextInt(n), end = rng.nextInt(n);
        if (start > end) { int t = start; start = end; end = t; }
        for (int i = start; i <= end; i++) childOrder[i] = p1.order()[i];
        int cIdx = 0;
        for (int i = 0; i < n; i++) {
            if (childOrder[i] == -1) {
                while (contains(childOrder, p2.order()[cIdx])) cIdx++;
                childOrder[i] = p2.order()[cIdx++];
            }
        }

        for (int i = 0; i < childEnds.length; i++) {
            int left = p1.routeEnds()[i];
            int right = p2.routeEnds()[i];
            childEnds[i] = Math.max(0, Math.min(n, (left + right) / 2 + rng.nextInt(3) - 1));
        }
        normalizeRouteEnds(childEnds, n);
        return new Chromosome(childOrder, childEnds);
    }

    private boolean contains(int[] arr, int val) {
        for (int x : arr) if (x == val) return true;
        return false;
    }

    private void mutate(Chromosome chromosome) {
        int[] order = chromosome.order();
        int i = rng.nextInt(order.length);
        int j = rng.nextInt(order.length);
        int tmp = order[i];
        order[i] = order[j];
        order[j] = tmp;

        if (chromosome.routeEnds().length > 0) {
            int splitIndex = rng.nextInt(chromosome.routeEnds().length);
            chromosome.routeEnds()[splitIndex] += rng.nextBoolean() ? 1 : -1;
            normalizeRouteEnds(chromosome.routeEnds(), order.length);
        }
    }

    private int[] randomRouteEnds(int len) {
        int[] routeEnds = new int[Math.max(fleetSize - 1, 0)];
        if (routeEnds.length == 0) {
            return routeEnds;
        }

        List<Integer> cutPoints = new ArrayList<>();
        for (int i = 1; i < len; i++) {
            cutPoints.add(i);
        }
        Collections.shuffle(cutPoints, rng);
        for (int i = 0; i < routeEnds.length; i++) {
            routeEnds[i] = i < cutPoints.size() ? cutPoints.get(i) : len;
        }
        normalizeRouteEnds(routeEnds, len);
        return routeEnds;
    }

    private void normalizeRouteEnds(int[] routeEnds, int maxLen) {
        Arrays.sort(routeEnds);
        int previous = 0;
        for (int i = 0; i < routeEnds.length; i++) {
            int minAllowed = previous;
            int maxAllowed = maxLen - (routeEnds.length - i);
            routeEnds[i] = Math.max(minAllowed, Math.min(maxAllowed, routeEnds[i]));
            previous = routeEnds[i];
        }
    }

    private record Chromosome(int[] order, int[] routeEnds) {
        private Chromosome copy() {
            return new Chromosome(order.clone(), routeEnds.clone());
        }
    }
}
