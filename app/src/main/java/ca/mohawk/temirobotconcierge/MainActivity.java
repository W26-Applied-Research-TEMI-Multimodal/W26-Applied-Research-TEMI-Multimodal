package ca.mohawk.temirobotconcierge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import com.robotemi.sdk.Robot;

import ca.mohawk.temirobotconcierge.llm.GeminiLLMService;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private Robot robot;
    private GeminiLLMService geminiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initTemiSdk();
        initLLMService();
        setupUI();
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
     * Initialize the Gemini LLM
     */
    private void initLLMService() {
        Log.d(TAG, "Initializing LLM Service");
        geminiService = new GeminiLLMService();
        Log.d(TAG, "LLM Service initialized successfully");
    }
    
    /**
     * Setup UI buttons and listeners
     */
    private void setupUI() {
        Button selectLocationButton = findViewById(R.id.selectLocationButton);
        selectLocationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, LocationSelectActivity.class);
            startActivity(intent);
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (geminiService != null) {
            geminiService.shutdown();
        }
    }
}
