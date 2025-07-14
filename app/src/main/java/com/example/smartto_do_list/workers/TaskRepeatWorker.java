package com.example.smartto_do_list.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.ExistingWorkPolicy;

import com.example.smartto_do_list.NotificationScheduler;
import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDatabase;
import com.example.smartto_do_list.utils.TaskUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TaskRepeatWorker extends Worker {

    public TaskRepeatWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        Log.d("TASK_WORKER", "⏰ TaskRepeatWorker triggered at " +
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));

        TaskDatabase db = TaskDatabase.getInstance(getApplicationContext());
        List<Task> allTasks = db.taskDao().getAllTasks();

        TaskUtils utils = new TaskUtils(getApplicationContext(), db);

        long nextRunMillis = Long.MAX_VALUE;
        long now = System.currentTimeMillis();

        for (Task task : allTasks) {
            long taskTimeMillis = TaskUtils.getNextScheduledTimeMillis(task);
            if (taskTimeMillis <= now + 1500) {
                // Due → send notification
                NotificationScheduler.scheduleTaskNotification(getApplicationContext(), task);

                // Handle repeat logic only if repeat is set
                if (task.getRepeat() != null && !task.getRepeat().isEmpty()) {
                    utils.handleRepeatingTask(task);

                    // Get new time after repeat
                    task = db.taskDao().getTaskById(task.getId());
                    taskTimeMillis = TaskUtils.getNextScheduledTimeMillis(task);
                }
            }

            nextRunMillis = Math.min(nextRunMillis, taskTimeMillis);
        }

        long delay = Math.max(1, nextRunMillis - System.currentTimeMillis());
        long delayMinutes = Math.max(1, TimeUnit.MILLISECONDS.toMinutes(delay));

        Log.d("TASK_WORKER", "📆 Next run in " + delayMinutes + " minute(s)");

        // Reschedule dynamically
        scheduleNextRepeatWorker(getApplicationContext(), delayMinutes);

        return Result.success();
    }


    public static void scheduleNextRepeatWorker(Context ctx, long delayMin) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(TaskRepeatWorker.class)
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(ctx).enqueueUniqueWork(
                "DynamicRepeatUpdater",
                ExistingWorkPolicy.REPLACE,
                req
        );
    }

}
