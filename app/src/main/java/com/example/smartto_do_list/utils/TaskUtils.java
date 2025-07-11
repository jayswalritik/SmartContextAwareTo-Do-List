package com.example.smartto_do_list.utils;

import android.content.Context;
import android.util.Log;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDatabase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TaskUtils {

    private final Context context;
    private final TaskDatabase db;

    public TaskUtils(Context context, TaskDatabase db) {
        this.context = context;
        this.db = db;
    }

    public void handleRepeatingTask(Task task) {
        if (task == null || task.getRepeat() == null || task.getRepeat().isEmpty()) return;
        if ("completed".equalsIgnoreCase(task.getTaskStatus())) return;

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        try {
            Date taskDate = dateFormat.parse(task.getDate());
            Calendar taskCal = Calendar.getInstance();
            taskCal.setTime(taskDate);

            String timeStr = task.getTime();
            String repeat = task.getRepeat().toLowerCase();
            boolean isTimeBased = repeat.contains("min") || repeat.contains("hour") || repeat.contains("hr");

            if (timeStr != null && !timeStr.isEmpty()) {
                Date taskTime = timeFormat.parse(timeStr);
                Calendar timeCal = Calendar.getInstance();
                timeCal.setTime(taskTime);
                taskCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                taskCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                taskCal.set(Calendar.SECOND, 0);
                taskCal.set(Calendar.MILLISECOND, 0);
            } else if (isTimeBased) {
                return; // Invalid time-based repeat without a time
            } else {
                taskCal.set(Calendar.HOUR_OF_DAY, 23);
                taskCal.set(Calendar.MINUTE, 59);
                taskCal.set(Calendar.SECOND, 0);
                taskCal.set(Calendar.MILLISECOND, 0);
            }

            Date now = new Date();
            if (!taskCal.getTime().before(now)) {
                return; // Task is still scheduled in the future
            }

            while (!taskCal.getTime().after(now)) {
                if (repeat.contains("every hour")) {
                    taskCal.add(Calendar.HOUR_OF_DAY, 1);
                } else if (repeat.contains("every day")) {
                    taskCal.add(Calendar.DATE, 1);
                } else if (repeat.contains("every week")) {
                    taskCal.add(Calendar.WEEK_OF_YEAR, 1);
                } else if (repeat.contains("every month")) {
                    taskCal.add(Calendar.MONTH, 1);
                } else if (repeat.contains("every year")) {
                    taskCal.add(Calendar.YEAR, 1);
                } else if (repeat.contains("every")) {
                    String[] parts = repeat.split(" ");
                    if (parts.length >= 3) {
                        try {
                            String rawAmount = parts[1];
                            String unit = parts[2];

                            int interval;
                            if (rawAmount.contains(":")) {
                                String[] hm = rawAmount.split(":");
                                int hrs = Integer.parseInt(hm[0]);
                                int mins = Integer.parseInt(hm[1]);
                                interval = hrs * 60 + mins;
                                unit = "min";
                            } else {
                                interval = Integer.parseInt(rawAmount);
                            }

                            switch (unit) {
                                case "min":
                                case "mins":
                                case "minute":
                                case "minutes":
                                    taskCal.add(Calendar.MINUTE, interval);
                                    break;
                                case "hr":
                                case "hrs":
                                case "hour":
                                case "hours":
                                    taskCal.add(Calendar.HOUR_OF_DAY, interval);
                                    break;
                                case "day":
                                case "days":
                                    taskCal.add(Calendar.DATE, interval);
                                    break;
                                case "week":
                                case "weeks":
                                    taskCal.add(Calendar.WEEK_OF_YEAR, interval);
                                    break;
                                case "month":
                                case "months":
                                    taskCal.add(Calendar.MONTH, interval);
                                    break;
                                case "year":
                                case "years":
                                    taskCal.add(Calendar.YEAR, interval);
                                    break;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    break;
                }
            }

            String newDate = dateFormat.format(taskCal.getTime());
            String newTime = (timeStr != null && !timeStr.isEmpty()) ? timeFormat.format(taskCal.getTime()) : "";
            task.setDate(newDate);
            task.setTime(newTime);

            Log.d("REPEAT_UPDATE", "Updated task " + task.getTitle() +
                    " → New date: " + newDate + ", New time: " + newTime);

            db.taskDao().updateTaskDateTime(task.getId(), newDate, newTime);

            NotificationScheduler.scheduleTaskNotification(context, task);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ✅ Gets the exact timestamp (millis) for next run
    public static long getNextScheduledTimeMillis(Task task) {
        if (task.getDate() == null || task.getTime() == null || task.getTime().isEmpty())
            return Long.MAX_VALUE;

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

            Calendar cal = Calendar.getInstance();
            cal.setTime(dateFormat.parse(task.getDate()));

            Calendar timeCal = Calendar.getInstance();
            timeCal.setTime(timeFormat.parse(task.getTime()));
            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            return cal.getTimeInMillis();

        } catch (ParseException e) {
            return Long.MAX_VALUE;
        }
    }

    // ✅ Returns milliseconds until task is due
    public static long millisUntilNextRun(Task task) {
        long millis = getNextScheduledTimeMillis(task);
        long now = System.currentTimeMillis();
        return (millis > now) ? (millis - now) : Long.MAX_VALUE;
    }

    // ✅ 🔧 FIX: Provide this missing static method for WorkerUtils
    public static long getMillisUntilNextDueTask(List<Task> tasks) {
        long minMillis = Long.MAX_VALUE;
        long now = System.currentTimeMillis();

        for (Task task : tasks) {
            long taskTime = getNextScheduledTimeMillis(task);
            if (taskTime > now) {
                minMillis = Math.min(minMillis, taskTime - now);
            }
        }

        return (minMillis == Long.MAX_VALUE) ? TimeUnit.MINUTES.toMillis(15) : minMillis;
    }
}
