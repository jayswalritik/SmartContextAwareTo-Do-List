package com.example.smartto_do_list;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao2 {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);
    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    Task getTaskById(int taskId);

    @Query("SELECT * FROM tasks WHERE LOWER(title) = LOWER(:title) AND LOWER(task_status) = 'pending' LIMIT 1")
    Task getPendingTaskByTitleIgnoreCase(String title);

    @Query("UPDATE tasks SET task_status = :status WHERE id = :taskId")
    void updateTaskStatus(int taskId, String status);

    @Query("SELECT * FROM tasks " +
            "ORDER BY " +
            "CASE " +
            "  WHEN task_status = 'pending' AND (date > :today OR (date = :today AND (time IS NOT NULL AND time != ''))) THEN 0 " +
            "  WHEN task_status = 'pending' THEN 1 " +
            "  ELSE 2 " +
            "END, " +
            "date ASC, " +
            "CASE WHEN time IS NULL OR time = '' THEN 1 ELSE 0 END, " +
            "time ASC")
    List<Task> getAllTasksCustomOrdered(String today);

    @Query("SELECT * FROM tasks WHERE priority = :priority " +
            "ORDER BY " +
            "CASE " +
            "  WHEN task_status = 'pending' AND (date > :today OR (date = :today AND (time IS NOT NULL AND time != ''))) THEN 0 " +
            "  WHEN task_status = 'pending' THEN 1 " +
            "  ELSE 2 " +
            "END, " +
            "date ASC, " +
            "CASE WHEN time IS NULL OR time = '' THEN 1 ELSE 0 END, " +
            "time ASC")
    List<Task> getTasksByPriorityWithStatuses(String priority, String today);

    @Query("SELECT * FROM tasks WHERE LOWER(category) = LOWER(:category) " +
            "ORDER BY " +
            "CASE " +
            "  WHEN task_status = 'pending' AND (date > :today OR (date = :today AND (time IS NOT NULL AND time != ''))) THEN 0 " +
            "  WHEN task_status = 'pending' THEN 1 " +
            "  ELSE 2 " +
            "END, " +
            "date ASC, " +
            "CASE WHEN time IS NULL OR time = '' THEN 1 ELSE 0 END, " +
            "time ASC")
    List<Task> getTasksByCategoryWithStatuses(String category, String today);

    @Query("SELECT * FROM tasks WHERE date = :date " +
            "ORDER BY " +
            "CASE WHEN task_status = 'pending' THEN 0 ELSE 1 END, " +
            "time ASC")
    List<Task> getTasksByDateWithStatuses(String date);

    @Delete
    void deleteTasks(List<Task> tasks);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTasks(List<Task> tasks);

    @Update
    void updateTasks(List<Task> tasks);

    @Query("UPDATE tasks SET task_status = 'overdue' WHERE task_status = 'pending' AND date < :today")
    void markOverdueTasks(String today);

    @Query("SELECT * FROM tasks")
    List<Task> getAllTasks();


}
