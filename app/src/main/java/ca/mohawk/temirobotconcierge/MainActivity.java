package ca.mohawk.temirobotconcierge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.map.MapModel;
import com.robotemi.sdk.map.OnLoadMapStatusChangedListener;

import ca.mohawk.temirobotconcierge.llm.GeminiLLMService;

import java.util.List;

/** Main Activity - Displays available maps and allows user to select one
 * Flow:
 * 1. Get all available maps via robot.getMapList()
 * 2. Display maps as buttons
 * 3. When user taps a map, load it via robot.loadMap(mapId)
 * 4. Listen for load completion via OnLoadMapStatusChangedListener
 * 5. Launch LocationSelectActivity when map is fully loaded
 */
public class MainActivity extends AppCompatActivity implements OnLoadMapStatusChangedListener {
    private static final String TAG = "MainActivity";
    private Robot robot;
    private GeminiLLMService geminiService;
    private LinearLayout mapsContainer;
    private String pendingMapId;  // Track which map is being loaded
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mapsContainer = findViewById(R.id.mapsContainer);
        
        // Initialize services
        initTemiSdk();
        initLLMService();
        
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
        
        // Register listener for map load status
        robot.addOnLoadMapStatusChangedListener(this);
        
        // Load and display available maps
        loadMaps();
    }
    
    private void initTemiSdk() {
        try {
            robot = Robot.getInstance();
            Log.d(TAG, "Temi SDK linked");
        } catch (Throwable t) {
            robot = null;
            Log.d(TAG, "Temi SDK not available on this device.");
        }
    }
    
    /**
     * Initialize the Gemini LLM Service
     */
    private void initLLMService() {
        Log.d(TAG, "Initializing LLM Service");
        geminiService = new GeminiLLMService();
        Log.d(TAG, "LLM Service initialized successfully");
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
                Log.w(TAG, "No maps available");
                Toast.makeText(this, "No maps found on robot", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d(TAG, "Found " + mapList.size() + " maps");
            
            // Create a button for each map
            for (MapModel map : mapList) {
                createMapButton(map.getId(), map.getName());
                Log.d(TAG, "Added button for map: " + map.getName() + " (ID: " + map.getId() + ")");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading maps: " + e.getMessage());
            Toast.makeText(this, "Error loading maps: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Create a button for a map
     * When tapped, load that map
     */
    private void createMapButton(String mapId, String mapName) {
        android.widget.Button button = new android.widget.Button(this);
        button.setText(mapName);
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
            
            try {
                // Load the map offline (faster, uses local cache)
                Log.d(TAG, "Loading map: " + mapId);
                robot.loadMap(mapId, false, null, true, false);
                Toast.makeText(this, "Loading " + mapName + "...", Toast.LENGTH_SHORT).show();
                
                // Disable all buttons while loading
                disableAllButtons();
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading map: " + e.getMessage());
                Toast.makeText(this, "Error loading map: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                pendingMapId = null;
            }
        });
        
        mapsContainer.addView(button);
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
                pendingMapId = null;
                break;
        }
    }
    
    /**
     * Map loaded successfully, launch LocationSelectActivity
     */
    private void launchLocationSelector() {
        Log.d(TAG, "Launching LocationSelectActivity");
        Intent intent = new Intent(this, LocationSelectActivity.class);
        startActivity(intent);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (geminiService != null) {
            geminiService.shutdown();
        }
        // Unregister listener when done
        try {
            if (robot != null) {
                robot.removeOnLoadMapStatusChangedListener(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing listener: " + e.getMessage());
        }
    }
}
