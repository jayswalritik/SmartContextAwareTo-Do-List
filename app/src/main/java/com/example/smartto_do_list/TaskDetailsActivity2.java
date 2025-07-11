package com.example.smartto_do_list;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TaskDetailsActivity2 extends AppCompatActivity {

    private TextView titleText, dateText, timeText, categoryText, priorityText,
            reminderText, noteIdText, locationIdText, statusText;

    private TaskDatabase db;
    private Task currentTask;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_details);

        initViews();
        db = TaskDatabase.getInstance(getApplicationContext());

        int taskId = getIntent().getIntExtra("task_id", -1);
        if (taskId != -1) {
            currentTask = db.taskDao().getTaskById(taskId);
            if (currentTask != null) {
                populateDetails(currentTask);
            } else {
                Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "No task ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(TaskDetailsActivity2.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void initViews() {
        backButton = findViewById(R.id.backiconbutton);
        titleText = findViewById(R.id.tvTitle);
        dateText = findViewById(R.id.tvDate);
        timeText = findViewById(R.id.tvTime);
        categoryText = findViewById(R.id.tvCategory);
        priorityText = findViewById(R.id.tvPriority);
        reminderText = findViewById(R.id.tvReminder);
        statusText = findViewById(R.id.tvStatus);
        noteIdText = findViewById(R.id.tvNoteId);         // Added
        locationIdText = findViewById(R.id.tvLocationId); // Added
    }

    private void populateDetails(Task task) {
        titleText.setText(isEmpty(task.getTitle()) ? "Not set" : task.getTitle());
        dateText.setText(isEmpty(task.getDate()) ? "Not set" : task.getDate());
        timeText.setText(isEmpty(task.getTime()) ? "Not set" : task.getTime());
        categoryText.setText(isEmpty(task.getCategory()) ? "Not set" : task.getCategory());
        priorityText.setText(isEmpty(task.getPriority()) ? "Not set" : task.getPriority());
        reminderText.setText(isEmpty(task.getReminder()) ? "Not set" : task.getReminder());
        locationIdText.setText(task.getLocationId() == 0 ? "Not set" : String.valueOf(task.getLocationId()));
        statusText.setText(isEmpty(task.getTaskStatus()) ? "Not set" : task.getTaskStatus());
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

}
