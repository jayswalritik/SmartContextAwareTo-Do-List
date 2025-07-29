package com.example.smartto_do_list;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Map;

@Entity(tableName = "tasks")
public class Task {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String title;

    public String date;
    public String time;

    @ColumnInfo(name = "location_id")
    public int locationId;

    public String priority;
    public String category;
    public String reminder;

    public String repeat;

    @ColumnInfo(name = "note_id")
    public Integer noteId;

    @ColumnInfo(name = "task_status")
    public String taskStatus;

    @ColumnInfo(name = "completed_date")  // Make sure this matches DB column name
    public String completedDate; // Format: "yyyy-MM-dd"

    // Getters
    public String getTitle() { return title; }
    public String getDate() { return date != null ? date : ""; }
    public String getTime() { return time != null ? time : ""; }
    public String getPriority() { return priority != null ? priority : ""; }
    public String getCategory() { return category != null ? category : ""; }
    public String getReminder() { return reminder != null ? reminder : ""; }
    public String getTaskStatus() { return taskStatus != null ? taskStatus : ""; }
    public int getLocationId() { return locationId; }
    public int getId() { return id; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setTitle(@NonNull String title) { this.title = title; }
    public void setDate(String date) { this.date = date; }
    public void setTime(String time) { this.time = time; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setCategory(String category) { this.category = category; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }

    public String getLocationLabel(Map<Integer, String> locationMap) {
        if (locationMap == null) return "N/A";
        return locationMap.getOrDefault(locationId, "N/A");
    }

    // Getter and setter for completedDate
    public String getCompletedDate() {
        return completedDate != null ? completedDate : "";
    }

    public void setCompletedDate(String completedDate) {
        this.completedDate = completedDate;
    }

    public String getRepeat() {
        return repeat;
    }

    public void setRepeat(String repeat) {
        this.repeat = repeat;
    }


}
