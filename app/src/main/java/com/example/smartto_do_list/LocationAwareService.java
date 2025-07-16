// LocationAwareService.java — Smart Foreground GPS + Accelerometer Tracker (with Task-SavedLocation relation + permission check)

package com.example.smartto_do_list;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationAwareService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private FusedLocationProviderClient fusedClient;
    private Handler handler;
    private TaskDatabase db;

    private static final float STATIONARY_THRESHOLD = 0.3f;
    private boolean isMoving = false;
    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes
    private final Map<Integer, Long> lastTriggeredMap = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        db = TaskDatabase.getInstance(this);
        handler = new Handler(Looper.getMainLooper());

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);

        startForeground(1, buildForegroundNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            List<TaskRelationWithSavedLocationsAndNote> taskRelations = db.taskDao().getActiveLocationTaskRelations();
            for (TaskRelationWithSavedLocationsAndNote relation : taskRelations) {
                monitorTaskETA(relation.task, relation.locations);
            }
        }).start();
        return START_STICKY;
    }

    private void monitorTaskETA(Task task, SavedLocations locationTarget) {
        handler.post(() -> {
            if (!isMoving) return;

            Location userLocation = null;
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                userLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

            if (userLocation == null) {
                fusedClient.getLastLocation().addOnSuccessListener(loc -> {
                    if (loc != null) handleLocation(task, locationTarget, loc);
                });
                return;
            }

            handleLocation(task, locationTarget, userLocation);
        });
    }

    private void handleLocation(Task task, SavedLocations locationTarget, Location userLocation) {
        float[] result = new float[1];
        Location.distanceBetween(
                userLocation.getLatitude(), userLocation.getLongitude(),
                locationTarget.latitude, locationTarget.longitude,
                result
        );

        float distance = result[0];
        double speed = Math.max(userLocation.getSpeed(), 0.3); // fallback default
        double speedKmh = speed * 3.6;

        double vMax = speedKmh * 1.2;
        double etaSec = distance / (vMax / 3.6);

        long delayMs = calculatePollingInterval(etaSec);

        if (distance <= 15) {
            long now = System.currentTimeMillis();
            long lastTime = lastTriggeredMap.getOrDefault(task.getId(), 0L);
            if (now - lastTime < COOLDOWN_MS) return; // cooldown active

            NotificationScheduler.scheduleImmediateNotification(this, task);
            db.taskDao().markLocationNotified(task.getId());
            lastTriggeredMap.put(task.getId(), now);
            return;
        }

        handler.postDelayed(() -> monitorTaskETA(task, locationTarget), delayMs);
    }

    private long calculatePollingInterval(double etaSec) {
        if (etaSec < 120) return 20_000;           // < 2 min → every 20 sec
        else if (etaSec < 600) return 90_000;      // < 10 min → every 1.5 min
        else if (etaSec < 3600) return 5 * 60_000; // < 1 hr → every 5 min
        else return 15 * 60_000;                   // > 1 hr → every 15 min
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double acceleration = Math.sqrt(x * x + y * y + z * z);

        isMoving = acceleration > STATIONARY_THRESHOLD + 9.8f; // approx gravity baseline
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, "location_channel")
                .setContentTitle("Monitoring Location Tasks")
                .setContentText("Your smart to-do list is watching for nearby reminders.")
                .setSmallIcon(R.drawable.locationicon)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);
    }
}
