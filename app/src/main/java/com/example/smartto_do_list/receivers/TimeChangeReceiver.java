package com.example.smartto_do_list.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.WorkManager;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDatabase;
import com.example.smartto_do_list.TaskNotificationsReceiver;
import com.example.smartto_do_list.utils.TaskUtils;

import java.util.List;

public class TimeChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {

            Log.d("TimeChangeReceiver", "\uD83D\uDD53 Device time changed — checking for most recent due task");

            TaskDatabase db = TaskDatabase.getInstance(context);
            new Thread(() -> {
                List<Task> allTasks = db.taskDao().getAllTasks();
                long now = System.currentTimeMillis();

                Task latestDueTask = null;
                long latestTime = Long.MIN_VALUE;

                for (Task task : allTasks) {
                    long taskMillis = TaskUtils.getNextScheduledTimeMillis(task);
                    if (taskMillis <= now + 1500 && taskMillis > latestTime) {
                        latestDueTask = task;
                        latestTime = taskMillis;
                    }
                }

                if (latestDueTask != null) {
                    Log.d("TimeChangeReceiver", "✅ Firing most recent due task: " + latestDueTask.getTitle());

                    // Cancel any existing alarm and repeat worker
                    NotificationScheduler.cancelTaskNotification(context, latestDueTask.getId());
                    WorkManager.getInstance(context).cancelUniqueWork("DynamicRepeatUpdater");

                    // Fire notification immediately
                    NotificationScheduler.scheduleImmediateNotification(context, latestDueTask);

                    // Update repeat schedule if needed
                    if (latestDueTask.getRepeat() != null && !latestDueTask.getRepeat().isEmpty()) {
                        TaskUtils utils = new TaskUtils(context, db);
                        utils.handleRepeatingTask(latestDueTask);
                    }
                } else {
                    Log.d("TimeChangeReceiver", "ℹ️ No task is due at this new time");
                }
            }).start();
        }
    }
}
