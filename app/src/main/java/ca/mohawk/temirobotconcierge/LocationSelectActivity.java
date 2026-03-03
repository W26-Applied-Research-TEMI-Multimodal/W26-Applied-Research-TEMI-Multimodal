package ca.mohawk.temirobotconcierge;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

import ca.mohawk.temirobotconcierge.poi.Location;
import ca.mohawk.temirobotconcierge.poi.LocationProvider;
import ca.mohawk.temirobotconcierge.llm.GeminiLLMService;
import ca.mohawk.temirobotconcierge.poi.PoiLocator;

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
    private GeminiLLMService geminiService;
    private LinearLayout locationsContainer;
    private PoiLocator poiLocator;
    private static final float WHERE_ARE_WE_MAX_DISTANCE_M = 2.0f;
    
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
            poiLocator = new PoiLocator();
            Log.d(TAG, "LocationProvider initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize LocationProvider: " + e.getMessage());
            finish();
            return;
        }
        
        // Initialize Gemini LLM service
        try {
            geminiService = new GeminiLLMService();
            Log.d(TAG, "GeminiLLMService initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize GeminiLLMService: " + e.getMessage());
            Toast.makeText(this, "Error initializing LLM service", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        addWhereAreWeButton();
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
        String displayName = (locationData != null) ? locationData.displayName : locationName;
        
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
            
            // Tell robot to go to this location immediately
            try {
                robot.goTo(locationName);
                Log.d(TAG, "Robot navigating to: " + locationName);
                Toast.makeText(this, "Navigating to " + displayName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to location: " + e.getMessage());
                Toast.makeText(this, "Error navigating: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Build prompt from location metadata
            String prompt = buildTourGuidePrompt(selectedLocation);
            Log.d(TAG, "Built prompt: " + prompt);
            
            // Send prompt to Gemini API (runs in background thread)
            geminiService.generateTourGuide(prompt, new GeminiLLMService.ResponseCallback() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "Gemini response received: " + response);
                    try {
                        robot.speak(TtsRequest.create(response));
                        Log.d(TAG, "Robot speaking tour guide");
                    } catch (Exception e) {
                        Log.e(TAG, "Error making robot speak: " + e.getMessage());
                    }
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Gemini API error: " + error);
                    Toast.makeText(LocationSelectActivity.this, "Error generating response: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });
        
        locationsContainer.addView(button);
    }

    private void addWhereAreWeButton() {
        Button whereBtn = new Button(this);
        whereBtn.setText("Where are we?");
        whereBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        whereBtn.setPadding(16, 16, 16, 16);

        whereBtn.setOnClickListener(v -> {

            poiLocator.buildWhereAreWeResponse(robot, locationProvider, WHERE_ARE_WE_MAX_DISTANCE_M, new PoiLocator.Callback<String>() {
                @Override
                public void onSuccess(String response) {
                    try {
                        robot.speak(TtsRequest.create(response));
                    } catch (Exception e) {
                        Toast.makeText(LocationSelectActivity.this,
                                "Speak error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String message) {
                    String fallback = (message == null || message.trim().isEmpty())
                            ? "I'm not sure where we are right now."
                            : message;

                    try {
                        robot.speak(TtsRequest.create(fallback));
                    } catch (Exception e) {
                        Toast.makeText(LocationSelectActivity.this,
                                "Speak error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });

        // Add at the top of the container
        locationsContainer.addView(whereBtn, 0);
    }


    /**
     * Build a tour guide prompt from location metadata
     * Uses location displayName, description, and wing to create context for Gemini
     */
    private String buildTourGuidePrompt(Location location) {
        if (location == null) {
            return "Generate a brief tour guide introduction for a campus location.";
        }
        
        String prompt = "The user is now visiting: " + location.displayName;
        
        if (location.wing != null && !location.wing.isEmpty()) {
            prompt += " (located in " + location.wing + ")";
        }
        
        if (location.description != null && !location.description.isEmpty()) {
            prompt += ". " + location.description;
        }
        
        prompt += " Provide a brief, welcoming introduction to the specific location for the visitor using only the location information provided.";
        
        return prompt;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (poiLocator != null) poiLocator.shutdown();
        if (geminiService != null) geminiService.shutdown();
    }
}
