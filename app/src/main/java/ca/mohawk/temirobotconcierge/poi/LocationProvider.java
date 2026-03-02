package ca.mohawk.temirobotconcierge.poi;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and provides Location data from locations.json asset file
 * 
 * Creates a HashMap of all locations, keyed by temiLocationName
 * Use: Location loc = provider.getLocation("engineering_wing_entrance")
 */
public class LocationProvider {
    private static final String TAG = "LocationProvider";
    private static final String LOCATIONS_FILE = "locations.json";
    
    private Map<String, Location> locationMap;
    
    /**
     * Initialize LocationProvider and load locations.json
     */
    public LocationProvider(Context context) {
        locationMap = new HashMap<>();
        loadLocationsFromJson(context);
    }
    
    /**
     * Load all locations from locations.json in assets folder
     */
    private void loadLocationsFromJson(Context context) {
        try {
            // Read JSON file from assets
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(LOCATIONS_FILE))
            );
            
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();
            
            Log.d(TAG, "Loaded JSON file");
            
            // Parse JSON
            JSONObject jsonObject = new JSONObject(jsonString.toString());
            JSONArray locationsArray = jsonObject.getJSONArray("locations");
            
            // Create Location objects for each entry
            for (int i = 0; i < locationsArray.length(); i++) {
                JSONObject locationObj = locationsArray.getJSONObject(i);
                
                String temiLocationName = locationObj.getString("temiLocationName");
                String displayName = locationObj.getString("displayName");
                String description = locationObj.getString("description");
                String wing = locationObj.getString("wing");
                
                // Create Location object
                Location location = new Location(temiLocationName, displayName, description, wing);
                
                // Store in map with temiLocationName as key
                locationMap.put(temiLocationName, location);
                
                Log.d(TAG, "Loaded location: " + displayName + " (" + temiLocationName + ")");
            }
            
            Log.d(TAG, "Successfully loaded " + locationMap.size() + " locations");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading locations from JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get a Location by its TEMI location name
     * 
     * @param temiLocationName The TEMI location name (e.g., "engineering_wing_entrance")
     * @return Location object, or null if not found
     */
    public Location getLocation(String temiLocationName) {
        Location location = locationMap.get(temiLocationName);
        
        if (location == null) {
            Log.w(TAG, "Location not found: " + temiLocationName);
        } else {
            Log.d(TAG, "Retrieved location: " + location);
        }
        
        return location;
    }
}
