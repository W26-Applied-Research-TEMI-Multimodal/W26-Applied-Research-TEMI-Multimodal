package ca.mohawk.temirobotconcierge.poi;

public class Poi {
    public String id;
    public String displayName;
    public String description;

    public String buildingId;

    public String temiLocationName;

    public Poi() {}

    public boolean isBound() {
        return temiLocationName != null && !temiLocationName.trim().isEmpty();
    }
}