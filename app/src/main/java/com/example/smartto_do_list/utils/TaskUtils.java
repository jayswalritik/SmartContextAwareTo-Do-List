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

            boolean isTimeBased = repeat.contains("min") || repeat.contains("mins") ||
                    repeat.contains("minute") || repeat.contains("minutes") ||
                    repeat.contains("hr") || repeat.contains("hrs") ||
                    repeat.contains("hour") || repeat.contains("hours");

            if (timeStr != null && !timeStr.isEmpty()) {
                Date taskTime = timeFormat.parse(timeStr);
                Calendar timeCal = Calendar.getInstance();
                timeCal.setTime(taskTime);
                taskCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                taskCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                taskCal.set(Calendar.SECOND, 0);
                taskCal.set(Calendar.MILLISECOND, 0);
            } else if (isTimeBased) {
                return; // invalid repeat format without a time
            } else {
                taskCal.set(Calendar.HOUR_OF_DAY, 23);
                taskCal.set(Calendar.MINUTE, 59);
                taskCal.set(Calendar.SECOND, 0);
                taskCal.set(Calendar.MILLISECOND, 0);
            }

            Date now = new Date();
            if (!taskCal.getTime().before(now)) return;

            // ✅ REMOVED: Same-day limitation code
            // OLD CODE (REMOVED):
            // Calendar endOfDay = Calendar.getInstance();
            // endOfDay.setTime(taskCal.getTime());
            // endOfDay.set(Calendar.HOUR_OF_DAY, 23);
            // endOfDay.set(Calendar.MINUTE, 59);
            // endOfDay.set(Calendar.SECOND, 59);
            // endOfDay.set(Calendar.MILLISECOND, 999);

            // ✅ NEW: Calculate next occurrence without day restriction
            Calendar nextOccurrence = calculateNextOccurrence(taskCal, repeat, now);

            if (nextOccurrence != null) {
                String newDate = dateFormat.format(nextOccurrence.getTime());
                String newTime = (timeStr != null && !timeStr.isEmpty()) ?
                        timeFormat.format(nextOccurrence.getTime()) : "";

                task.setDate(newDate);
                task.setTime(newTime);
                db.taskDao().updateTaskDateTime(task.getId(), newDate, newTime);
                NotificationScheduler.scheduleTaskNotification(context, task);

                Log.d("REPEAT_UPDATE", "Updated task " + task.getTitle() +
                        " → New date: " + newDate + ", New time: " + newTime);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ✅ NEW: Helper method to calculate next occurrence without day limits
    private Calendar calculateNextOccurrence(Calendar taskCal, String repeat, Date now) {
        Calendar nextCal = (Calendar) taskCal.clone();

        // Keep calculating until we find a future time
        while (!nextCal.getTime().after(now)) {
            if (repeat.contains("every hour")) {
                nextCal.add(Calendar.HOUR_OF_DAY, 1);
            } else if (repeat.contains("every day")) {
                nextCal.add(Calendar.DATE, 1);
            } else if (repeat.contains("every week")) {
                nextCal.add(Calendar.WEEK_OF_YEAR, 1);
            } else if (repeat.contains("every month")) {
                nextCal.add(Calendar.MONTH, 1);
            } else if (repeat.contains("every year")) {
                nextCal.add(Calendar.YEAR, 1);
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
                                nextCal.add(Calendar.MINUTE, interval);
                                break;
                            case "hr":
                            case "hrs":
                            case "hour":
                            case "hours":
                                nextCal.add(Calendar.HOUR_OF_DAY, interval);
                                break;
                            case "day":
                            case "days":
                                nextCal.add(Calendar.DATE, interval);
                                break;
                            case "week":
                            case "weeks":
                                nextCal.add(Calendar.WEEK_OF_YEAR, interval);
                                break;
                            case "month":
                            case "months":
                                nextCal.add(Calendar.MONTH, interval);
                                break;
                            case "year":
                            case "years":
                                nextCal.add(Calendar.YEAR, interval);
                                break;
                            default:
                                return null; // Invalid unit
                        }
                    } catch (NumberFormatException e) {
                        return null; // Invalid format
                    }
                } else {
                    return null; // Invalid format
                }
            } else {
                return null; // Unknown repeat pattern
            }

            // ✅ OPTIONAL: Add safety limit to prevent infinite loops
            // Stop if we've calculated too far into the future (e.g., 1 year)
            long maxFutureTime = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000); // 1 year
            if (nextCal.getTimeInMillis() > maxFutureTime) {
                Log.w("REPEAT_UPDATE", "Task repeat calculation exceeded 1 year limit");
                return null;
            }
        }

        return nextCal;
    }
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
    public static long millisUntilNextRun(Task task) {
        long millis = getNextScheduledTimeMillis(task);
        long now = System.currentTimeMillis();
        return (millis > now) ? (millis - now) : Long.MAX_VALUE;
    }
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