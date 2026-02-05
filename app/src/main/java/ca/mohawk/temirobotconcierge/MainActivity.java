package ca.mohawk.temirobotconcierge;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import com.robotemi.sdk.Robot;


public class MainActivity extends AppCompatActivity {
    private Robot robot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initTemiSdk();
    }

    private void initTemiSdk() {
        try {
            robot = Robot.getInstance();
            Log.d("TEMI", "Temi SDK linked");
        } catch (Throwable t) {
            robot = null;
            Log.d("TEMI", "Temi SDK not available on this device.");
        }
    }
}