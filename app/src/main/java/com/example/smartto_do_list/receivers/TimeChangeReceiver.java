package com.example.smartto_do_list.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDatabase;
import com.example.smartto_do_list.utils.TaskUtils;

import java.util.List;

public class TimeChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {

            Log.d("TimeChangeReceiver", "Device time changed - checking tasks");

            TaskDatabase db = TaskDatabase.getInstance(context);
            new Thread(() -> {
                List<Task> allTasks = db.taskDao().getAllTasks();
                long now = System.currentTimeMillis();

                for (Task task : allTasks) {
                    long scheduledMillis = TaskUtils.getNextScheduledTimeMillis(task);
                    if (scheduledMillis != Long.MAX_VALUE && scheduledMillis <= now) {
                        Log.d("TimeChangeReceiver", "Firing notification immediately for task: " + task.getTitle());
                        // Cancel any existing alarm
                        NotificationScheduler.cancelTaskNotification(context, task.getId());
                        // Schedule immediately (now + small delay, e.g. 1 second)
                        NotificationScheduler.scheduleImmediateNotification(context, task);
                    }
                }
            }).start();
        }
    }
}
