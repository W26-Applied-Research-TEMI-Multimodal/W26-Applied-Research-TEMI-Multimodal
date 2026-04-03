package ca.mohawk.temirobotconcierge;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;

import ca.mohawk.temirobotconcierge.poi.Location;
import ca.mohawk.temirobotconcierge.poi.LocationProvider;
import ca.mohawk.temirobotconcierge.llm.GeminiLLMService;
import ca.mohawk.temirobotconcierge.poi.PoiLocator;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Activity for selecting a location to navigate to
 * 
 * Similar to the old Temi-Tour-App:
 * 1. Loads a map on TEMI
 * 2. Gets all available locations from that map
 * 3. Displays location names as buttons
 * 4. When user taps a location, robot navigates there
 */
public class LocationSelectActivity extends AppCompatActivity implements OnGoToLocationStatusChangedListener {
    private static final String TAG = "LocationSelectActivity";
    private static final String DEMO_LOCATION_NAME = "e103";
    private static final long DEMO_ARRIVAL_DELAY_MS = 2500L;
    private Robot robot;
    private LocationProvider locationProvider;
    private GeminiLLMService geminiService;
    private LinearLayout locationsContainer;
    private ProgressBar locationsLoadingIndicator;
    private ProgressBar navigationProgressIndicator;
    private TextView locationLoadStatus;
    private TextView locationTitle;
    private TextView navigationStatusText;
    private TextView tourGuideResponseText;
    private PoiLocator poiLocator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean demoMode;
    private String selectedMapId;
    private String selectedMapName;
    private String pendingLocationName;
    private String pendingDisplayName;
    private Location pendingLocationData;
    private static final float WHERE_ARE_WE_MAX_DISTANCE_M = 2.0f;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_select);
        
        locationsContainer = findViewById(R.id.locationsContainer);
        locationsLoadingIndicator = findViewById(R.id.locationsLoadingIndicator);
        navigationProgressIndicator = findViewById(R.id.navigationProgressIndicator);
        locationLoadStatus = findViewById(R.id.locationLoadStatus);
        locationTitle = findViewById(R.id.locationTitle);
        navigationStatusText = findViewById(R.id.navigationStatusText);
        tourGuideResponseText = findViewById(R.id.tourGuideResponseText);
        demoMode = getIntent().getBooleanExtra(MainActivity.EXTRA_DEMO_MODE, false);
        selectedMapId = getIntent().getStringExtra(MainActivity.EXTRA_SELECTED_MAP_ID);
        selectedMapName = getIntent().getStringExtra(MainActivity.EXTRA_SELECTED_MAP_NAME);

        if (selectedMapName != null && !selectedMapName.trim().isEmpty()) {
            locationTitle.setText("Select Location - " + selectedMapName);
        }
        
        // Get TEMI robot instance
        try {
            robot = Robot.getInstance();
            robot.addOnGoToLocationStatusChangedListener(this);
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
        
        if (!demoMode) {
            addWhereAreWeButton();
        }
        loadLocations();
    }
    
    /**
     * Load locations from TEMI and display as buttons
     */
    private void loadLocations() {
        Log.d(TAG, "Loading locations");
        showLocationsLoading(true, "Loading locations...");

        if (demoMode) {
            handler.postDelayed(this::loadDemoLocation, 850);
            return;
        }
        
        try {
            List<String> locationNames = robot.getLocations();
            String inferredWing = inferEWingFromMap(selectedMapName, selectedMapId);

            if (inferredWing != null) {
                List<Location> configuredWingLocations = locationProvider.getLocationsByWing(inferredWing);
                if (!configuredWingLocations.isEmpty()) {
                    Log.d(TAG, "Using configured locations for " + inferredWing + ": " + configuredWingLocations.size());
                    for (Location location : configuredWingLocations) {
                        createLocationButton(location.temiLocationName);
                    }
                    showLocationsLoading(false, "Select a location");
                    return;
                }
            }

            if (locationNames == null || locationNames.isEmpty()) {
                Log.w(TAG, "No locations available for selected map.");
                Toast.makeText(this, "No locations found for this map.", Toast.LENGTH_SHORT).show();
                showLocationsLoading(false, "No locations available for this map");
                return;
            }

            Log.d(TAG, "Using " + locationNames.size() + " robot POIs from loaded map");
            for (String locationName : locationNames) {
                createLocationButton(locationName);
                Log.d(TAG, "Added button for location: " + locationName);
            }
            showLocationsLoading(false, "Select a location");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading locations: " + e.getMessage());
            Toast.makeText(this, "Error loading locations for this map.", Toast.LENGTH_SHORT).show();
            showLocationsLoading(false, "Unable to load locations for this map");
        }
    }

    private void loadDemoLocation() {
        Log.d(TAG, "Loading hardcoded demo location");
        createLocationButton(DEMO_LOCATION_NAME);
        showLocationsLoading(false, "Demo location ready");
    }

    private String inferEWingFromMap(String mapName, String mapId) {
        String source = ((mapName == null) ? "" : mapName) + " " + ((mapId == null) ? "" : mapId);
        String normalized = source.trim().toLowerCase(Locale.US);

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.contains("e wing") || normalized.contains("ewing") || normalized.contains("east wing")) {
            return "E Wing";
        }

        Matcher matcher = Pattern.compile("\\be\\s*wing(?:\\s*\\d+)?\\b", Pattern.CASE_INSENSITIVE).matcher(source);
        if (matcher.find()) {
            return "E Wing";
        }

        return null;
    }
    
    /**
     * Create a button for a location
     * When tapped, robot navigates to that location and sends metadata to Gemini
     */
    private void createLocationButton(String locationName) {
        // Look up enriched location data from JSON
        String lookupName = locationName == null ? "" : locationName.trim().toLowerCase();
        Location locationData = locationProvider.getLocation(lookupName);
        
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
            startNavigationUi(locationName, displayName, selectedLocation);
            
            // Tell robot to go to this location immediately
            try {
                robot.goTo(locationName);
                Log.d(TAG, "Robot navigating to: " + locationName);
                Toast.makeText(this, "Navigating to " + displayName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to location: " + e.getMessage());
                if (!demoMode) {
                    showNavigationFailure("Navigation failed: " + e.getMessage());
                    Toast.makeText(this, "Error navigating: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, "Demo mode: simulated navigation to " + displayName, Toast.LENGTH_SHORT).show();
            }

            if (demoMode) {
                handler.postDelayed(() -> handleArrival(locationName), DEMO_ARRIVAL_DELAY_MS);
            }
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

    private void startNavigationUi(String locationName, String displayName, Location location) {
        pendingLocationName = locationName;
        pendingDisplayName = displayName;
        pendingLocationData = location;
        navigationProgressIndicator.setVisibility(android.view.View.VISIBLE);
        navigationStatusText.setText("Moving to " + displayName + "...");
        tourGuideResponseText.setVisibility(android.view.View.GONE);
        tourGuideResponseText.setText("");
        setLocationButtonsEnabled(false);
    }

    private void setLocationButtonsEnabled(boolean enabled) {
        for (int index = 0; index < locationsContainer.getChildCount(); index++) {
            locationsContainer.getChildAt(index).setEnabled(enabled);
        }
    }

    private void handleArrival(String locationName) {
        if (pendingLocationName == null || !pendingLocationName.equalsIgnoreCase(locationName)) {
            return;
        }

        String arrivedLocationName = pendingLocationName;
        String arrivedDisplayName = pendingDisplayName;
        Location arrivedLocationData = pendingLocationData;

        navigationProgressIndicator.setVisibility(android.view.View.VISIBLE);
        navigationStatusText.setText("Arrived at " + arrivedDisplayName + ". Generating welcome message...");
        tourGuideResponseText.setText("Generating tour-guide response...");
        tourGuideResponseText.setVisibility(android.view.View.VISIBLE);
        setLocationButtonsEnabled(true);

        pendingLocationName = null;
        pendingDisplayName = null;
        pendingLocationData = null;

        requestArrivalResponse(arrivedLocationName, arrivedLocationData, arrivedDisplayName);
    }

    private void requestArrivalResponse(String locationName, Location location, String displayName) {
        String fallbackResponse = buildArrivalTourResponse(locationName, location, displayName);
        String prompt = buildTourGuidePrompt(location);

        geminiService.generateTourGuide(prompt, new GeminiLLMService.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                if (response != null && !response.trim().isEmpty()) {
                    showArrivalResponse(displayName, response.trim(), false);
                } else {
                    showArrivalResponse(displayName, fallbackResponse, true);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Gemini API error on arrival: " + error);
                showArrivalResponse(displayName, fallbackResponse, true);
            }
        });
    }

    private void showArrivalResponse(String displayName, String response, boolean fallbackUsed) {
        navigationProgressIndicator.setVisibility(android.view.View.GONE);
        navigationStatusText.setText(
                fallbackUsed
                        ? "Arrived at " + displayName + " - showing fallback welcome"
                        : "Arrived at " + displayName
        );
        tourGuideResponseText.setText(response);
        tourGuideResponseText.setVisibility(android.view.View.VISIBLE);

        try {
            robot.speak(TtsRequest.create(response));
            Log.d(TAG, "Robot speaking arrival tour response");
        } catch (Exception e) {
            Log.e(TAG, "Error making robot speak: " + e.getMessage());
            Toast.makeText(this, "Speak error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showNavigationFailure(String message) {
        navigationProgressIndicator.setVisibility(android.view.View.GONE);
        navigationStatusText.setText(message);
        setLocationButtonsEnabled(true);
        pendingLocationName = null;
        pendingDisplayName = null;
        pendingLocationData = null;
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
        
        prompt += " You are a friendly campus tour guide robot."
                + " Write a spoken welcome of 3 to 4 sentences for a visitor who just arrived here."
                + " Be warm and conversational."
                + " Rephrase the details naturally — do not copy them word for word."
                + " Use only the facts provided and do not invent new ones."
                + " Do not end with a hype line, slogan, or call to action."
                + " No bullet points or markdown, plain spoken sentences only.";
        
        return prompt;
    }

    private String buildDemoTourResponse(Location location, String displayName) {
        String resolvedName = displayName;
        if (resolvedName == null || resolvedName.trim().isEmpty()) {
            resolvedName = (location != null && location.displayName != null && !location.displayName.trim().isEmpty())
                    ? location.displayName
                    : "this area";
        }

        String description = (location != null && location.description != null)
                ? location.description.trim()
                : "";

        String wing = (location != null && location.wing != null)
                ? location.wing.trim()
                : "";

        StringBuilder response = new StringBuilder();
        response.append("Welcome to ").append(resolvedName).append(". ");
        if (!wing.isEmpty()) {
            response.append("You're currently in the ").append(wing).append(". ");
        }
        if (!description.isEmpty()) {
            response.append(description).append(" ");
        }
        response.append("Let me know if you'd like directions to another point of interest.");
        return response.toString().trim();
    }

    private String buildArrivalTourResponse(String locationName, Location location, String displayName) {
        if (locationName != null && locationName.trim().equalsIgnoreCase(DEMO_LOCATION_NAME)) {
            return "Welcome to the Engineering Building. This area supports applied learning, labs, and hands-on project work for students and visitors.";
        }

        return buildDemoTourResponse(location, displayName);
    }

    @Override
    public void onGoToLocationStatusChanged(String location, String status, int descriptionId, String description) {
        Log.d(TAG, "Go-to status changed. location=" + location + ", status=" + status + ", description=" + description);

        if (pendingLocationName == null || location == null || !pendingLocationName.equalsIgnoreCase(location)) {
            return;
        }

        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();

        if (normalizedStatus.contains("complete")) {
            runOnUiThread(() -> handleArrival(location));
            return;
        }

        if (normalizedStatus.contains("going") || normalizedStatus.contains("start") || normalizedStatus.contains("calculating")) {
            runOnUiThread(() -> navigationStatusText.setText("Moving to " + pendingDisplayName + "..."));
            return;
        }

        if (normalizedStatus.contains("abort") || normalizedStatus.contains("cancel") || normalizedStatus.contains("fail")) {
            String failureMessage = (description == null || description.trim().isEmpty())
                    ? "Navigation stopped before arrival"
                    : description;
            runOnUiThread(() -> showNavigationFailure(failureMessage));
        }
    }

    private void showLocationsLoading(boolean show, String status) {
        locationsLoadingIndicator.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        locationLoadStatus.setText(status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try {
            if (robot != null) {
                robot.removeOnGoToLocationStatusChangedListener(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing go-to listener: " + e.getMessage());
        }
        if (poiLocator != null) poiLocator.shutdown();
        if (geminiService != null) geminiService.shutdown();
    }
}
