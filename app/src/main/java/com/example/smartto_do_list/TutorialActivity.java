package com.example.smartto_do_list;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;

public class TutorialActivity extends AppCompatActivity {
    private ImageButton backIconButton;

    private Button  btnCreateTask, btnEditTask, btnViewTask, btnDeleteTask, btnMarkCompleted, btnSelectTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);

        backIconButton = findViewById(R.id.backiconbutton);
        btnCreateTask = findViewById(R.id.btn_create_task);
        btnEditTask = findViewById(R.id.btn_edit_task);
        btnViewTask = findViewById(R.id.btn_view_task);
        btnDeleteTask = findViewById(R.id.btn_delete_task);
        btnMarkCompleted = findViewById(R.id.btn_mark_completed);
        btnSelectTask = findViewById(R.id.btn_select_task);
        backIconButton.setOnClickListener(v -> onBackPressed());


        btnCreateTask.setOnClickListener(v -> {
                Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
                intent.putExtra("show_create_task_tutorial", true); // ❌ don't include open_drawer
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();


        });

        btnEditTask.setOnClickListener(v -> {
            Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
            intent.putExtra("show_edit_task_tutorial", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnViewTask.setOnClickListener(v -> {
            Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
            intent.putExtra("show_view_task_tutorial", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnDeleteTask.setOnClickListener(v -> {
            Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
            intent.putExtra("show_delete_task_tutorial", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });


        btnSelectTask.setOnClickListener(v -> {
            Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
            intent.putExtra("show_select_task_tutorial", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnMarkCompleted.setOnClickListener(v -> {
            Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
            intent.putExtra("show_task_mark_completed_tutorial", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });


    }

    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        Intent intent = new Intent(TutorialActivity.this, MainActivity.class);
        intent.putExtra("open_drawer", true);  // This is fine here
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }


}
