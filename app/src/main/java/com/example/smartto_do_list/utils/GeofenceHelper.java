package com.example.smartto_do_list.utils;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.example.smartto_do_list.SavedLocations;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.receivers.GeofenceBroadcastReceiver;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

public class GeofenceHelper {
    private static final String TAG = "GeofenceHelper";
    private final Context context;
    private final GeofencingClient geofencingClient;

    public boolean geofenceEntered=false;

    public GeofenceHelper(Context context) {
        this.context = context.getApplicationContext();
        this.geofencingClient = LocationServices.getGeofencingClient(context);
    }

    public void registerGeofenceForTask(Task task, SavedLocations location) {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "❌ Permissions not granted — geofence not registered.");
            return;
        }

        try {
            Geofence geofence = new Geofence.Builder()
                    .setRequestId(String.valueOf(task.id))
                    .setCircularRegion(location.latitude, location.longitude, 100)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build();

            GeofencingRequest request = new GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofence(geofence)
                    .build();

            PendingIntent pendingIntent = getGeofencePendingIntent();

            // Remove existing geofence (if any) and add new one
            geofencingClient.removeGeofences(java.util.Collections.singletonList(String.valueOf(task.id)))
                    .addOnCompleteListener(task1 -> {
                        if (!hasLocationPermissions()) return; // double check again
                        try {
                            geofencingClient.addGeofences(request, pendingIntent)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Geofence added for task: " + task.title))
                                    .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to add geofence: " + e.getMessage()));
                        } catch (SecurityException se) {
                            Log.e(TAG, "❌ SecurityException while adding geofence: " + se.getMessage());
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException while removing geofence: " + e.getMessage());
        }
    }


    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE  // ✅ REQUIRED on Android 12+
        );
    }

    private boolean hasLocationPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void removeGeofenceForTask(Task task) {
        geofencingClient.removeGeofences(java.util.Collections.singletonList(String.valueOf(task.id)))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "🗑 Geofence removed for task: " + task.title))
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to remove geofence: " + e.getMessage()));
    }


}
