package com.example.smartto_do_list;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TaskDetailsActivity extends AppCompatActivity {

    private TextView titleText, dateText, timeText, categoryText, priorityText,
            reminderText, noteIdText, locationName, statusText, repeatText;

    private TaskDatabase db;
    private Task currentTask;
    private ImageButton backButton, editButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_details);

        initViews();
        db = TaskDatabase.getInstance(getApplicationContext());

        int taskId = getIntent().getIntExtra("task_id", -1);
        if (taskId != -1) {
            TaskRelationWithSavedLocationsAndNote relation = db.taskDao().getTaskWithLocationAndNote(taskId);

            if (relation != null) {
                currentTask = relation.task;  // <-- Assign the loaded task here
                populateDetails(relation);
            } else {
                Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show();
                finish();
            }

        } else {
            Toast.makeText(this, "No task ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }

        backButton.setOnClickListener(v -> {
            onBackPressed();
        });

        editButton.setOnClickListener(v -> {
            if (currentTask != null) {
                Intent intent = new Intent(TaskDetailsActivity.this, TaskDescription.class);
                intent.putExtra("task_id", currentTask.getId());
                intent.putExtra("is_view_only", false);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Task not loaded", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initViews() {
        backButton = findViewById(R.id.backiconbutton);
        editButton = findViewById(R.id.editbutton);
        titleText = findViewById(R.id.tvTitle);
        dateText = findViewById(R.id.tvDate);
        timeText = findViewById(R.id.tvTime);
        categoryText = findViewById(R.id.tvCategory);
        priorityText = findViewById(R.id.tvPriority);
        reminderText = findViewById(R.id.tvReminder);
        repeatText = findViewById(R.id.tvRepeat);
        statusText = findViewById(R.id.tvStatus);
        noteIdText = findViewById(R.id.tvNoteId);         // Added
        locationName = findViewById(R.id.tvLocationId); // Added
    }

    private void populateDetails(TaskRelationWithSavedLocationsAndNote relation) {
        Task task = relation.task;
        SavedLocations location = relation.locations;
        Note note = relation.note;

        titleText.setText(isEmpty(task.getTitle()) ? "Not set" : task.getTitle());
        dateText.setText(isEmpty(task.getDate()) ? "Not set" : task.getDate());
        timeText.setText(isEmpty(task.getTime()) ? "Not set" : task.getTime());
        categoryText.setText(isEmpty(task.getCategory()) ? "Not set" : task.getCategory());
        priorityText.setText(isEmpty(task.getPriority()) ? "Not set" : task.getPriority());
        reminderText.setText(isEmpty(task.getReminder()) ? "Not set" : task.getReminder());
        repeatText.setText(isEmpty(task.getRepeat()) ? "Not set" : task.getRepeat());
        locationName.setText(location != null && !isEmpty(location.label) ? location.label : "Not set");
        statusText.setText(isEmpty(task.getTaskStatus()) ? "Not set" : task.getTaskStatus());

        noteIdText.setText(note != null && !isEmpty(note.content) ? note.content : "No note attached");
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

    }

}
