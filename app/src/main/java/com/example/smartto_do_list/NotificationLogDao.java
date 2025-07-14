package com.example.smartto_do_list;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NotificationLogDao {
    @Insert
    void insert(NotificationLog notificationLog);

    @Query("SELECT * FROM notification_log ORDER BY notificationTime DESC")
    List<NotificationLog> getAllNotifications();

    @Query("SELECT * FROM notification_log WHERE taskId = :taskId ORDER BY notificationTime DESC")
    List<NotificationLog> getNotificationsForTask(int taskId);

    @Query("DELETE FROM notification_log")
    void deleteAllNotifications();

    @Delete
    void delete(NotificationLog notificationLog);

}
