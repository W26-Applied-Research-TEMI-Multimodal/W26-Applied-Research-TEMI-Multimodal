package ca.mohawk.temirobotconcierge;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.robotemi.sdk.Robot;

import ca.mohawk.temirobotconcierge.poi.Location;
import ca.mohawk.temirobotconcierge.poi.LocationProvider;

import java.util.List;

/**
 * Activity for selecting a location to navigate to
 * 
 * Similar to the old Temi-Tour-App:
 * 1. Loads a map on TEMI
 * 2. Gets all available locations from that map
 * 3. Displays location names as buttons
 * 4. When user taps a location, robot navigates there
 */
public class LocationSelectActivity extends AppCompatActivity {
    private static final String TAG = "LocationSelectActivity";
    
    private Robot robot;
    private LocationProvider locationProvider;
    private LinearLayout locationsContainer;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_select);
        
        locationsContainer = findViewById(R.id.locationsContainer);
        
        // Get TEMI robot instance
        try {
            robot = Robot.getInstance();
            Log.d(TAG, "Robot instance obtained");
        } catch (Exception e) {
            Log.e(TAG, "Failed to get Robot instance: " + e.getMessage());
            Toast.makeText(this, "Robot not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize LocationProvider to access location metadata from JSON
        try {
            locationProvider = new LocationProvider(this);
            Log.d(TAG, "LocationProvider initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize LocationProvider: " + e.getMessage());
            finish();
            return;
        }
        
        // Load and display locations
        loadLocations();
    }
    
    /**
     * Load locations from TEMI and display as buttons
     */
    private void loadLocations() {
        Log.d(TAG, "Loading locations");
        
        try {
            // Get list of location names from current map
            List<String> locationNames = robot.getLocations();
            
            if (locationNames == null || locationNames.isEmpty()) {
                Log.w(TAG, "No locations available. Make sure a map is loaded first.");
                Toast.makeText(this, "No locations found. Load a map first.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d(TAG, "Found " + locationNames.size() + " locations");
            
            // Create a button for each location
            for (String locationName : locationNames) {
                createLocationButton(locationName);
                Log.d(TAG, "Added button for location: " + locationName);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading locations: " + e.getMessage());
            Toast.makeText(this, "Error loading locations: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Create a button for a location
     * When tapped, robot navigates to that location and sends metadata to Gemini
     */
    private void createLocationButton(String locationName) {
        // Look up enriched location data from JSON
        Location locationData = locationProvider.getLocation(locationName);
        
        // Use display name if available, otherwise fall back to location name
        String displayName = (locationData != null) ? locationData.getDisplayName() : locationName;
        
        Button button = new Button(this);
        button.setText(displayName);
        button.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        button.setPadding(16, 16, 16, 16);
        
        // Store the Location object as tag for later use when building Gemini prompt
        button.setTag(locationData);
        
        // When clicked, navigate robot to this location and prepare for Gemini
        button.setOnClickListener(v -> {
            Log.d(TAG, "User selected location: " + locationName);
            
            // Get the Location object with all metadata
            Location selectedLocation = (Location) button.getTag();
            if (selectedLocation != null) {
                Log.d(TAG, "Location metadata: " + selectedLocation);
            }
            
            // Tell robot to go to this location
            try {
                robot.goTo(locationName);
                Log.d(TAG, "Robot navigating to: " + locationName);
                Toast.makeText(this, "Navigating to " + displayName, Toast.LENGTH_SHORT).show();
                
                // TODO: Use selectedLocation metadata to build Gemini prompt
                // Build prompt with: displayName, description, wing, temiLocationName
                // Send prompt to Gemini API
                // Speak response via robot.speak()
                
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to location: " + e.getMessage());
                Toast.makeText(this, "Error navigating: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        locationsContainer.addView(button);
    }
}
