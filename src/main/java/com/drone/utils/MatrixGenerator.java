package com.drone.utils;

import com.drone.model.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates stable land-based delivery points around Manhattan so routes stay on roads.
 */
public class MatrixGenerator {

    private static final double BASE_LAT = 40.7128;
    private static final double BASE_LNG = -74.0060;
    private static final double JITTER_LAT = 0.0028;
    private static final double JITTER_LNG = 0.0032;

    private static final double[][] LAND_ANCHORS = {
            {40.7075, -74.0113}, // Battery Park / FiDi
            {40.7116, -74.0086}, // Wall Street
            {40.7158, -74.0036}, // Tribeca
            {40.7198, -73.9992}, // Soho
            {40.7238, -73.9951}, // Nolita
            {40.7295, -73.9886}, // East Village
            {40.7358, -73.9911}, // Flatiron
            {40.7412, -73.9897}, // Midtown South
            {40.7488, -73.9857}, // Midtown
            {40.7549, -73.9840}, // Bryant Park
            {40.7615, -73.9777}, // Midtown East
            {40.7681, -73.9819}, // Columbus Circle
            {40.7736, -73.9590}, // Upper East
            {40.7794, -73.9852}, // Upper West
            {40.7323, -74.0007}, // Greenwich Village
            {40.7265, -73.9815}, // Alphabet City
            {40.7440, -73.9725}, // Murray Hill
            {40.7519, -73.9714}, // Grand Central
            {40.7580, -73.9693}, // Sutton Place
            {40.7061, -73.9969}, // DUMBO
            {40.7122, -73.9874}, // Lower East Side
            {40.7211, -73.9571}, // Williamsburg
            {40.7421, -73.9545}, // Long Island City
            {40.7307, -73.9352}  // Queens west
    };

    private static final Random RNG = new Random();

    public static List<Location> generateLocations(int count) {
        List<Location> locations = new ArrayList<>();
        locations.add(new Location(0, "Base (Depot)", BASE_LAT, BASE_LNG, 0.0, 1440.0, Location.Priority.NORMAL));

        for (int i = 1; i < count; i++) {
            double[] anchor = LAND_ANCHORS[(i - 1) % LAND_ANCHORS.length];
            double lat = anchor[0] + (RNG.nextDouble() - 0.5) * JITTER_LAT;
            double lng = anchor[1] + (RNG.nextDouble() - 0.5) * JITTER_LNG;
            double ready = 0.0;
            double due = 20 + RNG.nextInt(100);
            Location.Priority priority = randomPriority();
            if (priority == Location.Priority.CRITICAL) {
                due = Math.min(due, 45 + RNG.nextInt(25));
            } else if (priority == Location.Priority.HIGH) {
                due = Math.min(due, 70 + RNG.nextInt(30));
            }
            locations.add(new Location(i, "Order #" + (1000 + i), lat, lng, ready, due, priority));
        }

        return locations;
    }

    public static void setSeed(long seed) {
        RNG.setSeed(seed);
    }

    private static Location.Priority randomPriority() {
        double draw = RNG.nextDouble();
        if (draw < 0.15) {
            return Location.Priority.CRITICAL;
        }
        if (draw < 0.40) {
            return Location.Priority.HIGH;
        }
        if (draw < 0.80) {
            return Location.Priority.NORMAL;
        }
        return Location.Priority.LOW;
    }
}
