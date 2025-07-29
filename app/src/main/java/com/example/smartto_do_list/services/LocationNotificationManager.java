package com.example.smartto_do_list.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class LocationNotificationManager {
    private static final String TAG = "LocationNotificationManager";
    private static final String PREFS_NAME = "location_notifications";
    private static final String KEY_NOTIFIED_TASKS = "notified_task_ids";

    private static LocationNotificationManager instance;
    private final SharedPreferences prefs;

    private LocationNotificationManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized LocationNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new LocationNotificationManager(context);
        }
        return instance;
    }

    public synchronized boolean shouldSendNotification(int taskId) {
        Set<String> notifiedIds = prefs.getStringSet(KEY_NOTIFIED_TASKS, new HashSet<>());
        String taskIdStr = String.valueOf(taskId);

        if (notifiedIds.contains(taskIdStr)) {
            Log.d(TAG, "Notification already sent for task " + taskId);
            return false;
        }

        // Mark as notified
        Set<String> updatedIds = new HashSet<>(notifiedIds);
        updatedIds.add(taskIdStr);
        prefs.edit().putStringSet(KEY_NOTIFIED_TASKS, updatedIds).apply();

        Log.d(TAG, "Marking task " + taskId + " as notified");
        return true;
    }

    public synchronized void resetNotification(int taskId) {
        Set<String> notifiedIds = prefs.getStringSet(KEY_NOTIFIED_TASKS, new HashSet<>());
        String taskIdStr = String.valueOf(taskId);

        if (notifiedIds.contains(taskIdStr)) {
            Set<String> updatedIds = new HashSet<>(notifiedIds);
            updatedIds.remove(taskIdStr);
            prefs.edit().putStringSet(KEY_NOTIFIED_TASKS, updatedIds).apply();
            Log.d(TAG, "Reset notification eligibility for task " + taskId);
        }
    }
}
