package ca.mohawk.temirobotconcierge.poi;

/**
 * Represents a campus location with tour guide information
 * 
 * Fields are populated from locations.json asset file
 */
public class Location {
    public String temiLocationName;      // Name used by TEMI robot 
    public String displayName;            // UI Friendly Name
    public String description;            // Descriptor for prompt generation
    public String wing;                   // Which wing/area of campus 
    
    public Location(String temiLocationName, String displayName, String description, String wing) {
        this.temiLocationName = temiLocationName;
        this.displayName = displayName;
        this.description = description;
        this.wing = wing;
    }
    
    public Location() {
    }
    
    @Override
    public String toString() {
        return "Location{" +
                "displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", wing='" + wing + '\'' +
                '}';
    }
}
