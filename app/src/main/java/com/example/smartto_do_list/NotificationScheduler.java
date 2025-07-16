package com.example.smartto_do_list;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.smartto_do_list.utils.TaskUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class NotificationScheduler {

    public static void scheduleTaskNotification(Context context, Task task) {
        if (task == null) return;

        // ✅ Cancel any existing alarm before scheduling new one
        cancelTaskNotification(context, task.getId());

        if (task.getTime() == null || task.getTime().isEmpty()) {
            // No time set, schedule two notifications at 9:00 and 17:00
            scheduleNotificationAtTime(context, task, 9, 0, 0);   // 09:00 AM
            scheduleNotificationAtTime(context, task, 17, 0, 1);  // 05:00 PM (17:00)
        } else {
            Calendar calendar = getNotificationTime(context, task);
            if (calendar != null) {
                scheduleAlarm(context, task, calendar, task.getId());
            }
        }
        scheduleTaskReminder(context, task); // 🔔 schedule reminder before main notification
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
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Cancel default alarm
        cancelAlarmWithRequestCode(context, alarmManager, taskId);

        // Cancel possible 9:00 AM and 5:00 PM alarms
        cancelAlarmWithRequestCode(context, alarmManager, taskId * 10 + 0); // 9:00 AM
        cancelAlarmWithRequestCode(context, alarmManager, taskId * 10 + 1); // 5:00 PM
    }

    private static void cancelAlarmWithRequestCode(Context context, AlarmManager alarmManager, int requestCode) {
        Intent intent = new Intent(context, TaskNotificationsReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            Log.d("NotificationScheduler", "Cancelled alarm with requestCode: " + requestCode);
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

    public static long getReminderOffsetMillis(String reminder) {
        if (reminder == null || reminder.trim().isEmpty()) return 0;

        reminder = reminder.toLowerCase().replace("custom", "").trim();
        String[] parts = reminder.split(" ");

        if (parts.length < 2) return 0;

        try {
            int value = Integer.parseInt(parts[0]);
            String unit = parts[1];

            switch (unit) {
                case "min":
                case "mins":
                case "minutes":
                    return TimeUnit.MINUTES.toMillis(value);
                case "hr":
                case "hrs":
                case "hour":
                case "hours":
                    return TimeUnit.HOURS.toMillis(value);
                case "day":
                case "days":
                    return TimeUnit.DAYS.toMillis(value);
                case "week":
                case "weeks":
                    return TimeUnit.DAYS.toMillis(value * 7);
                case "month":
                case "months":
                    return TimeUnit.DAYS.toMillis(value * 30); // approx
                case "year":
                case "years":
                    return TimeUnit.DAYS.toMillis(value * 365); // approx
                default:
                    return 0;
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0;
        }
    }
    public static void scheduleTaskReminder(Context context, Task task) {
        if (task == null || task.getReminder() == null || task.getReminder().isEmpty()) return;

        long scheduledTimeMillis = TaskUtils.getNextScheduledTimeMillis(task);
        long reminderOffsetMillis = getReminderOffsetMillis(task.getReminder());

        long reminderTimeMillis = scheduledTimeMillis - reminderOffsetMillis;
        if (reminderTimeMillis <= System.currentTimeMillis()) return; // Too late to remind

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(reminderTimeMillis);

        Intent intent = new Intent(context, TaskNotificationsReceiver.class);
        intent.putExtra("task_id", task.getId());
        intent.putExtra("task_title", "[Reminder] " + task.getTitle());
        intent.putExtra("notification_type", "Reminder"); // ✅ Add this line

        int requestCode = task.getId() + 9999; // Different request code than main

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                reminderTimeMillis,
                pendingIntent
        );
    }
    public static void cancelTaskReminder(Context context, int taskId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, TaskNotificationsReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId + 5000,  // use same ID as reminder notification
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

}
