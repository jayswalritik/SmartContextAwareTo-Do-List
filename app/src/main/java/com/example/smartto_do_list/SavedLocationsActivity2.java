package com.example.smartto_do_list;

import static androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class SavedLocationsActivity2 extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SavedLocationsAdapter2 adapter;  // Use SavedLocationsAdapter2 (no selection)
    private List<SavedLocations> locations = new ArrayList<>();

    private SavedLocationsDao savedLocationsDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_locations);

        recyclerView = findViewById(R.id.savedLocationsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SavedLocationsAdapter2(locations, new SavedLocationsAdapter2.Listener() {
            @Override
            public void onEdit(SavedLocations location) {
                showEditDialog(location);
            }

            @Override
            public void onDelete(SavedLocations location) {
                // No explicit delete button in adapter, but kept interface method for future use
                // For now, delete handled via swipe gesture
            }
        });
        recyclerView.setAdapter(adapter);

        savedLocationsDao = TaskDatabase.getInstance(this).savedLocationDao();

        loadLocations();
        attachSwipeToDelete(recyclerView);
    }

    private void loadLocations() {
        new Thread(() -> {
            List<SavedLocations> loaded = savedLocationsDao.getAllSavedLocations();
            runOnUiThread(() -> {
                locations.clear();
                locations.addAll(loaded);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void attachSwipeToDelete(RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                SavedLocations locationToDelete = locations.get(position);

                confirmAndDeleteLocation(locationToDelete, position, recyclerView);
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    c.drawRect(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom(), paint);

                    Drawable deleteIcon = ContextCompat.getDrawable(SavedLocationsActivity2.this, R.drawable.deleteicon);
                    if (deleteIcon != null) {
                        int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                        int top = itemView.getTop() + iconMargin;
                        int bottom = top + deleteIcon.getIntrinsicHeight();
                        int left = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
                        int right = itemView.getRight() - iconMargin;
                        deleteIcon.setBounds(left, top, right, bottom);
                        deleteIcon.draw(c);
                    }
                }

                float maxSwipe = recyclerView.getWidth() * 0.7f;
                itemView.setTranslationX(Math.max(dX, -maxSwipe));
            }

            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(0, ItemTouchHelper.LEFT);
            }

            @Override
            public float getSwipeThreshold(RecyclerView.ViewHolder viewHolder) {
                return 0.45f;
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                return defaultValue * 35f;
            }

            @Override
            public float getSwipeVelocityThreshold(float defaultValue) {
                return defaultValue * 2f;
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    private void confirmAndDeleteLocation(SavedLocations location, int position, RecyclerView recyclerView) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Location")
                .setMessage("Are you sure you want to delete \"" + location.getLabel() + "\"?\n\n" +
                        "This will also unlink it from any associated tasks.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Remove from list & notify adapter
                    locations.remove(position);
                    adapter.notifyItemRemoved(position);

                    new Thread(() -> {
                        savedLocationsDao.delete(location);
                        TaskDatabase.getInstance(this)
                                .taskDao()
                                .clearTasksWithLocationId(location.getId());

                        runOnUiThread(() -> {
                            Snackbar.make(recyclerView, "Location deleted", Snackbar.LENGTH_LONG)
                                    .setAction("UNDO", v -> {
                                        new Thread(() -> {
                                            savedLocationsDao.insert(location);
                                            runOnUiThread(this::loadLocations);
                                        }).start();
                                    })
                                    .show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", (dialog, which) -> adapter.notifyItemChanged(position))
                .setCancelable(false)
                .show();
    }

    private void showEditDialog(SavedLocations location) {
        runOnUiThread(() -> {
            EditText input = new EditText(this);
            input.setText(location.getLabel());

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Edit Location Name")
                    .setView(input)
                    .setCancelable(false)
                    .setPositiveButton("Save", null)
                    .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                    .create();

            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String newLabel = input.getText().toString().trim();

                    if (newLabel.isEmpty()) {
                        input.setError("Name cannot be empty");
                        return;
                    }

                    new Thread(() -> {
                        SavedLocations existing = savedLocationsDao.getLocationByLabel(newLabel);

                        runOnUiThread(() -> {
                            if (existing != null && existing.getId() != location.getId()) {
                                input.setError("Location name already exists. Choose a different name.");
                            } else {
                                final String oldLabel = location.getLabel();
                                location.label = newLabel;

                                new Thread(() -> {
                                    savedLocationsDao.update(location);

                                    runOnUiThread(() -> {
                                        Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show();
                                        loadLocations();

                                        Snackbar.make(recyclerView, "Location updated", Snackbar.LENGTH_LONG)
                                                .setAction("UNDO", v1 -> {
                                                    new Thread(() -> {
                                                        location.label = oldLabel;
                                                        savedLocationsDao.update(location);
                                                        runOnUiThread(this::loadLocations);
                                                    }).start();
                                                })
                                                .show();
                                    });
                                }).start();

                                dialog.dismiss();
                            }
                        });
                    }).start();
                });
            });

            dialog.show();
        });
    }
}
