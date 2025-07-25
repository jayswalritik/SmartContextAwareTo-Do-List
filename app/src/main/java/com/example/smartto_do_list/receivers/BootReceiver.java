package com.example.smartto_do_list.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDatabase;
import com.example.smartto_do_list.services.MotionDetectionService;
import com.example.smartto_do_list.workers.TaskRepeatWorker;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Restart MotionDetectionService
            Intent serviceIntent = new Intent(context, MotionDetectionService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // Restore scheduled notifications
            TaskDatabase db = TaskDatabase.getInstance(context);

            new Thread(() -> {
                List<Task> allTasks = db.taskDao().getAllTasks();
                for (Task task : allTasks) {
                    NotificationScheduler.scheduleTaskNotification(context, task);
                }
                // Schedule repeat worker
                TaskRepeatWorker.scheduleNextRepeatWorker(context, 1);
            }).start();
        }
    }

}
