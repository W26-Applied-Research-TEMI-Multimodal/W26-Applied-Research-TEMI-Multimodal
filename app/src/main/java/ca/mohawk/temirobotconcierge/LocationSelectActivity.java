package ca.mohawk.temirobotconcierge;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.robotemi.sdk.Robot;

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
     * When tapped, robot navigates to that location
     */
    private void createLocationButton(String locationName) {
        Button button = new Button(this);
        button.setText(locationName);
        button.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        button.setPadding(16, 16, 16, 16);
        
        // When tapped, navigate robot to this location
        button.setOnClickListener(v -> {
            Log.d(TAG, "User selected location: " + locationName);
            
            // Tell robot to go to this location
            try {
                robot.goTo(locationName);
                Log.d(TAG, "Robot navigating to: " + locationName);
                Toast.makeText(this, "Navigating to " + locationName, Toast.LENGTH_SHORT).show();
                
                // TODO: Get location coordinates from TEMI map data
                // Then call handleLocationArrival() to send to Gemini
                // locationDataHandler.createLocationData(locationName, x, y);
                
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to location: " + e.getMessage());
                Toast.makeText(this, "Error navigating: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        locationsContainer.addView(button);
    }
}
