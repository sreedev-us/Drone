# Drone Delivery Algorithms Component Analysis

This document provides a concise time and space complexity evaluation for the core components powering the `DroneDeliveryApp` Multi-Vehicle Dispatch optimization engine.

## 1. Brute Force Optimizer
The Brute Force technique explicitly constructs and searches through every possible nodal assignment sequence across the given set of coordinates. It evaluates every route combination, assigning each drone sequentially to the permutations.

- **Best Case Time Complexity**: $O(N!)$ — Irrespective of input, it mathematically yields every permutation across total nodes $N$.
- **Average Case Time Complexity**: $O(N!)$
- **Worst Case Time Complexity**: $O(N!)$
- **Space Complexity**: $O(N)$ — Holds recursive stack references per permutation depth alongside array-copy elements.

## 2. Greedy Fleet Dispatch (Nearest-Neighbor) 
A Multi-Vehicle adaptation where clusters are partitioned based conditionally on depot proximity, angle, and predefined centroid bounds. Post-clustering, each assigned agent iterates and connects the most optimally advantageous step iteratively based on localized distance logic and delivery window.

- **Best Case Time Complexity**: $O(N^2)$ — Even in optimal linear spatial sets, the nearest candidate is continuously evaluated iteratively against all remaining subsets.
- **Average Case Time Complexity**: $O(D \cdot N^2)$ or effectively $O(N^2)$ — Where $D$ stands for the fleet threshold or subset length multiplier mapping onto subset search iterations.
- **Worst Case Time Complexity**: $O(N^2)$
- **Space Complexity**: $O(N)$ — To allocate location sublists, balanced cluster definitions, and tracking arrays.

## 3. Genetic Fleet Optimizer
An evolutionary algorithm variant structured dynamically using chromosome representations matching routing node pointers and fleet path-splitting arrays. Fitness correlates directly inversely vs travel distance parameters spanning up to predefined upper bounding thresholds (600 Max Generations across 200 Population Size).

- **Best Case Time Complexity**: $O(G \times P \times N)$ — Due to strict adherence to fixed-termination criteria ($G$ = 600) rather than a conditional stagnation break factor.
- **Average Case Time Complexity**: $O(G \times P \times N)$
- **Worst Case Time Complexity**: $O(G \times P \times N)$
- **Space Complexity**: $O(P \times N)$ — It actively populates arrays preserving crossover elements matching overall parent-child subsets concurrently.

> **Conclusion**: The Genetic Approach is scalable to heavy operational scaling, scaling linearly in proportion to inputs given static generation boundaries, outmatching factorial-grade complexity algorithms immediately when coordinate metrics widen. Greedy algorithms present extremely efficient baseline operational models for lower subsets but might occasionally fail convergence on exact-optimal paths found sequentially in Evolutionary generation runs.
