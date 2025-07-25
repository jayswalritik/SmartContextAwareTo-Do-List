package com.example.smartto_do_list.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDatabase;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);

        if (event == null || event.hasError()) {
            Log.e("GeofenceReceiver", "Error: " + (event != null ? event.getErrorCode() : "null event"));
            return;
        }

        int transition = event.getGeofenceTransition();

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            for (Geofence geofence : event.getTriggeringGeofences()) {
                String taskId = geofence.getRequestId();
                try {
                    TaskDatabase db = TaskDatabase.getInstance(context);
                    Task task = db.taskDao().getTaskById(Integer.parseInt(taskId));

                    if (task != null) {
                        NotificationScheduler.scheduleImmediateNotification(context, task);
                        Log.d("GeofenceReceiver", "Geofence entered for task: " + task.title);


                        // ❌ Remove or comment this line
                        // db.taskDao().markLocationNotified(task.id);
                    } else {
                        Log.e("GeofenceReceiver", "Task not found for ID: " + taskId);
                    }
                } catch (Exception e) {
                    Log.e("GeofenceReceiver", "Exception handling geofence ID: " + taskId, e);
                }
            }
        } else {
            Log.d("GeofenceReceiver", "Geofence transition not enter: " + transition);
        }
    }
}
