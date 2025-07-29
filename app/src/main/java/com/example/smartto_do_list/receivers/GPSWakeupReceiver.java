package com.example.smartto_do_list.receivers;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.smartto_do_list.R;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDao;
import com.example.smartto_do_list.TaskDatabase;
import com.example.smartto_do_list.services.GPSTracker;
import com.example.smartto_do_list.SavedLocations;
import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.services.LocationNotificationManager;
import com.google.android.gms.location.*;

// ... package and imports unchanged

public class GPSWakeupReceiver extends BroadcastReceiver {
    private static final String TAG = "GPSWakeupReceiver";
    private static final int MAX_RETRY_COUNT = 20;

    @Override
    public void onReceive(Context context, Intent intent) {
        int taskId = intent.getIntExtra("taskId", -1);
        if (taskId == -1) return;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "SmartToDo:GPSWakeLock");
        wakeLock.acquire(30 * 1000L); // hold for 30 seconds max

        GPSTracker tracker = new GPSTracker(context);

        // NEW: Check if we're already in continuous tracking mode
        if (tracker.isContinuousTracking()) {
            Log.d(TAG, "Already in continuous tracking mode, ignoring wakeup for task " + taskId);
            wakeLock.release();
            return;
        }

        TaskDao taskDao = TaskDatabase.getInstance(context).taskDao();
        Task task = taskDao.getTaskById(taskId);
        if (task == null || task.locationId <= 0) {
            wakeLock.release();
            return;
        }

        SavedLocations saved = taskDao.getSavedLocationById(task.locationId);
        Location target = new Location("");
        target.setLatitude(saved.getLatitude());
        target.setLongitude(saved.getLongitude());

        FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Cannot proceed with GPS wakeup.");
            wakeLock.release();
            return;
        }

        try {
            LocationRequest request = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setNumUpdates(1)
                    .setInterval(0);

            fusedClient.requestLocationUpdates(request, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    try {
                        fusedClient.removeLocationUpdates(this);
                    } catch (SecurityException e) {
                        Log.w(TAG, "removeLocationUpdates SecurityException", e);
                    }

                    Location location = locationResult != null ? locationResult.getLastLocation() : null;

                    if (location == null) {
                        Log.d(TAG, "Real-time location null, falling back to last known location...");
                        fallbackToLastKnownLocation(context, fusedClient, tracker, target, task, taskId, wakeLock);
                    } else {
                        handleLocation(context, location, target, tracker, task, taskId, wakeLock);
                    }
                }
            }, Looper.getMainLooper());

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Location permission likely missing.", e);
            wakeLock.release();
        }
    }

    private void fallbackToLastKnownLocation(Context context, FusedLocationProviderClient fusedClient,
                                             GPSTracker tracker, Location target, Task task,
                                             int taskId, PowerManager.WakeLock wakeLock) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted during fallback.");
            wakeLock.release();
            return;
        }

        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                Log.d(TAG, "Using fallback last known location.");
                handleLocation(context, location, target, tracker, task, taskId, wakeLock);
            } else {
                Log.d(TAG, "No fallback location available.");
                wakeLock.release();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get last known location", e);
            wakeLock.release();
        });
    }

    private void handleLocation(Context context, Location location, Location target,
                                GPSTracker tracker, Task task, int taskId,
                                PowerManager.WakeLock wakeLock) {

        float distance = location.distanceTo(target);
        Log.d(TAG, "Distance to task " + taskId + ": " + distance + " meters");

        if (distance <= 100) {
            Log.d(TAG, "Within 100m → Firing notification for taskId=" + taskId);

            if (LocationNotificationManager.getInstance(context).shouldSendNotification(taskId)) {
                NotificationScheduler.scheduleImmediateNotification(context, task);
            }

            tracker.notifiedLocationTaskIds.add(taskId);
            wakeLock.release();
            return;
        }

        // NEW: Calculate ETA and determine tracking strategy
        float speed = location.getSpeed();
        if (speed < 0.1f) speed = 1.5f; // fallback speed

        float remainingDistance = Math.max(distance - 100, 0);
        long etaMillis = (long) ((remainingDistance / speed) * 1000);

        Log.d(TAG, "ETA for task " + taskId + ": " + etaMillis + "ms");

        if (etaMillis > 0 && etaMillis <= 15_000) {
            // NEW: ETA < 10 seconds - Start continuous tracking
            Log.d(TAG, "ETA < 10s → Starting continuous tracking for task " + taskId);

            // Set current speed in tracker for ETA calculations
            tracker.setCurrentSpeed(speed);
            tracker.setLastKnownLocation(location);

            // Start continuous tracking
            tracker.startContinuousTrackingForTask(taskId);

        } else if (etaMillis <= 5_000) {
            // Very close - schedule immediate recheck
            tracker.scheduleTaskCheck(taskId, 2000);
        } else {
            // Normal scheduling - recheck ~10 seconds before arrival
            tracker.scheduleTaskCheck(taskId, etaMillis - 15_000);
        }

        if (distance > 120) {
            Log.d(TAG, "User moved beyond 120m radius for task " + taskId + ", resetting notification flags.");
            LocationNotificationManager.getInstance(context).resetNotification(taskId);
            tracker.notifiedLocationTaskIds.remove(taskId);
        }

        showWakeupNotification(context, taskId);
        wakeLock.release();
    }

    private void showWakeupNotification(Context context, int taskId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "gps_speed_channel", "GPS Speed Notifications",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "gps_speed_channel")
                .setSmallIcon(R.drawable.addtask)
                .setContentTitle("GPS Wakeup Triggered")
                .setContentText("Scheduled GPS check for task ID " + taskId)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(taskId + 20000, builder.build());
        }
    }
}
