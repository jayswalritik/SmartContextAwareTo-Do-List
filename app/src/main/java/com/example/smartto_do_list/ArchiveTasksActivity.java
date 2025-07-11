package com.example.smartto_do_list;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ArchiveTasksActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ArchiveTasksAdapter adapter;
    private List<Task> archivedTasks = new ArrayList<>();
    private TaskDao taskDao;

    private CheckBox selectAllCheckbox;
    private ImageButton deleteAllButton, backButton;
    private TextView emptyMessage;
    private View actionRow;

    private boolean selectionModeActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive_tasks);

        recyclerView = findViewById(R.id.savedLocationsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        emptyMessage = findViewById(R.id.emptyMessage);
        actionRow = findViewById(R.id.actionrow);
        selectAllCheckbox = findViewById(R.id.selectallcheckbox);
        deleteAllButton = findViewById(R.id.deleteallicon);
        backButton = findViewById(R.id.backiconbutton);

        backButton.setOnClickListener(v -> finish());

        // ✅ Correct place to initialize taskDao
        taskDao = TaskDatabase.getInstance(this).taskDao();

        // ✅ Now taskDao is non-null when passed to adapter
        adapter = new ArchiveTasksAdapter(archivedTasks, taskDao, this, new ArchiveTasksAdapter.Listener() {
            @Override
            public void onDelete(Task task) {
                confirmDelete(task);
            }

            @Override
            public void onSelectionCountChanged(int selectedCount, int totalCount) {
                selectAllCheckbox.setOnCheckedChangeListener(null);
                selectAllCheckbox.setChecked(selectedCount == totalCount && totalCount > 0);
                selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) adapter.selectAll();
                    else adapter.clearSelection();
                });

                if (selectedCount > 0 && !selectionModeActive) {
                    selectionModeActive = true;
                    emptyMessage.setVisibility(View.GONE);
                    actionRow.setVisibility(View.VISIBLE);
                } else if (selectedCount == 0 && selectionModeActive) {
                    selectionModeActive = false;
                    emptyMessage.setVisibility(View.VISIBLE);
                    actionRow.setVisibility(View.GONE);
                    //if (archivedTasks.isEmpty()) emptyMessage.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean isSelectionModeActive() {
                return selectionModeActive;
            }

            @Override
            public void onSelectTask() {
                selectionModeActive = true;
            }

            @Override
            public void onClearSelection() {
                selectionModeActive = false;
                selectAllCheckbox.setChecked(false);
                actionRow.setVisibility(View.GONE);
                if (archivedTasks.isEmpty()) emptyMessage.setVisibility(View.VISIBLE);
            }
        });

        recyclerView.setAdapter(adapter);

        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) adapter.selectAll();
            else adapter.clearSelection();
        });

        deleteAllButton.setOnClickListener(v -> {
            List<Task> selected = adapter.getSelectedTasks();
            if (selected.isEmpty()) {
                Toast.makeText(this, "No tasks selected", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Delete Tasks")
                    .setMessage("Delete " + selected.size() + " archived task(s)?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        List<Task> deleted = new ArrayList<>(selected);
                        archivedTasks.removeAll(deleted);
                        adapter.clearSelection();
                        adapter.notifyDataSetChanged();

                        new Thread(() -> {
                            for (Task t : deleted) taskDao.delete(t);
                            runOnUiThread(() -> {
                                Snackbar.make(recyclerView, "Tasks deleted", Snackbar.LENGTH_LONG)
                                        .setAction("UNDO", v1 -> new Thread(() -> {
                                            for (Task t : deleted) taskDao.insert(t);
                                            runOnUiThread(this::loadArchivedTasks);
                                        }).start())
                                        .show();
                            });
                        }).start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        loadArchivedTasks();
        attachSwipeToDelete(recyclerView, adapter, taskDao);

        backButton.setOnClickListener(v -> {
            if (selectionModeActive) {
                selectionModeActive = false;
                adapter.clearSelection();
                actionRow.setVisibility(View.GONE);
                emptyMessage.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            } else {
                onBackPressed();
            }
        });


    }

    private void confirmDelete(Task task) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Delete \"" + task.title + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    archivedTasks.remove(task);
                    adapter.notifyDataSetChanged();

                    new Thread(() -> {
                        taskDao.delete(task);
                        runOnUiThread(() -> {
                            Snackbar.make(recyclerView, "Task deleted", Snackbar.LENGTH_LONG)
                                    .setAction("UNDO", v1 -> new Thread(() -> {
                                        taskDao.insert(task);
                                        runOnUiThread(this::loadArchivedTasks);
                                    }).start())
                                    .show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadArchivedTasks() {
        new Thread(() -> {
            List<Task> allCompleted = taskDao.getCompletedTasks();  // Gets all completed tasks
            List<Task> filtered = new ArrayList<>();

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date today = new Date();

                for (Task task : allCompleted) {
                    String completedDateStr = task.getCompletedDate();  // Use completedDate here!
                    if (completedDateStr == null || completedDateStr.isEmpty()) continue;

                    Date completedDate = sdf.parse(completedDateStr);
                    if (completedDate != null) {
                        long diff = today.getTime() - completedDate.getTime();
                        long days = diff / (1000 * 60 * 60 * 24);

                        // Show only tasks completed more than 30 days ago
                        if (days > 30) {
                            filtered.add(task);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            runOnUiThread(() -> {
                archivedTasks.clear();
                archivedTasks.addAll(filtered);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }
    private void attachSwipeToDelete(RecyclerView recyclerView, ArchiveTasksAdapter adapter, TaskDao taskDao) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (adapter.isSelectionModeActive()) {
                    adapter.notifyItemChanged(viewHolder.getAdapterPosition());
                    return;
                }

                int position = viewHolder.getAdapterPosition();
                Task taskToDelete = adapter.getTaskAt(position);

                // Remove from UI
                adapter.getCurrentList().remove(position);
                adapter.notifyItemRemoved(position);

                // Show Undo Snackbar
                Snackbar.make(recyclerView, "Task deleted", Snackbar.LENGTH_LONG)
                        .setAction("UNDO", v -> {
                            adapter.getCurrentList().add(position, taskToDelete);
                            adapter.notifyItemInserted(position);
                            recyclerView.scrollToPosition(position);
                        })
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                if (event != DISMISS_EVENT_ACTION) {
                                    // Actually delete only if not undone
                                    new Thread(() -> taskDao.delete(taskToDelete)).start();
                                }
                            }
                        })
                        .show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {

                if (adapter.isSelectionModeActive() || dX >= 0) {
                    super.onChildDraw(c, recyclerView, viewHolder, 0, dY, actionState, false);
                    return;
                }

                View itemView = viewHolder.itemView;
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                c.drawRect(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom(), paint);

                Drawable deleteIcon = ContextCompat.getDrawable(recyclerView.getContext(), R.drawable.deleteicon);
                if (deleteIcon != null) {
                    int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                    int top = itemView.getTop() + iconMargin;
                    int bottom = top + deleteIcon.getIntrinsicHeight();
                    int left = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
                    int right = itemView.getRight() - iconMargin;
                    deleteIcon.setBounds(left, top, right, bottom);
                    deleteIcon.draw(c);
                }

                float maxSwipe = recyclerView.getWidth() * 0.7f;
                itemView.setTranslationX(Math.max(dX, -maxSwipe));
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return adapter.isSelectionModeActive() ? 0 : makeMovementFlags(0, ItemTouchHelper.LEFT);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.4f;
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                return defaultValue * 20f;
            }

            @Override
            public float getSwipeVelocityThreshold(float defaultValue) {
                return defaultValue * 2f;
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (selectionModeActive) {
            selectionModeActive = false;
            adapter.clearSelection();
            actionRow.setVisibility(View.GONE);
            emptyMessage.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.VISIBLE);
        } else {
            Intent intent = new Intent(ArchiveTasksActivity.this, MainActivity.class);
            intent.putExtra("open_drawer", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    }
}
