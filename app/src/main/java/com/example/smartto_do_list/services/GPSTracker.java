package com.example.smartto_do_list.services;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.R;
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
    private static final long NOTIFICATION_COOLDOWN_MS = 5000;

    private TaskDao taskDao;
    private final Handler gpsHandler = new Handler(Looper.getMainLooper());
    private Runnable scheduledCheckRunnable;
    private Location lastKnownLocation;

    private final Set<Integer> notifiedLocationTaskIds = new HashSet<>();

    private static final long MIN_RECHECK_BUFFER_MS = 5000;
    private static final float PROXIMITY_RADIUS = 100;
    private static final float EXIT_RADIUS = 120;

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

        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(2000)
                .setFastestInterval(1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    calculateSpeed(location);
                    lastKnownLocation = location;
                    checkProximityToSavedLocations(location);
                    resetNotificationIfExited(location); // new
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    public void stopTracking() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
            Log.d(TAG, "GPS tracking stopped");
        }
        lastLocation = null;
        lastKnownLocation = null;
        currentSpeed = 0f;
    }

    private void resetNotificationIfExited(Location current) {
        // Reset notification flag if user moved out of EXIT_RADIUS
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
                //notifyNearLocation(task);
                NotificationScheduler.scheduleImmediateNotification(context, task);
                Log.d(TAG, "GPS location notifaction");
                stopTracking(); // prevent further updates
                notifiedLocationTaskIds.add(task.id);
                break;
            }
        }
    }

    private void notifyNearLocation(Task task) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.addtask)
                .setContentTitle("You're near your task location!")
                .setContentText("Task: " + task.getTitle())
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(task.getId(), builder.build());
            Log.d(TAG, "Proximity alert notification sent for task: " + task.getTitle());
        }
    }

    private void calculateSpeed(Location newLocation) {
        if (lastLocation != null) {
            float speed = newLocation.getSpeed();

            if (speed == 0) {
                long timeDelta = newLocation.getTime() - lastLocation.getTime();
                if (timeDelta > 0) {
                    float distance = lastLocation.distanceTo(newLocation);
                    speed = distance / (timeDelta / 1000f);
                }
            }

            currentSpeed = speed;
        }

        lastLocation = newLocation;

        if (!shouldKeepTrackingWithin10s()) {
            scheduleNextGpsCheckToLocation(); // respects notification state
        }
    }

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
            if (eta > 0 && eta <= 10_000) return true;
        }

        return false;
    }

    private void scheduleNextGpsCheckToLocation() {
        if (lastKnownLocation == null || currentSpeed <= 0.1f) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<Task> tasks = taskDao.getTasksWithLocationAndDate(today);

        Task nearestTask = null;
        SavedLocations nearestSaved = null;
        float minDistance = Float.MAX_VALUE;

        for (Task task : tasks) {
            if (task.locationId <= 0 || notifiedLocationTaskIds.contains(task.id)) continue;

            SavedLocations saved = taskDao.getSavedLocationById(task.locationId);
            Location target = new Location("");
            target.setLatitude(saved.getLatitude());
            target.setLongitude(saved.getLongitude());

            float distance = lastKnownLocation.distanceTo(target);
            if (distance < minDistance) {
                minDistance = distance;
                nearestTask = task;
                nearestSaved = saved;
            }
        }

        if (nearestTask == null) return;

        Location nearestLocation = new Location("");
        nearestLocation.setLatitude(nearestSaved.getLatitude());
        nearestLocation.setLongitude(nearestSaved.getLongitude());

        long etaMillis = calculateEtaMillis(lastKnownLocation, nearestLocation, currentSpeed);

        if (etaMillis <= 0 || etaMillis <= 10_000) return;

        long wakeupDelay = Math.max(etaMillis - 10_000, MIN_RECHECK_BUFFER_MS);

        if (scheduledCheckRunnable != null)
            gpsHandler.removeCallbacks(scheduledCheckRunnable);

        scheduledCheckRunnable = () -> {
            Log.d(TAG, "Scheduled GPS recheck triggered.");
            startTracking();
        };

        stopTracking();
        gpsHandler.postDelayed(scheduledCheckRunnable, wakeupDelay);
    }

    private long calculateEtaMillis(Location from, Location to, float speed) {
        if (speed < 0.1f) return -1;
        float distance = from.distanceTo(to) - PROXIMITY_RADIUS;
        return (long) ((Math.max(distance, 0) / speed) * 1000);
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
}
