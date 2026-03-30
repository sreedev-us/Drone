package com.drone.model;

/**
 * No-fly zone defined as a rectangle in geographic coordinates (longitude, latitude).
 * Uses a flat-earth approximation for line-segment intersection, sufficient for small zones.
 */
public class NoFlyZone {
    private final double minLng, maxLng, minLat, maxLat;

    /**
     * Constructs a no-fly zone from two opposite corners.
     * @param lng1 longitude of first corner
     * @param lat1 latitude of first corner
     * @param lng2 longitude of opposite corner
     * @param lat2 latitude of opposite corner
     */
    public NoFlyZone(double lng1, double lat1, double lng2, double lat2) {
        this.minLng = Math.min(lng1, lng2);
        this.maxLng = Math.max(lng1, lng2);
        this.minLat = Math.min(lat1, lat2);
        this.maxLat = Math.max(lat1, lat2);
    }

    public double getMinLng() { return minLng; }
    public double getMaxLng() { return maxLng; }
    public double getMinLat() { return minLat; }
    public double getMaxLat() { return maxLat; }

    /**
     * Returns true if the line segment (lng1,lat1) → (lng2,lat2) passes through or touches this zone.
     */
    public boolean intersectsPath(double lng1, double lat1, double lng2, double lat2) {
        if (contains(lng1, lat1) || contains(lng2, lat2)) return true;
        // Check segment against all four edges of the rectangle
        return segmentsIntersect(lng1, lat1, lng2, lat2, minLng, minLat, maxLng, minLat) ||
                segmentsIntersect(lng1, lat1, lng2, lat2, maxLng, minLat, maxLng, maxLat) ||
                segmentsIntersect(lng1, lat1, lng2, lat2, minLng, maxLat, maxLng, maxLat) ||
                segmentsIntersect(lng1, lat1, lng2, lat2, minLng, minLat, minLng, maxLat);
    }

    private boolean contains(double lng, double lat) {
        return lng >= minLng && lng <= maxLng && lat >= minLat && lat <= maxLat;
    }

    /**
     * Checks if two line segments intersect, including collinear overlap.
     */
    private boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                      double cx, double cy, double dx, double dy) {
        double d1x = bx - ax, d1y = by - ay;
        double d2x = dx - cx, d2y = dy - cy;
        double cross = d1x * d2y - d1y * d2x;
        if (Math.abs(cross) < 1e-10) {
            // Collinear – check if segments overlap
            return collinearOverlap(ax, ay, bx, by, cx, cy, dx, dy);
        }
        double t = ((cx - ax) * d2y - (cy - ay) * d2x) / cross;
        double u = ((cx - ax) * d1y - (cy - ay) * d1x) / cross;
        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }

    /**
     * Returns true if two collinear segments overlap (including touching at endpoints).
     */
    private boolean collinearOverlap(double ax, double ay, double bx, double by,
                                     double cx, double cy, double dx, double dy) {
        // Project onto the line's dominant axis
        double t1 = 0, t2 = 1;
        double t3, t4;

        if (Math.abs(bx - ax) > Math.abs(by - ay)) {
            t1 = ax;
            t2 = bx;
            t3 = cx;
            t4 = dx;
        } else {
            t1 = ay;
            t2 = by;
            t3 = cy;
            t4 = dy;
        }

        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
        if (t3 > t4) { double tmp = t3; t3 = t4; t4 = tmp; }

        return Math.max(t1, t3) <= Math.min(t2, t4);
    }
}