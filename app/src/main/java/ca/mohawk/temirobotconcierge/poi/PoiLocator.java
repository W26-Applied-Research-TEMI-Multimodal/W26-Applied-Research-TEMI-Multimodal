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

        public Result(Position currentPosition, @Nullable String nearestTemiLocationName,float nearestDistance) {
            this.currentPosition = currentPosition;
            this.nearestTemiLocationName = nearestTemiLocationName;
            this.nearestDistance = nearestDistance;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void shutdown() {
        executor.shutdown();
    }

    public void findNearestSavedLocation(com.robotemi.sdk.Robot robot, Callback callback) {
        executor.execute(() -> {
            if (robot == null) {
                callback.onError("Robot instance is null.");
                return;
            }

            Position currentPosition = robot.getPosition();

            if (currentPosition == null) {
                callback.onError("Unable to retrieve robot position.");
                return;
            }

            Result result = new Result(currentPosition,null,-1f);

            callback.onSuccess(result);
        });
    }

}
