package com.example.smartto_do_list;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.smartto_do_list.receivers.NotificationActionReceiver;
import com.example.smartto_do_list.utils.TaskUtils;

public class TaskNotificationsReceiver extends BroadcastReceiver {
    private static Ringtone activeRingtone;  // 🔊 Store ringtone for stop later

    public static void stopActiveRingtone() {
        if (activeRingtone != null && activeRingtone.isPlaying()) {
            activeRingtone.stop();
            activeRingtone = null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String rawTitle = intent.getStringExtra("task_title");
        int taskId = intent.getIntExtra("task_id", 0);

        boolean isReminder = rawTitle != null && rawTitle.startsWith("[Reminder] ");
        String taskTitle = isReminder ? rawTitle.replace("[Reminder] ", "") : rawTitle;

        String contentTitle = isReminder ? "Upcoming Task Reminder" : "Task Reminder";
        int notificationId = isReminder ? taskId + 5000 : taskId;

        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_app_notification_sound);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        TaskDatabase db = TaskDatabase.getInstance(context);
        new Thread(() -> {
            Task task = db.taskDao().getTaskById(taskId);
            if (task == null) return;

            String priority = task.getPriority() != null ? task.getPriority().toLowerCase() : "low";

            String channelId = "task_channel_low";
            String channelName = "Low Priority Tasks";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            long[] vibrationPattern = new long[]{0L};
            boolean persistent = false;

            switch (priority) {
                case "medium":
                    channelId = "task_channel_medium";
                    channelName = "Medium Priority Tasks";
                    importance = NotificationManager.IMPORTANCE_HIGH;
                    vibrationPattern = new long[]{0, 250, 250, 250};
                    break;
                case "high":
                    channelId = "task_channel_high";
                    channelName = "High Priority Tasks";
                    importance = NotificationManager.IMPORTANCE_MAX;
                    vibrationPattern = new long[]{0, 500, 500, 500};
                    persistent = true;
                    break;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

                NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
                channel.setSound(soundUri, audioAttributes);
                channel.enableVibration(true);
                channel.setVibrationPattern(vibrationPattern);
                manager.createNotificationChannel(channel);
            }

            Intent markIntent = new Intent(context, NotificationActionReceiver.class);
            markIntent.setAction("ACTION_MARK_COMPLETE");
            markIntent.putExtra("task_id", taskId);

            PendingIntent markPendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId + 1000,
                    markIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            PendingIntent snoozePendingIntent = null;
            if (!isReminder) {
                Intent snoozeIntent = new Intent(context, SnoozeOptionsActivity.class);
                snoozeIntent.putExtra("task_id", taskId);
                snoozeIntent.putExtra("task_title", taskTitle);
                snoozeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                snoozePendingIntent = PendingIntent.getActivity(
                        context,
                        taskId + 2000,
                        snoozeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setContentTitle(contentTitle)
                    .setContentText(taskTitle)
                    .setSound(soundUri)
                    .setVibrate(vibrationPattern)
                    .setAutoCancel(!persistent)
                    .setPriority(importance)
                    .addAction(R.drawable.timeicon, "Mark Complete", markPendingIntent);

            if (persistent) builder.setOngoing(true);
            if (snoozePendingIntent != null) {
                builder.addAction(R.drawable.calendaricon, "Snooze", snoozePendingIntent);
            }

            manager.notify(notificationId, builder.build());

            // 🔊 Loop sound manually for high priority tasks
            if ("high".equals(priority) && !isReminder) {
                stopActiveRingtone(); // 🛑 Cancel previous ringing before starting new

                try {
                    Ringtone ringtone = RingtoneManager.getRingtone(context, soundUri);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.setLooping(true);
                    }
                    ringtone.play();
                    activeRingtone = ringtone;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 🔁 Handle repeating task
            if (!isReminder && task.getRepeat() != null && !task.getRepeat().isEmpty()) {
                TaskUtils utils = new TaskUtils(context, db);
                utils.handleRepeatingTask(task);
            }

            db.notificationLogDao().insert(new NotificationLog(taskId, taskTitle, System.currentTimeMillis()));
        }).start();
    }
}
