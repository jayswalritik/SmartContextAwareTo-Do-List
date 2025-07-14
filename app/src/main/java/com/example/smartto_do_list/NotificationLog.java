package com.example.smartto_do_list;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notification_log")
public class NotificationLog {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private int taskId;
    private String taskTitle;
    private long notificationTime; // Timestamp when the notification was sent

    public NotificationLog(int taskId, String taskTitle, long notificationTime) {
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.notificationTime = notificationTime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTaskId() {
        return taskId;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public long getNotificationTime() {
        return notificationTime;
    }
}