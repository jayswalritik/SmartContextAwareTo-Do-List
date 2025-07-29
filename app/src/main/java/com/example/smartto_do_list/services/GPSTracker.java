package com.example.smartto_do_list.services;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.R;
import com.example.smartto_do_list.receivers.GPSWakeupReceiver;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import android.os.Handler;
import android.os.Looper;

import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDao;
import com.example.smartto_do_list.TaskDatabase;
import com.example.smartto_do_list.SavedLocations;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// [Unchanged imports… same as before]

public class GPSTracker {
    // [Unchanged constants and fields]
    private static final String TAG = "GPSTracker";
    private static final String CHANNEL_ID = "gps_speed_channel";
    private static final int NOTIFICATION_ID = 9999;

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private Location lastLocation;
    private float currentSpeed = 0f;
    private LocationCallback locationCallback;
    private long lastNotificationTime = 0;
    private static final long NOTIFICATION_COOLDOWN_MS = 100;

    private TaskDao taskDao;
    private final Handler gpsHandler = new Handler(Looper.getMainLooper());
    private Runnable scheduledCheckRunnable;
    private Location lastKnownLocation;

    public final Set<Integer> notifiedLocationTaskIds = new HashSet<>();

    private static final long MIN_RECHECK_BUFFER_MS = 2000;
    private static final float PROXIMITY_RADIUS = 100;
    private static final float EXIT_RADIUS = 120;

    private boolean forceTracking = false;

    // NEW: Continuous tracking state management
    private boolean isContinuousTracking = false;
    private int continuousTrackingTaskId = -1;
    private long continuousTrackingStartTime = 0;
    private static final long CONTINUOUS_TRACKING_DURATION_MS = 15_000; // 10 seconds
    private LocationCallback continuousLocationCallback;
    private Runnable continuousTrackingTimeoutRunnable;

    private static final int SPEED_SMOOTHING_WINDOW = 5;
    private final float[] speedBuffer = new float[SPEED_SMOOTHING_WINDOW];
    private int speedBufferIndex = 0;
    private int speedBufferCount = 0;

    private float averageSpeed = 0f;
    private float lowerSpeedBound = 0f;

    private Location gpsStartLocation;
    private long gpsStartTimeMs = 0;
    private float finalSmoothedSpeed = 0f;

    private Runnable stopTrackingRunnable;

    private static final int MAX_LOCATION_HISTORY = 5;
    private final Location[] locationHistory = new Location[MAX_LOCATION_HISTORY];
    private int locationHistoryIndex = 0;

    public void startTracking(boolean forceStart) {
        forceTracking = forceStart;
        startTracking(); // existing method
    }

    public boolean isForceTracking() {
        return forceTracking;
    }

    public GPSTracker(Context context) {
        this.context = context.getApplicationContext();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
        createNotificationChannel();
        taskDao = TaskDatabase.getInstance(context).taskDao();
        Log.d(TAG, "GPSTracker initialized");
    }

    @SuppressLint("MissingPermission")
    public void startTracking() {
        Log.d(TAG, "Starting GPS tracking");

        if (locationCallback != null) return; // Already tracking

        gpsStartTimeMs = System.currentTimeMillis();

        for (int i = 0; i < MAX_LOCATION_HISTORY; i++) locationHistory[i] = null;
        locationHistoryIndex = 0;

        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(200)
                .setFastestInterval(100)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    if (gpsStartLocation == null) {
                        gpsStartLocation = new Location(location);
                        Log.d(TAG, "GPS Start Lat: " + gpsStartLocation.getLatitude() + ", Lon: " + gpsStartLocation.getLongitude());
                    }

                    // Store to history buffer
                    locationHistory[locationHistoryIndex] = new Location(location);
                    locationHistoryIndex = (locationHistoryIndex + 1) % MAX_LOCATION_HISTORY;

                    calculateSpeed(location);
                    lastKnownLocation = location;
                    checkProximityToSavedLocations(location);
                    resetNotificationIfExited(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    /**
     * NEW: Start continuous tracking for a specific task when ETA < 10 seconds
     */
    @SuppressLint("MissingPermission")
    public void startContinuousTrackingForTask(int taskId) {
        if (isContinuousTracking) {
            Log.d(TAG, "Already in continuous tracking mode for task " + continuousTrackingTaskId);
            return;
        }

        Log.d(TAG, "Starting continuous tracking for task " + taskId);

        // Stop regular tracking if running
        if (locationCallback != null) {
            stopTracking();
        }

        isContinuousTracking = true;
        continuousTrackingTaskId = taskId;
        continuousTrackingStartTime = System.currentTimeMillis();

        // Create location request with 1-second intervals
        LocationRequest continuousRequest = LocationRequest.create()
                .setInterval(1000) // 1 second intervals
                .setFastestInterval(500)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxWaitTime(1000);

        continuousLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || !isContinuousTracking) return;

                for (Location location : locationResult.getLocations()) {
                    lastKnownLocation = location;
                    calculateSpeed(location);

                    // Check if user reached the target task
                    if (checkSpecificTaskProximity(location, continuousTrackingTaskId)) {
                        Log.d(TAG, "Task " + continuousTrackingTaskId + " reached during continuous tracking!");
                        stopContinuousTracking();
                        return;
                    }
                }
            }
        };

        // Start continuous location updates
        fusedLocationClient.requestLocationUpdates(continuousRequest, continuousLocationCallback, null);

        // Set up 10-second timeout to re-evaluate ETA
        scheduleContinuousTrackingTimeout();

        // Show notification
        showContinuousTrackingNotification(taskId);
    }

    /**
     * NEW: Check proximity for a specific task during continuous tracking
     */
    private boolean checkSpecificTaskProximity(Location currentLocation, int taskId) {
        Task task = taskDao.getTaskById(taskId);
        if (task == null || task.locationId <= 0) {
            Log.w(TAG, "Task " + taskId + " not found or has no location");
            return false;
        }

        SavedLocations saved = taskDao.getSavedLocationById(task.locationId);
        if (saved == null) {
            Log.w(TAG, "Saved location not found for task " + taskId);
            return false;
        }

        Location target = new Location("");
        target.setLatitude(saved.getLatitude());
        target.setLongitude(saved.getLongitude());

        float distance = currentLocation.distanceTo(target);
        Log.d(TAG, "Continuous tracking - Distance to task " + taskId + ": " + distance + "m");

        if (distance <= PROXIMITY_RADIUS) {
            // Fire notification and mark as notified
            if (LocationNotificationManager.getInstance(context).shouldSendNotification(taskId)) {
                NotificationScheduler.scheduleImmediateNotification(context, task);
                Log.d(TAG, "Continuous tracking notification fired for task " + taskId);
            }

            notifiedLocationTaskIds.add(taskId);
            return true; // Task reached
        }

        return false; // Task not reached yet
    }

    /**
     * NEW: Schedule timeout for continuous tracking (10 seconds)
     */
    private void scheduleContinuousTrackingTimeout() {
        // Cancel any existing timeout
        if (continuousTrackingTimeoutRunnable != null) {
            gpsHandler.removeCallbacks(continuousTrackingTimeoutRunnable);
        }

        continuousTrackingTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Continuous tracking timeout reached for task " + continuousTrackingTaskId);

                // Re-check ETA after 10 seconds
                if (lastKnownLocation != null && currentSpeed > 0.1f) {
                    long newEta = calculateEtaForTask(continuousTrackingTaskId);

                    if (newEta > 0 && newEta <= 15_000) {
                        // ETA still < 10 seconds, continue continuous tracking
                        Log.d(TAG, "ETA still < 10s (" + newEta + "ms), continuing continuous tracking");
                        continuousTrackingStartTime = System.currentTimeMillis();
                        scheduleContinuousTrackingTimeout(); // Schedule another 10s timeout
                        return;
                    }
                }

                // ETA > 10 seconds or invalid, switch back to normal scheduling
                Log.d(TAG, "ETA > 10s or invalid, switching to normal scheduling");
                stopContinuousTracking();
                scheduleNextGpsCheckToLocation(); // Resume normal scheduling
            }
        };

        gpsHandler.postDelayed(continuousTrackingTimeoutRunnable, CONTINUOUS_TRACKING_DURATION_MS);
    }

    /**
     * NEW: Calculate ETA for a specific task
     */
    private long calculateEtaForTask(int taskId) {
        if (lastKnownLocation == null || currentSpeed <= 0.1f) return 0;

        Task task = taskDao.getTaskById(taskId);
        if (task == null || task.locationId <= 0) return 0;

        SavedLocations saved = taskDao.getSavedLocationById(task.locationId);
        if (saved == null) return 0;

        Location target = new Location("");
        target.setLatitude(saved.getLatitude());
        target.setLongitude(saved.getLongitude());

        return calculateEtaMillis(lastKnownLocation, target, currentSpeed);
    }

    /**
     * NEW: Stop continuous tracking and clean up resources
     */
    public void stopContinuousTracking() {
        if (!isContinuousTracking) return;

        Log.d(TAG, "Stopping continuous tracking for task " + continuousTrackingTaskId);

        // Remove location updates
        if (continuousLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(continuousLocationCallback);
            continuousLocationCallback = null;
        }

        // Cancel timeout
        if (continuousTrackingTimeoutRunnable != null) {
            gpsHandler.removeCallbacks(continuousTrackingTimeoutRunnable);
            continuousTrackingTimeoutRunnable = null;
        }

        // Clear notification
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(continuousTrackingTaskId + 40000); // Cancel continuous tracking notification
        }

        // Reset state
        isContinuousTracking = false;
        continuousTrackingTaskId = -1;
        continuousTrackingStartTime = 0;
    }

    /**
     * NEW: Show notification during continuous tracking
     */
    private void showContinuousTrackingNotification(int taskId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.addtask)
                .setContentTitle("Continuous GPS Tracking")
                .setContentText("Tracking task " + taskId + " - ETA < 10 seconds")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true); // Make it persistent

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(taskId + 40000, builder.build());
        }
    }

    // [Rest of the existing methods remain unchanged, but modify shouldKeepTrackingWithin10s]

    private boolean shouldKeepTrackingWithin10s() {
        if (lastKnownLocation == null || currentSpeed <= 0.1f) return false;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<Task> tasks = taskDao.getTasksWithLocationAndDate(today);

        for (Task task : tasks) {
            if (task.locationId <= 0 || notifiedLocationTaskIds.contains(task.id)) continue;

            SavedLocations saved = taskDao.getSavedLocationById(task.locationId);
            Location target = new Location("");
            target.setLatitude(saved.getLatitude());
            target.setLongitude(saved.getLongitude());

            long eta = calculateEtaMillis(lastKnownLocation, target, currentSpeed);
            if (eta > 0 && eta <= 15_000) {
                Log.d(TAG, "ETA < 10s for task " + task.id + " (" + eta + "ms) - Starting continuous tracking");

                // NEW: Start continuous tracking instead of short GPS window
                startContinuousTrackingForTask(task.id);
                return false; // Stop regular tracking since we're switching to continuous
            }
        }

        return false;
    }

    // [Keep all other existing methods unchanged: resetNotificationIfExited, checkProximityToSavedLocations,
    // scheduleNextGpsCheckToLocation, scheduleTaskCheck, calculateSpeed, calculateEtaMillis, stopTracking, etc.]

    public boolean isContinuousTracking() {
        return isContinuousTracking;
    }

    public int getContinuousTrackingTaskId() {
        return continuousTrackingTaskId;
    }

    // NEW: Setter methods for GPSWakeupReceiver
    public void setCurrentSpeed(float speed) {
        this.currentSpeed = speed;
    }

    public void setLastKnownLocation(Location location) {
        this.lastKnownLocation = location;
    }

    // Keep all existing methods unchanged:
    private void resetNotificationIfExited(Location current) {
        for (int taskId : new HashSet<>(notifiedLocationTaskIds)) {
            Task task = taskDao.getTaskById(taskId);
            if (task == null) continue;
            SavedLocations saved = taskDao.getSavedLocationById(task.locationId);
            Location target = new Location("");
            target.setLatitude(saved.getLatitude());
            target.setLongitude(saved.getLongitude());

            float distance = current.distanceTo(target);
            if (distance > EXIT_RADIUS) {
                Log.d(TAG, "Exited radius of task " + taskId + ", resetting notification eligibility.");
                notifiedLocationTaskIds.remove(taskId);
                LocationNotificationManager.getInstance(context).resetNotification(taskId);
            }
        }
    }

    private void checkProximityToSavedLocations(Location current) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<Task> tasks = taskDao.getTasksWithLocationAndDate(today);

        for (Task task : tasks) {
            if (task.locationId <= 0 || notifiedLocationTaskIds.contains(task.id)) continue;

            SavedLocations saved = taskDao.getSavedLocationById(task.locationId);
            Location target = new Location("");
            target.setLatitude(saved.getLatitude());
            target.setLongitude(saved.getLongitude());

            float distance = current.distanceTo(target);
            if (distance <= PROXIMITY_RADIUS) {
                if (LocationNotificationManager.getInstance(context).shouldSendNotification(task.id)) {
                    NotificationScheduler.scheduleImmediateNotification(context, task);
                    Log.d(TAG, "GPS location notification sent");
                } else {
                    Log.d(TAG, "GPS notification skipped - already sent");
                }

                notifiedLocationTaskIds.add(task.id);

                if (!shouldKeepTrackingWithin10s()) {
                    Log.d(TAG, "Reached task location and no nearby tasks remain → stopping GPS");
                    stopTracking();
                } else {
                    Log.d(TAG, "Task reached, but keeping GPS alive for nearby tasks");
                }
            }
        }
    }

    private void scheduleNextGpsCheckToLocation() {
        if (lastKnownLocation == null || currentSpeed <= 0.1f) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<Task> tasks = taskDao.getTasksWithLocationAndDate(today);

        for (Task task : tasks) {
            if (task.locationId <= 0 || notifiedLocationTaskIds.contains(task.id)) continue;

            SavedLocations saved = taskDao.getSavedLocationById(task.locationId);
            Location target = new Location("");
            target.setLatitude(saved.getLatitude());
            target.setLongitude(saved.getLongitude());

            long etaMillis = calculateEtaMillis(lastKnownLocation, target, currentSpeed);

            if (etaMillis <= 0 || etaMillis <= 15_000) continue;

            scheduleTaskCheck(task.id, etaMillis - 15_000);
        }

        stopTracking();
    }

    @SuppressLint("ScheduleExactAlarm")
    public void scheduleTaskCheck(int taskId, long delayMillis) {
        long triggerTime = System.currentTimeMillis() + Math.max(delayMillis, MIN_RECHECK_BUFFER_MS);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, GPSWakeupReceiver.class);
        intent.putExtra("taskId", taskId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, taskId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String wakeTime = sdf.format(new Date(triggerTime));

        Log.d(TAG, "Scheduled GPS wakeup for task ID " + taskId + " at " + wakeTime);
        Log.d(TAG, "⏰ ETA (ms): " + delayMillis + " | Scheduled wake-up in: " + (Math.max(delayMillis, MIN_RECHECK_BUFFER_MS)) + " ms");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.addtask)
                .setContentTitle("GPS Wakeup Scheduled")
                .setContentText("Task " + taskId + " at " + wakeTime)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(taskId + 10000, builder.build());
        }
    }

    public void onMotionDetected() {
        if (!isTracking()) {
            Log.d(TAG, "Motion detected → starting GPS");
            startTracking();
        }

        long now = System.currentTimeMillis();
        if (now - lastNotificationTime > NOTIFICATION_COOLDOWN_MS) {
            lastNotificationTime = now;
            showSpeedNotification(currentSpeed);
        }
    }

    private void showSpeedNotification(float speed) {
        String speedText = String.format("Current speed: %.2f m/s", speed);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.addtask)
                .setContentTitle("Speed Update")
                .setContentText(speedText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GPS Speed Notifications",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public boolean isTracking() {
        return locationCallback != null;
    }

    public float getCurrentSpeed() {
        return currentSpeed;
    }

    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }

    private void calculateSpeed(Location newLocation) {
        if (lastLocation != null) {
            float rawSpeed = newLocation.getSpeed();
            float computedSpeed;

            if (rawSpeed > 0 && !Float.isNaN(rawSpeed) && !Float.isInfinite(rawSpeed)) {
                computedSpeed = rawSpeed;
            } else if (speedBufferCount > 0) {
                computedSpeed = speedBuffer[(speedBufferIndex - 1 + SPEED_SMOOTHING_WINDOW) % SPEED_SMOOTHING_WINDOW];
                Log.d(TAG, "Fallback to last known speed sample = " + computedSpeed);
            } else {
                computedSpeed = 1.0f;
            }

            // Update speed buffer (rolling window)
            speedBuffer[speedBufferIndex] = computedSpeed;
            speedBufferIndex = (speedBufferIndex + 1) % SPEED_SMOOTHING_WINDOW;
            if (speedBufferCount < SPEED_SMOOTHING_WINDOW) speedBufferCount++;

            // Compute smoothed average and min
            float sum = 0f;
            float min = Float.MAX_VALUE;
            for (int i = 0; i < speedBufferCount; i++) {
                sum += speedBuffer[i];
                if (speedBuffer[i] < min) min = speedBuffer[i];
            }

            averageSpeed = sum / speedBufferCount;
            lowerSpeedBound = min;
            currentSpeed = computedSpeed;

            // Only set final smoothed if window is full
            if (speedBufferCount == SPEED_SMOOTHING_WINDOW) {
                finalSmoothedSpeed = averageSpeed;
                Log.d(TAG, "Final smoothed speed updated: " + finalSmoothedSpeed + " m/s");
            }

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
            String time = sdf.format(new Date(newLocation.getTime()));
            Log.d(TAG, "---- GPS SAMPLE [" + time + "] ----");
            Log.d(TAG, "Raw speed from getSpeed(): " + rawSpeed + " m/s");
            Log.d(TAG, "Speed Window: " + Arrays.toString(speedBuffer));
            Log.d(TAG, "Smoothed Speed: " + averageSpeed + " m/s");
            Log.d(TAG, "New Current Speed: " + currentSpeed + " m/s");
            Log.d(TAG, "Current Coordinates: Lat " + newLocation.getLatitude() + ", Lon " + newLocation.getLongitude());

            if (lastLocation != null) {
                Log.d(TAG, "Last Known Location: Lat " + lastLocation.getLatitude() + ", Lon " + lastLocation.getLongitude());
            }
        }

        lastLocation = newLocation;
        if (shouldKeepTrackingWithin10s()) {
            scheduleShortTrackingFallbackIfNeeded();
        } else {
            gpsHandler.removeCallbacks(stopTrackingRunnable);
            scheduleNextGpsCheckToLocation();
        }
    }

    private long calculateEtaMillis(Location from, Location to, float ignored) {
        float effectiveSpeed = lowerSpeedBound;
        if (effectiveSpeed < 0.1f) return 0;

        float distance = from.distanceTo(to) - PROXIMITY_RADIUS;
        return (long) ((Math.max(distance, 0) / effectiveSpeed) * 1000);
    }

    public void stopTracking() {
        // NEW: Also stop continuous tracking if running
        if (isContinuousTracking) {
            stopContinuousTracking();
        }

        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
            Log.d(TAG, "GPS tracking stopped");
        }

        if (lastKnownLocation != null || locationHistoryHasValidEntries()) {
            Location start = resolveFallbackStartLocation();
            Location end = resolveFallbackEndLocation();

            if (start != null && end != null) {
                long endTimeMs = System.currentTimeMillis();
                float distance = start.distanceTo(end);
                float timeSec = (endTimeMs - gpsStartTimeMs) / 1000f;

                float directSpeed = timeSec > 0 ? distance / timeSec : 0f;

                float finalSpeed;
                if (finalSmoothedSpeed > 1.0 && ((finalSmoothedSpeed-directSpeed)<1)) {
                    finalSpeed = (finalSmoothedSpeed + directSpeed) / 2f;
                } else {
                    finalSpeed = directSpeed;
                }

                currentSpeed = finalSpeed;
                Log.d(TAG, "---- GPS SESSION END ----");
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
                String endTime = sdf.format(new Date(endTimeMs));

                Log.d(TAG, "Session End Time: " + endTime);
                Log.d(TAG, "Start Location: " + formatLocation(start));
                Log.d(TAG, "End Location: " + formatLocation(end));
                Log.d(TAG, "Distance: " + distance + " meters");
                Log.d(TAG, "Elapsed Time: " + timeSec + " sec");
                Log.d(TAG, "Direct Speed: " + directSpeed + " m/s");
                Log.d(TAG, "Smoothed Speed: " + finalSmoothedSpeed + " m/s");
                Log.d(TAG, "✅ Final Averaged Speed = " + currentSpeed + " m/s");

                scheduleNextGpsCheckToLocation();
            }
        }

        // Reset state
        forceTracking = false;
        lastLocation = null;
        lastKnownLocation = null;
        gpsStartLocation = null;
        gpsStartTimeMs = 0;
        finalSmoothedSpeed = 0f;

        speedBufferIndex = 0;
        speedBufferCount = 0;
        averageSpeed = 0f;
        lowerSpeedBound = 0f;
    }

    private void scheduleShortTrackingFallbackIfNeeded() {
        Log.d(TAG, "ETA < 10s → keeping GPS for short window (10s max)");

        gpsHandler.removeCallbacks(stopTrackingRunnable);

        stopTrackingRunnable = () -> {
            Log.d(TAG, "Timeout hit — user did not reach task. Re-scheduling GPS...");
            scheduleNextGpsCheckToLocation();
        };

        gpsHandler.postDelayed(stopTrackingRunnable, 15_000);
    }

    private Location resolveFallbackStartLocation() {
        if (gpsStartLocation != null) return gpsStartLocation;

        for (int i = 0; i < MAX_LOCATION_HISTORY; i++) {
            int index = (locationHistoryIndex + i) % MAX_LOCATION_HISTORY;
            if (locationHistory[index] != null) return locationHistory[index];
        }
        return null;
    }

    private Location resolveFallbackEndLocation() {
        if (lastKnownLocation != null) return lastKnownLocation;

        for (int i = 1; i <= MAX_LOCATION_HISTORY; i++) {
            int index = (locationHistoryIndex - i + MAX_LOCATION_HISTORY) % MAX_LOCATION_HISTORY;
            if (locationHistory[index] != null) return locationHistory[index];
        }
        return null;
    }

    private boolean locationHistoryHasValidEntries() {
        for (Location loc : locationHistory) {
            if (loc != null) return true;
        }
        return false;
    }

    private String formatLocation(Location loc) {
        return loc != null ? "Lat " + loc.getLatitude() + ", Lon " + loc.getLongitude() : "null";
    }
}