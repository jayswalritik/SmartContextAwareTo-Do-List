package com.example.smartto_do_list;

import static androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class SavedLocationsActivity extends AppCompatActivity {
    private ImageButton deleteAllButton, backButton;
    private TextView description;
    private CheckBox selectAllCheckBox;
    private RelativeLayout actionRow;
    private RecyclerView recyclerView;
    private SavedLocationsAdapter adapter;
    private List<SavedLocations> locations = new ArrayList<>();

    private SavedLocationsDao savedLocationsDao;
    private boolean selectionModeActive = false;

    private CompoundButton.OnCheckedChangeListener selectAllListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_locations);

        recyclerView = findViewById(R.id.savedLocationsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        description = findViewById(R.id.emptyMessage);

        backButton = findViewById(R.id.backiconbutton);
        backButton.setOnClickListener(v -> onBackPressed());
        selectAllCheckBox = findViewById(R.id.selectallcheckbox);

        // Listener for select all checkbox
        selectAllListener = (buttonView, isChecked) -> {
            if (isChecked) {
                adapter.selectAll();
            } else {
                adapter.clearSelection();
            }
        };
        selectAllCheckBox.setOnCheckedChangeListener(selectAllListener);

        actionRow = findViewById(R.id.actionrow);
        deleteAllButton = findViewById(R.id.deleteallicon);

        adapter = new SavedLocationsAdapter(locations, new SavedLocationsAdapter.Listener() {
            @Override
            public void onEdit(SavedLocations location) {
                if (!selectionModeActive) {
                    showEditDialog(location);
                }
            }

            @Override
            public void onDelete(SavedLocations location) {
                confirmAndDeleteLocation(location, locations.indexOf(location), recyclerView);
            }

            @Override
            public void onSelectionCountChanged(int selectedCount, int totalCount) {
                selectAllCheckBox.setOnCheckedChangeListener(null);
                selectAllCheckBox.setChecked(selectedCount == totalCount && totalCount > 0);
                selectAllCheckBox.setOnCheckedChangeListener(selectAllListener);

                if (selectedCount > 0 && !selectionModeActive) {
                    selectionModeActive = true;
                    description.setVisibility(View.GONE);
                    actionRow.setVisibility(View.VISIBLE);
                } else if (selectedCount == 0 && selectionModeActive) {
                    selectionModeActive = false;
                    description.setVisibility(View.VISIBLE);
                    actionRow.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean isSelectionModeActive() {
                return selectionModeActive;
            }

            @Override
            public void onSelectLocation() {
                if (!selectionModeActive) {
                    selectionModeActive = true;
                    // TODO: Show selection UI if needed
                }
            }

            @Override
            public void onClearSelection() {
                if (selectionModeActive) {
                    selectionModeActive = false;
                    selectAllCheckBox.setOnCheckedChangeListener(null);
                    selectAllCheckBox.setChecked(false);
                    selectAllCheckBox.setOnCheckedChangeListener(selectAllListener);
                    // TODO: Hide selection UI if needed
                }
            }
        });

        recyclerView.setAdapter(adapter);

        savedLocationsDao = TaskDatabase.getInstance(this).savedLocationDao();

        loadLocations();
        attachSwipeToDelete(recyclerView);

        deleteAllButton.setOnClickListener(v -> {
            List<SavedLocations> selectedLocations = adapter.getSelectedLocations();

            if (selectedLocations.isEmpty()) {
                Toast.makeText(this, "No locations selected", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Delete Selected Locations")
                    .setMessage("Are you sure you want to delete " + selectedLocations.size() + " location(s)?\n\nThis will also unlink them from any tasks.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        List<SavedLocations> deletedLocations = new ArrayList<>(selectedLocations);

                        locations.removeAll(deletedLocations);
                        adapter.clearSelection();
                        adapter.notifyDataSetChanged();

                        new Thread(() -> {
                            for (SavedLocations loc : deletedLocations) {
                                savedLocationsDao.delete(loc);
                                TaskDatabase.getInstance(this)
                                        .taskDao()
                                        .clearTasksWithLocationId(loc.getId());
                            }

                            runOnUiThread(() -> {
                                Snackbar.make(recyclerView, "Deleted " + deletedLocations.size() + " location(s)", Snackbar.LENGTH_LONG)
                                        .setAction("UNDO", undoView -> {
                                            new Thread(() -> {
                                                for (SavedLocations loc : deletedLocations) {
                                                    savedLocationsDao.insert(loc);
                                                }
                                                runOnUiThread(this::loadLocations);
                                            }).start();
                                        }).show();
                            });
                        }).start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
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

    private void confirmAndDeleteLocation(SavedLocations location, int position, RecyclerView recyclerView) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Location")
                .setMessage("Are you sure you want to delete \"" + location.getLabel() + "\"?\n\n" +
                        "This will also unlink it from any associated tasks.")
                .setPositiveButton("Delete", (dialog, which) -> {
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

    private void attachSwipeToDelete(RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                if (selectionModeActive) {
                    adapter.notifyItemChanged(viewHolder.getAdapterPosition());
                    return;
                }

                int position = viewHolder.getAdapterPosition();
                SavedLocations locationToDelete = locations.get(position);
                confirmAndDeleteLocation(locationToDelete, position, recyclerView);
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                if (selectionModeActive || dX >= 0) {
                    super.onChildDraw(c, recyclerView, viewHolder, 0, dY, actionState, false);
                    return;
                }

                View itemView = viewHolder.itemView;
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                c.drawRect(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom(), paint);

                Drawable deleteIcon = ContextCompat.getDrawable(SavedLocationsActivity.this, R.drawable.deleteicon);
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
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (selectionModeActive) {
                    return 0;
                }
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

    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (selectionModeActive) {
            selectionModeActive = false;
            adapter.clearSelection();
            actionRow.setVisibility(View.GONE);
            description.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.VISIBLE);
        } else {
            Intent intent = new Intent(SavedLocationsActivity.this, MainActivity.class);
            intent.putExtra("open_drawer", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    }


}
