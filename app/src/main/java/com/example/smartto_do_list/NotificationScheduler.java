package com.example.smartto_do_list;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class NotificationScheduler {

    public static void scheduleTaskNotification(Context context, Task task) {
        if (task == null) return;

        if (task.getTime() == null || task.getTime().isEmpty()) {
            // No time set, schedule two notifications at 9:00 and 17:00
            scheduleNotificationAtTime(context, task, 9, 0, 0);   // 09:00 AM
            scheduleNotificationAtTime(context, task, 17, 0, 1);  // 05:00 PM (17:00)
        } else {
            // Time is set, schedule single notification at task time
            Calendar calendar = getNotificationTime(context, task);
            if (calendar != null) {
                scheduleAlarm(context, task, calendar, task.getId());
            }
        }
    }

    private static void scheduleNotificationAtTime(Context context, Task task, int hour, int minute, int suffix) {
        Calendar calendar = Calendar.getInstance();
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            calendar.setTime(dateFormat.parse(task.getDate()));
        } catch (ParseException e) {
            e.printStackTrace();
            return;
        }

        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // Skip scheduling if notification time is past
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) return;

        int requestCode = task.getId() * 10 + suffix;  // create unique request code per alarm
        scheduleAlarm(context, task, calendar, requestCode);
    }

    private static void scheduleAlarm(Context context, Task task, Calendar calendar, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("NotificationScheduler", "Exact alarms not allowed. Skipping...");
                return;
            }
        }

        Intent intent = new Intent(context, TaskNotificationsReceiver.class);
        intent.putExtra("task_id", task.getId());
        intent.putExtra("task_title", task.getTitle());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
        );
    }

    private static Calendar getNotificationTime(Context context, Task task) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(dateFormat.parse(task.getDate()));

            if (!task.getTime().isEmpty()) {
                Calendar timeCal = Calendar.getInstance();
                timeCal.setTime(timeFormat.parse(task.getTime()));
                calendar.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                calendar.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
            } else {
                // fallback (should not happen here)
                calendar.set(Calendar.HOUR_OF_DAY, 9);
                calendar.set(Calendar.MINUTE, 0);
            }

            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
                return null;
            }

            return calendar;

        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void cancelTaskNotification(Context context, int taskId) {
        Intent intent = new Intent(context, TaskNotificationsReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
            Log.d("NotificationScheduler", "Cancelled alarm for taskId: " + taskId);
        }
    }

    public static void scheduleImmediateNotification(Context context, Task task) {
        Intent intent = new Intent(context, TaskNotificationsReceiver.class);
        intent.putExtra("task_id", task.getId());
        intent.putExtra("task_title", task.getTitle());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                task.getId(),  // use the same request code as original alarm for uniqueness
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long triggerAtMillis = System.currentTimeMillis() + 1000; // 1 second later

        alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
        );
    }


}
