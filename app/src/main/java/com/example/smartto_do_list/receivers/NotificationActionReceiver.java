package com.example.smartto_do_list.receivers;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDatabase;
import com.example.smartto_do_list.TaskNotificationsReceiver;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int taskId = intent.getIntExtra("task_id", -1);
        String action = intent.getAction();

        if (taskId == -1 || action == null) return;

        if ("ACTION_MARK_COMPLETE".equals(action)) {
            TaskDatabase db = TaskDatabase.getInstance(context);
            new Thread(() -> {
                Task task = db.taskDao().getTaskById(taskId);
                if (task != null) {
                    task.setTaskStatus("completed");
                    db.taskDao().update(task);

                    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(taskId);         // Main task notification
                    manager.cancel(taskId + 5000);  // Reminder notification

                    TaskNotificationsReceiver.stopActiveRingtone(); // 🛑 Stop high-priority alarm sound

                    NotificationScheduler.cancelTaskNotification(context, taskId);
                    NotificationScheduler.cancelTaskReminder(context, taskId);

                }
            }).start();
        }
    }
}
