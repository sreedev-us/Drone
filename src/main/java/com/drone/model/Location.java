package com.drone.model;

import java.util.Objects;

/**
 * Enhanced Location model supporting real-world Lat/Lng and VRP Time Windows.
 */
public class Location {
    public enum Priority {
        LOW(0.70, "Low"),
        NORMAL(1.00, "Normal"),
        HIGH(1.60, "High"),
        CRITICAL(2.40, "Critical");

        private final double weight;
        private final String label;

        Priority(double weight, String label) {
            this.weight = weight;
            this.label = label;
        }

        public double getWeight() {
            return weight;
        }

        public String getLabel() {
            return label;
        }

        public static Priority fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return NORMAL;
            }
            for (Priority priority : values()) {
                if (priority.name().equalsIgnoreCase(raw) || priority.label.equalsIgnoreCase(raw)) {
                    return priority;
                }
            }
            return NORMAL;
        }
    }

    private final int id;
    private final String name;
    
    // Pixel/Relative coordinates (for legacy zoom/pan calculations)
    private double x;
    private double y;

    // Real-world coordinates
    private double lat;
    private double lng;

    // Time Windows (in minutes from simulation start)
    private double readyTime = 0.0;
    private double dueTime   = 1440.0; // Default to 24 hours
    private Priority priority = Priority.NORMAL;

    public Location(int id, String name, double x, double y) {
        this(id, name, x, y, Priority.NORMAL);
    }

    public Location(int id, String name, double x, double y, Priority priority) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.priority = priority == null ? Priority.NORMAL : priority;
    }

    // New Constructor for Lat/Lng
    public Location(int id, String name, double lat, double lng, double readyTime, double dueTime) {
        this(id, name, lat, lng, readyTime, dueTime, Priority.NORMAL);
    }

    public Location(int id, String name, double lat, double lng, double readyTime, double dueTime, Priority priority) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.priority = priority == null ? Priority.NORMAL : priority;
        // Mock X/Y based on Lat/Lng for pixel-ui consistency if needed
        this.x = (lng + 180) * 1000;
        this.y = (90 - lat) * 1000;
    }

    public int getId()      { return id; }
    public String getName() { return name; }
    public double getX()    { return x; }
    public double getY()    { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public double getReadyTime() { return readyTime; }
    public void setReadyTime(double readyTime) { this.readyTime = readyTime; }

    public double getDueTime() { return dueTime; }
    public void setDueTime(double dueTime) { this.dueTime = dueTime; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority == null ? Priority.NORMAL : priority; }
    public double getPriorityWeight() { return priority.getWeight(); }

    @Override public String toString() { return name; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location l)) return false;
        return id == l.id;
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
