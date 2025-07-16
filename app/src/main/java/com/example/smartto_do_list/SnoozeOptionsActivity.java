package com.example.smartto_do_list;

import android.app.NotificationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SnoozeOptionsActivity extends AppCompatActivity {

    private int taskId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snooze_options);

        taskId = getIntent().getIntExtra("task_id", -1);

        Button btn10 = findViewById(R.id.btn10min);
        Button btn30 = findViewById(R.id.btn30min);
        Button btn60 = findViewById(R.id.btn1hour);
        Button btnCustom = findViewById(R.id.btncustom);

        View.OnClickListener listener = v -> {
            int minutes = 10;
            if (v.getId() == R.id.btn30min) minutes = 30;
            else if (v.getId() == R.id.btn1hour) minutes = 60;

            scheduleSnoozedNotification(taskId, minutes);
            finish();
        };

        btn10.setOnClickListener(listener);
        btn30.setOnClickListener(listener);
        btn60.setOnClickListener(listener);

        btnCustom.setOnClickListener(v -> showCustomSnoozeDialog());
    }

    private void scheduleSnoozedNotification(int taskId, int minutes) {
        TaskDatabase db = TaskDatabase.getInstance(this);
        new Thread(() -> {
            Task task = db.taskDao().getTaskById(taskId);
            if (task != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MINUTE, minutes);
                task.setDate(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime()));
                task.setTime(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.getTime()));
                db.taskDao().updateTaskDateTime(taskId, task.getDate(), task.getTime());

                TaskNotificationsReceiver.stopActiveRingtone(); // 🛑 Stop alarm sound on snooze

                NotificationScheduler.scheduleTaskNotification(this, task);
                NotificationScheduler.scheduleTaskReminder(this, task);

                // ✅ Clear the old notification from the screen
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                manager.cancel(taskId);
            }
        }).start();
    }
    private void showCustomSnoozeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.custom_snooze_units_button, null);
        EditText inputValue = dialogView.findViewById(R.id.input_value);
        Button btnMinutes = dialogView.findViewById(R.id.btn_minutes);
        Button btnHours = dialogView.findViewById(R.id.btn_hours);

        final int[] multiplier = {1}; // 1 for minutes, 60 for hours

        // Unit selection with highlight
        View.OnClickListener unitSelector = v -> {
            if (v.getId() == R.id.btn_minutes) {
                multiplier[0] = 1;
                btnMinutes.setAlpha(1f);
                btnHours.setAlpha(0.5f);
            } else {
                multiplier[0] = 60;
                btnHours.setAlpha(1f);
                btnMinutes.setAlpha(0.5f);
            }
            inputValue.setError(null);
        };

        btnMinutes.setOnClickListener(unitSelector);
        btnHours.setOnClickListener(unitSelector);
        btnMinutes.performClick(); // default to minutes

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Custom Snooze")
                .setView(dialogView)
                .setPositiveButton("Snooze", null) // will override
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String valueStr = inputValue.getText().toString().trim();

                if (valueStr.isEmpty()) {
                    inputValue.setError("Please enter a value");
                    return;
                }

                try {
                    int input = Integer.parseInt(valueStr);
                    int unit = multiplier[0];
                    int totalMinutes = input * unit;

                    if ((unit == 1 && input >= 60)) {
                        inputValue.setError("Must be less than 60 minutes");
                        return;
                    }

                    if (unit == 60 && input >= 24) {
                        inputValue.setError("Must be less than 24 hours");
                        return;
                    }

                    if (totalMinutes < 10) {
                        inputValue.setError("Snooze must be at least 10 minutes");
                        return;
                    }

                    int taskId = getIntent().getIntExtra("task_id", -1);
                    scheduleSnoozedNotification(taskId, totalMinutes);
                    dialog.dismiss(); // close only after valid input
                    finish();

                } catch (NumberFormatException e) {
                    inputValue.setError("Invalid number");
                }
            });
        });

        dialog.show();
    }

}
