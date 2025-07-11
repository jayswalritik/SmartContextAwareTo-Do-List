package com.example.smartto_do_list.utils;

import android.content.Context;

import com.example.smartto_do_list.Task;
import com.example.smartto_do_list.TaskDatabase;
import com.example.smartto_do_list.workers.TaskRepeatWorker;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class WorkerUtils {

    public static void scheduleDynamicRepeatWorker(Context context) {
        TaskDatabase db = TaskDatabase.getInstance(context);

        new Thread(() -> {
            try {
                // 🔄 Load all tasks instead of just today’s tasks
                List<Task> tasks = db.taskDao().getAllTasks();

                // ⏱ Calculate delay until next task
                long delayMillis = TaskUtils.getMillisUntilNextDueTask(tasks);
                long delayMinutes = Math.max(1, TimeUnit.MILLISECONDS.toMinutes(delayMillis));

                // 🔁 Schedule the worker
                TaskRepeatWorker.scheduleNextRepeatWorker(context, delayMinutes);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
