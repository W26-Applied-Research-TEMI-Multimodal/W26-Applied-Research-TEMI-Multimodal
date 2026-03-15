package ca.mohawk.temirobotconcierge;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.map.MapModel;
import com.robotemi.sdk.map.OnLoadMapStatusChangedListener;

import java.util.List;

/** Main Activity - Displays available maps and allows user to select one
 * Flow:
 * 1. Get all available maps via robot.getMapList()
 * 2. Display maps as buttons
 * 3. When user taps a map, load it via robot.loadMap(mapId)
 * 4. Listen for load completion via OnLoadMapStatusChangedListener
 * 5. Launch LocationSelectActivity when map is fully loaded
 *
 * Cloning new repo
 */
public class MainActivity extends AppCompatActivity implements OnLoadMapStatusChangedListener {
    private static final String TAG = "MainActivity";
    public static final String EXTRA_DEMO_MODE = "extra_demo_mode";
    public static final String EXTRA_SELECTED_MAP_ID = "extra_selected_map_id";
    public static final String EXTRA_SELECTED_MAP_NAME = "extra_selected_map_name";
    private static final String DEMO_MAP_ID = "demo_mohawk_campus_map";
    private static final String DEMO_MAP_NAME = "Demo Campus Map";
    private Robot robot;
    private LinearLayout mapsContainer;
    private ProgressBar mapLoadingIndicator;
    private TextView mapLoadStatus;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingMapId;  // Track which map is being loaded
    private String pendingMapName;
    private boolean pendingDemoMode;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mapsContainer = findViewById(R.id.mapsContainer);
        mapLoadingIndicator = findViewById(R.id.mapLoadingIndicator);
        mapLoadStatus = findViewById(R.id.mapLoadStatus);
        
        // Initialize Temi SDK
        robot = tryGetRobotOrFinish();
        if (robot == null) {
            return;
        }

        // Register listener for map load status
        robot.addOnLoadMapStatusChangedListener(this);
        
        // Load and display available maps
        loadMaps();
    }

    private Robot tryGetRobotOrFinish() {
        try {
            Robot r = Robot.getInstance();
            Log.d(TAG, "Robot instance obtained");
            return r;
        } catch (Throwable t) {
            Log.e(TAG, "Robot not available (Temi SDK not linked or not running on device)", t);
            Toast.makeText(this,"Robot not available", Toast.LENGTH_SHORT).show();
            finish();
            return null;
        }
    }

    /**
     * Get all available maps and display as buttons
     */
    private void loadMaps() {
        Log.d(TAG, "Loading maps");
        
        try {
            // Get list of all available maps
            List<MapModel> mapList = robot.getMapList();
            
            if (mapList == null || mapList.isEmpty()) {
                Log.w(TAG, "No maps available on robot. Demo map will be shown.");
                Toast.makeText(this, "No maps found on robot. Demo map enabled.", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "Found " + mapList.size() + " maps");

                // Create a button for each map
                for (MapModel map : mapList) {
                    createMapButton(map.getId(), map.getName(), false);
                    Log.d(TAG, "Added button for map: " + map.getName() + " (ID: " + map.getId() + ")");
                }
            }

            // Always include one hardcoded demo map so demo flow works without real mapping.
            createMapButton(DEMO_MAP_ID, DEMO_MAP_NAME, true);
            Log.d(TAG, "Added hardcoded demo map button");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading maps: " + e.getMessage());
            Toast.makeText(this, "Error loading maps: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            pendingMapId = null;
            enableAllButtons();
            createMapButton(DEMO_MAP_ID, DEMO_MAP_NAME, true);
        }
    }
    
    /**
     * Create a button for a map
     * When tapped, load that map
     */
    private void createMapButton(String mapId, String mapName, boolean demoMode) {
        android.widget.Button button = new android.widget.Button(this);
        button.setText(demoMode ? mapName + " (Hardcoded Demo)" : mapName);
        button.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        button.setPadding(16, 16, 16, 16);
        button.setTextSize(16);
        
        // When tapped, load this map
        button.setOnClickListener(v -> {
            Log.d(TAG, "User selected map: " + mapName);
            pendingMapId = mapId;
            pendingMapName = mapName;
            pendingDemoMode = demoMode;
            disableAllButtons();
            showMapLoading(true, "Loading \"" + mapName + "\"...");

            if (demoMode) {
                handler.postDelayed(() -> {
                    Log.d(TAG, "Demo map selected. Simulating successful map load.");
                    launchLocationSelector();
                }, 900);
                return;
            }
            
            try {
                // Load the map offline (faster, uses local cache)
                Log.d(TAG, "Loading map: " + mapId);
                robot.loadMap(mapId, false, null, true, false);
                Toast.makeText(this, "Loading " + mapName + "...", Toast.LENGTH_SHORT).show();
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading map: " + e.getMessage());
                Toast.makeText(this, "Error loading map: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                showMapLoading(false, "Select a map");
                enableAllButtons();
                pendingMapId = null;
            }
        });
        
        mapsContainer.addView(button);
    }

    private void showMapLoading(boolean show, String status) {
        mapLoadingIndicator.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        mapLoadStatus.setText(status);
    }
    
    /**
     * Disable all map selection buttons during loading
     */
    private void disableAllButtons() {
        for (int i = 0; i < mapsContainer.getChildCount(); i++) {
            mapsContainer.getChildAt(i).setEnabled(false);
        }
    }
    
    /**
     * Enable all map selection buttons
     */
    private void enableAllButtons() {
        for (int i = 0; i < mapsContainer.getChildCount(); i++) {
            mapsContainer.getChildAt(i).setEnabled(true);
        }
    }
    
    /**
     * Listener for map load status changes
     */
    @Override
    public void onLoadMapStatusChanged(
        @OnLoadMapStatusChangedListener.Status int status,
        String requestId
    ) {
        Log.d(TAG, "Map load status: " + status);
        
        switch (status) {
            case OnLoadMapStatusChangedListener.START:
                Log.d(TAG, "Map loading started");
                showMapLoading(true, "Loading map data...");
                break;
                
            case OnLoadMapStatusChangedListener.COMPLETE:
                Log.d(TAG, "Map loaded successfully");
                if (pendingMapId != null) {
                    launchLocationSelector();
                }
                break;
                
            case OnLoadMapStatusChangedListener.ERROR_GET_MAP_DATA:
            case OnLoadMapStatusChangedListener.ERROR_ABORT_ON_TIMEOUT:
            case OnLoadMapStatusChangedListener.ERROR_ABORT_BUSY:
            case OnLoadMapStatusChangedListener.ERROR_ABORT_FROM_ROBOX:
            case OnLoadMapStatusChangedListener.ERROR_ABORT_ON_NOT_CHARGING:
            case OnLoadMapStatusChangedListener.ERROR_UNKNOWN:
            case OnLoadMapStatusChangedListener.ERROR_PB_STREAM_FILE_INVALID:
                Log.e(TAG, "Map loading failed with status: " + status);
                Toast.makeText(this, "Failed to load map", Toast.LENGTH_SHORT).show();
                enableAllButtons();
                showMapLoading(false, "Select a map");
                pendingMapId = null;
                break;
        }
    }
    
    /**
     * Map loaded successfully, launch LocationSelectActivity
     */
    private void launchLocationSelector() {
        Log.d(TAG, "Launching LocationSelectActivity");
        showMapLoading(false, "Select a map");
        Intent intent = new Intent(this, LocationSelectActivity.class);
        intent.putExtra(EXTRA_DEMO_MODE, pendingDemoMode);
        intent.putExtra(EXTRA_SELECTED_MAP_ID, pendingMapId);
        intent.putExtra(EXTRA_SELECTED_MAP_NAME, pendingMapName);
        startActivity(intent);
        pendingMapId = null;
        pendingMapName = null;
        pendingDemoMode = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableAllButtons();
        showMapLoading(false, "Select a map");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister listener when done
        try {
            handler.removeCallbacksAndMessages(null);
            if (robot != null) {
                robot.removeOnLoadMapStatusChangedListener(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing listener: ", e);
        }
    }
}
