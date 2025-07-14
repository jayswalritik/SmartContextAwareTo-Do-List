package com.example.smartto_do_list;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.smartto_do_list.NotificationLogDao;
import com.example.smartto_do_list.utils.TaskUtils;


public class TaskNotificationsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String taskTitle = intent.getStringExtra("task_title");
        int taskId = intent.getIntExtra("task_id", 0);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 🔊 Custom sound URI
        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_app_notification_sound);

        // 🔧 Android 8+ (Oreo and above) → configure channel with custom sound
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel channel = new NotificationChannel(
                    "task_channel",
                    "Task Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setSound(soundUri, audioAttributes);
            manager.createNotificationChannel(channel);
        }

        // 🔔 Notification builder (applies to all versions)
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "task_channel")
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle("Task Reminder")
                .setContentText(taskTitle)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri); // 🔉 Also works for pre-Oreo

        manager.notify(taskId, builder.build());

        // ✅ Repeat & log logic
        TaskDatabase db = TaskDatabase.getInstance(context);
        new Thread(() -> {
            Task task = db.taskDao().getTaskById(taskId);
            if (task != null && task.getRepeat() != null && !task.getRepeat().isEmpty()) {
                TaskUtils utils = new TaskUtils(context, db);
                utils.handleRepeatingTask(task);
            }

            NotificationLog log = new NotificationLog(taskId, taskTitle, System.currentTimeMillis());
            db.notificationLogDao().insert(log);
        }).start();
    }


}
