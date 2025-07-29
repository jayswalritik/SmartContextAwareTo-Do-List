package com.example.smartto_do_list.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.smartto_do_list.R;
import com.example.smartto_do_list.TaskDao;
import com.example.smartto_do_list.TaskDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MotionDetectionService extends Service implements SensorEventListener {

    private static final String TAG = "MotionDetectionService";
    private static final String CHANNEL_ID = "motion_service_channel";

    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private Sensor stepDetectorSensor;

    private static final int SMOOTHING_WINDOW_SIZE = 5;
    private final double[] accelBuffer = new double[SMOOTHING_WINDOW_SIZE];
    private int bufferIndex = 0;
    private int bufferCount = 0;

    private static final long COOLDOWN_MS = 7000;
    private long lastMotionTimestamp = 0;

    private double baseAcceleration = -1;
    private double lowerBound;
    private double upperBound;
    private static final double MIN_BUFFER = 0.3;

    private static final double REST_ACCEL_THRESHOLD = 0.3;
    private static final double SMOOTH_MOTION_THRESHOLD = 1;
    private static final double JERK_THRESHOLD = 10.0;

    private boolean wasIdle = true;
    private boolean motionDetectionAllowed = false;
    private boolean hasFirstMotionBeenHandled = false;

    private TaskDao taskDao;
    private GPSTracker gpsTracker;
    private boolean shouldActivateGPS = false;

    private long motionStartTime = 0;
    private long restStartTime = 0;
    private static final long MOTION_CONFIRMATION_MS = 2000;
    private static final long REST_CONFIRMATION_MS = 5000;

    private long bufferBreachStartTime = 0;
    private static final long BASE_UPDATE_DELAY_MS = 0; // 3 seconds delay

    private static final float ACCELERATION_THRESHOLD = 2.5f; // example threshold
    private static final long GPS_START_DELAY_MS = 2000; // 2 seconds
    private long accelerationAboveThresholdStartTime = -1;
    private boolean gpsTrackingStarted = false;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        TaskDatabase db = TaskDatabase.getInstance(getApplicationContext());
        taskDao = db.taskDao();

        gpsTracker = new GPSTracker(this);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        if (linearAccelerationSensor != null) {
            sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }


        createNotificationChannel();
        startForeground(1, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Motion Detection Running")
                .setContentText("Monitoring physical activity")
                .setSmallIcon(R.drawable.addtask)
                .build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        if (gpsTracker != null) {
            gpsTracker.stopTracking();
            Log.d(TAG, "GPS tracking stopped because service destroyed.");
        }
        Log.d(TAG, "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            Log.d(TAG, "Step detected.");
            handleStepDetection();
            return;
        }

        if (event.sensor.getType() != Sensor.TYPE_LINEAR_ACCELERATION) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double currentAccel = Math.sqrt(x * x + y * y + z * z);

        accelBuffer[bufferIndex] = currentAccel;
        bufferIndex = (bufferIndex + 1) % SMOOTHING_WINDOW_SIZE;
        if (bufferCount < SMOOTHING_WINDOW_SIZE) bufferCount++;

        double smoothedAccel = 0;
        for (int i = 0; i < bufferCount; i++) {
            smoothedAccel += accelBuffer[i];
        }
        smoothedAccel /= bufferCount;

        // --- Idle Detection ---
        if (smoothedAccel < REST_ACCEL_THRESHOLD) {
            motionStartTime = 0; // Reset motion timer

            if (restStartTime == 0) {
                restStartTime = System.currentTimeMillis();
            } else if ((System.currentTimeMillis() - restStartTime) >= REST_CONFIRMATION_MS) {
                if (!wasIdle) {
                    Log.d(TAG, "Device confirmed at rest. Stopping GPS.");
                    if (gpsTracker != null) {
                        gpsTracker.stopTracking();
                        Log.d(TAG, "GPS tracking stopped after rest detected.");
                    }
                }
                wasIdle = true;
                hasFirstMotionBeenHandled = false;
                motionDetectionAllowed = false;
            }

            // Reset acceleration breach timer while idle
            accelerationAboveThresholdStartTime = -1;
            return;
        } else {
            restStartTime = 0;
        }

        // --- Motion Detection ---
        if (smoothedAccel >= REST_ACCEL_THRESHOLD) {
            //Log.d(TAG, "Smoothed acceleration: " + String.format("%.2f", smoothedAccel));

            if (motionStartTime == 0) {
                motionStartTime = System.currentTimeMillis();
            } else if ((System.currentTimeMillis() - motionStartTime) >= MOTION_CONFIRMATION_MS) {
                if (!hasFirstMotionBeenHandled && shouldContinueMotionDetection()) {
                    showMotionNotification("First motion after rest", smoothedAccel);

                    motionDetectionAllowed = true;
                    hasFirstMotionBeenHandled = true;
                    wasIdle = false;

                    if (gpsTracker != null && !gpsTracker.isTracking()) {
                        gpsTracker.startTracking();
                        Log.d(TAG, "GPS tracking started after motion.");
                    }
                }
            }
        } else {
            motionStartTime = 0;
        }

        if (!motionDetectionAllowed) return;

        // Proceed with regular motion detection
        wasIdle = false;

        if (baseAcceleration == -1) {
            setBufferAndBase(smoothedAccel);
            return;
        }

        boolean accelOutsideBuffer = smoothedAccel < lowerBound || smoothedAccel > upperBound;

        // --- New GPS Start Delay Logic (3s sustained breach) ---
        if (accelOutsideBuffer) {
            if (accelerationAboveThresholdStartTime == -1) {
                accelerationAboveThresholdStartTime = System.currentTimeMillis();
                //Log.d(TAG, "Acceleration breach detected. Starting 2-second timer.");
            } else {
                long duration = System.currentTimeMillis() - accelerationAboveThresholdStartTime;
                if (duration >= GPS_START_DELAY_MS && gpsTracker != null && !gpsTracker.isTracking()) {
                    gpsTracker.startTracking();
                    gpsTrackingStarted = true;
                    //Log.d(TAG, "Acceleration sustained beyond buffer for 2 seconds. GPS started.");

                    // Optionally update base acceleration
                    setBufferAndBase(smoothedAccel);

                    // Show motion notification if cooldown has passed
                    long now = System.currentTimeMillis();
                    if (now - lastMotionTimestamp >= COOLDOWN_MS) {
                        lastMotionTimestamp = now;
                        String type = smoothedAccel >= JERK_THRESHOLD ? "sudden jerk"
                                : smoothedAccel >= SMOOTH_MOTION_THRESHOLD ? "smooth motion"
                                : "minor movement";
                        //showMotionNotification(type, smoothedAccel);

                        if (gpsTracker != null) {
                            gpsTracker.onMotionDetected();
                        }
                    }
                }
            }
        } else {
            if (accelerationAboveThresholdStartTime != -1) {
                //Log.d(TAG, "Acceleration back within buffer. Timer reset.");
            }
            accelerationAboveThresholdStartTime = -1;
        }
    }

    private void handleStepDetection() {
        if (wasIdle && shouldContinueMotionDetection()) {
            wasIdle = false;
            hasFirstMotionBeenHandled = true;
            motionDetectionAllowed = true;

            //showMotionNotification("Step detected after idle", -1);

            if (gpsTracker != null && !gpsTracker.isTracking()) {
                gpsTracker.startTracking();
                Log.d(TAG, "GPS tracking started after step.");
            }

        }
        if (!motionDetectionAllowed) return;

        long now = System.currentTimeMillis();
        if (now - lastMotionTimestamp >= COOLDOWN_MS) {
            lastMotionTimestamp = now;
            //showMotionNotification("Walking (step detected)", -1);

            if (gpsTracker != null) {
                gpsTracker.onMotionDetected();
                Log.d(TAG, "Motion detected: walking (step detected)");
            }
        }
    }

    private void setBufferAndBase(double newBase) {
        baseAcceleration = newBase;
        double buffer = Math.max(newBase / 5.0, MIN_BUFFER);
        lowerBound = baseAcceleration - buffer;
        upperBound = baseAcceleration + buffer;
        Log.d(TAG, "Buffer and base set: base=" + String.format("%.2f", baseAcceleration)
                + ", lowerBound=" + String.format("%.2f", lowerBound)
                + ", upperBound=" + String.format("%.2f", upperBound));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Motion Detection Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void showMotionNotification(String motionType, double accel) {
        String message = (accel == -1)
                ? "Activity: " + motionType
                : "Detected " + motionType + " with acceleration " + String.format("%.2f", accel) + " m/s²";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.addtask)
                .setContentTitle("Motion Event")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
        Log.d(TAG, "Motion notification shown: " + message);
    }

    private boolean shouldContinueMotionDetection() {
        try {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            int count = taskDao.getCountOfLocationTasksForToday(today);
            Log.d(TAG, "Tasks with location for today: " + count);
            return count > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking tasks", e);
            return false;
        }
    }
}
