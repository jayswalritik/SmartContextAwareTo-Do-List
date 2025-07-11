package com.example.smartto_do_list;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {

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

    // --- NEW method to fetch tasks only for a given date (e.g. today) ---
    @Query("SELECT * FROM tasks WHERE date = :date")
    List<Task> getTasksByDate(String date);

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

    @Query("UPDATE tasks SET location_id = -1 WHERE location_id = :locationId")
    void clearTasksWithLocationId(int locationId);

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    TaskRelationWithSavedLocationsAndNote getTaskWithLocationAndNote(int taskId);

    // Completed task queries

    @Query("SELECT * FROM tasks WHERE LOWER(task_status) = 'completed'")
    List<Task> getCompletedTasks();

    @Query("SELECT * FROM tasks WHERE LOWER(task_status) = 'completed' AND completed_date >= date('now', '-30 day')")
    List<Task> getRecentCompletedTasks();

    @Query("UPDATE tasks SET task_status = :status, completed_date = :completedDate WHERE id = :taskId")
    void updateTaskStatusAndCompletedDate(int taskId, String status, String completedDate);

    // Updated queries that use dynamic status

    @Query("SELECT * FROM tasks WHERE LOWER(task_status) = LOWER(:status) ORDER BY date ASC, time ASC")
    List<Task> getTasksByStatus(String status);

    @Query("SELECT * FROM tasks WHERE LOWER(task_status) = LOWER(:status) AND LOWER(priority) = LOWER(:priority) ORDER BY date ASC, time ASC")
    List<Task> getTasksByPriorityAndStatus(String priority, String status);

    @Query("SELECT * FROM tasks WHERE LOWER(task_status) = LOWER(:status) AND LOWER(category) = LOWER(:category) ORDER BY date ASC, time ASC")
    List<Task> getTasksByCategoryAndStatus(String category, String status);

    @Query("SELECT * FROM tasks WHERE LOWER(task_status) = LOWER(:status) AND date = :date ORDER BY time ASC")
    List<Task> getTasksByDateAndStatus(String date, String status);

    @Query("UPDATE tasks SET date = :newDate, time = :newTime WHERE id = :taskId")
    void updateTaskDateTime(int taskId, String newDate, String newTime);

}
