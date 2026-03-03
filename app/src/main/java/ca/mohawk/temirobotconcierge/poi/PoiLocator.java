package ca.mohawk.temirobotconcierge.poi;

import androidx.annotation.Nullable;

import com.robotemi.sdk.navigation.model.Position;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PoiLocator
 * - Finds the nearest saved temi location to the robot's current position.
 * - Worker-thread execution (no UI blocking).
 */
public class PoiLocator {
    public interface Callback {
        void onSuccess(Result result);
        void onError(String message);
    }

    public static class Result {
        public final Position currentPosition;
        public final @Nullable String nearestTemiLocationName;
        public final float nearestDistance;

        public Result(Position currentPosition,
                      @Nullable String nearestTemiLocationName,
                      float nearestDistance) {
            this.currentPosition = currentPosition;
            this.nearestTemiLocationName = nearestTemiLocationName;
            this.nearestDistance = nearestDistance;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void shutdown() {
        executor.shutdown();
    }
}
