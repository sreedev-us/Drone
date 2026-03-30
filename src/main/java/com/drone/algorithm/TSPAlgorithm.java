package com.drone.algorithm;

import com.drone.model.Location;
import com.drone.model.Route;
import java.util.List;

/**
 * Universal interface for fleet-aware optimization algorithms.
 */
public interface TSPAlgorithm {
    /**
     * Solves the Vehicle Routing Problem (VRP) for a given fleet size.
     * @param matrix   Distance matrix (distance[i][j])
     * @param locations List of Location objects (model-level metadata)
     * @param droneCount Number of drones available (fleet size)
     */
    Route solve(double[][] matrix, List<Location> locations, int droneCount);
    
    String getName();
}
