package ca.mohawk.temirobotconcierge.poi;

import androidx.annotation.Nullable;

import com.robotemi.sdk.navigation.model.Position;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.robotemi.sdk.map.Layer;
import com.robotemi.sdk.map.LayerPose;
import com.robotemi.sdk.map.MapDataModel;

import static com.robotemi.sdk.map.MapDataModelKt.LOCATION;

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

    public interface CallbackString {
        void onSuccess(String response);
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

            // Get current position
            Position currentPosition = robot.getPosition();
            if (currentPosition == null) {
                callback.onError("Unable to retrieve robot position.");
                return;
            }

            // Get map data
            MapDataModel mapData = robot.getMapData();
            if (mapData == null) {
                callback.onError("No map data available. Load a map first.");
                return;
            }

            // Validate saved locations exist
            if (mapData.getLocations() == null || mapData.getLocations().isEmpty()) {
                callback.onError("No saved locations found in current map.");
                return;
            }

            // Find nearest LOCATION layer
            String bestName = null;
            float bestDist = Float.MAX_VALUE;

            for (Layer layer : mapData.getLocations()) {
                if (layer == null) continue;
                if (layer.getLayerCategory() != LOCATION) continue;

                String name = layer.getLayerId();
                if (name == null || name.trim().isEmpty()) continue;

                java.util.List<LayerPose> poses = layer.getLayerPoses();
                if (poses == null || poses.isEmpty()) continue;

                // Saved locations should have 1 pose
                LayerPose p = poses.get(0);

                float d = distance2D(currentPosition.getX(),currentPosition.getY(), p.getX(), p.getY());

                if (d < bestDist) {
                    bestDist = d;
                    bestName = name;
                }
            }

            if (bestName == null) {
                callback.onError("No LOCATION layers found in map data.");
                return;
            }

            Result result = new Result( currentPosition,bestName,bestDist);

            callback.onSuccess(result);
        });
    }

    private float distance2D(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public void buildWhereAreWeResponse(com.robotemi.sdk.Robot robot,LocationProvider locationProvider,
            float maxDistanceMeters,CallbackString callback) {
        findNearestSavedLocation(robot, new Callback() {
            @Override
            public void onSuccess(Result result) {
                String nearestName = result.nearestTemiLocationName;

                if (nearestName == null) {
                    callback.onError("I’m not sure where we are right now.");
                    return;
                }

                // Normalize because Temi LOCATION layerIds are lowercased
                nearestName = nearestName.trim().toLowerCase();

                if (result.nearestDistance > maxDistanceMeters) {
                    callback.onError("I’m not close enough to a saved point of interest to be sure where we are.");
                    return;
                }

                Location loc = locationProvider != null ? locationProvider.getLocation(nearestName) : null;

                if (loc != null) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("We’re at ").append(loc.displayName).append(".");

                    if (loc.wing != null && !loc.wing.isEmpty()) {
                        msg.append(" This is in the ").append(loc.wing).append(".");
                    }

                    callback.onSuccess(msg.toString());
                } else {
                    callback.onSuccess("We’re near " + nearestName + ".");
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

}
