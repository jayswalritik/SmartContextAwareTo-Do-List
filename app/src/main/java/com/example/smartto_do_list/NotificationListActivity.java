package com.example.smartto_do_list;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class NotificationListActivity extends AppCompatActivity implements NotificationAdapter.Listener {

    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private TaskDatabase db;

    // Selection mode flag
    private boolean isSelectionMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_list);

        recyclerView = findViewById(R.id.notificationsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NotificationAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        db = TaskDatabase.getInstance(this);

        loadAndGroupNotifications();
        attachSwipeToDelete();

        // Handle Select All checkbox clicks from adapter header
        adapter.updateHeaderControls(false, false); // Initially hide controls

        // Listen for Select All clicked inside adapter via listener method (defined below)
    }

    private void loadAndGroupNotifications() {
        new Thread(() -> {
            List<NotificationLog> allNotifications = db.notificationLogDao().getAllNotifications();

            long startOfToday = getStartOfTodayInMillis();

            List<NotificationListItem> groupedList = new ArrayList<>();

            List<NotificationLog> todayList = new ArrayList<>();
            List<NotificationLog> earlierList = new ArrayList<>();

            for (NotificationLog notification : allNotifications) {
                if (notification.getNotificationTime() >= startOfToday) {
                    todayList.add(notification);
                } else {
                    earlierList.add(notification);
                }
            }

            if (!todayList.isEmpty()) {
                groupedList.add(new NotificationListItem.HeaderItem("Today"));
                for (NotificationLog notif : todayList) {
                    groupedList.add(new NotificationListItem.NotificationItem(notif));
                }
            }

            if (!earlierList.isEmpty()) {
                groupedList.add(new NotificationListItem.HeaderItem("Earlier"));
                for (NotificationLog notif : earlierList) {
                    groupedList.add(new NotificationListItem.NotificationItem(notif));
                }
            }

            runOnUiThread(() -> {
                adapter.updateList(groupedList);
                // Reset selection mode and header controls when data reloads
                isSelectionMode = false;
                adapter.updateHeaderControls(false, false);
            });
        }).start();
    }

    private long getStartOfTodayInMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void attachSwipeToDelete() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false; // no move support
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                // Disable swipe if selection mode is active
                if (isSelectionMode) {
                    return 0;
                }
                return super.getMovementFlags(recyclerView, viewHolder);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                NotificationListItem item = adapter.getItem(position);

                // Only allow swipe to delete on notification items, not headers
                if (!(item instanceof NotificationListItem.NotificationItem)) {
                    adapter.notifyItemChanged(position); // revert swipe on headers
                    return;
                }

                NotificationLog notifToDelete = ((NotificationListItem.NotificationItem) item).getNotification();

                // Delete notification from DB in background
                new Thread(() -> {
                    db.notificationLogDao().delete(notifToDelete);
                    // Reload notifications after deletion
                    loadAndGroupNotifications();
                }).start();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive && dX < 0) {
                    Paint paint = new Paint();
                    paint.setColor(Color.RED);

                    View itemView = viewHolder.itemView;
                    c.drawRect(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom(), paint);

                    Drawable deleteIcon = ContextCompat.getDrawable(NotificationListActivity.this, R.drawable.ic_delete);
                    if (deleteIcon != null) {
                        int margin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                        int top = itemView.getTop() + margin;
                        int bottom = top + deleteIcon.getIntrinsicHeight();
                        int left = itemView.getRight() - margin - deleteIcon.getIntrinsicWidth();
                        int right = itemView.getRight() - margin;
                        deleteIcon.setBounds(left, top, right, bottom);
                        deleteIcon.draw(c);
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.4f; // swipe past 40% triggers deletion
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    // NotificationAdapter.Listener implementations

    @Override
    public void onSelectionCountChanged(int selectedCount, int totalCount) {
        isSelectionMode = selectedCount > 0;
        adapter.updateHeaderControls(isSelectionMode, adapter.areAllSelected());

        // Optional: update toolbar, action bar, or other UI here
    }

    @Override
    public void onDeleteSelected(List<NotificationLog> selectedNotifications) {
        if (selectedNotifications == null || selectedNotifications.isEmpty()) return;

        new Thread(() -> {
            for (NotificationLog notif : selectedNotifications) {
                db.notificationLogDao().delete(notif);
            }
            runOnUiThread(() -> {
                adapter.clearSelection();
                loadAndGroupNotifications();
            });
        }).start();
    }

    @Override
    public boolean isSelectionModeActive() {
        return isSelectionMode;
    }

    @Override
    public void onSelectionModeEntered() {
        isSelectionMode = true;
        // Optional: update UI if needed
    }

    @Override
    public void onSelectionModeExited() {
        isSelectionMode = false;
        adapter.clearSelection();
        adapter.updateHeaderControls(false, false);
        // Optional: update UI if needed
    }

    @Override
    public void onSelectAllClicked(boolean isChecked, int headerPosition) {
        List<Integer> idsUnderHeader = adapter.getNotificationIdsForHeader(headerPosition);
        if (idsUnderHeader == null) return;

        if (isChecked) {
            adapter.selectNotifications(idsUnderHeader);
        } else {
            adapter.deselectNotifications(idsUnderHeader);
        }

        int selectedCount = adapter.getSelectedNotifications().size();
        onSelectionCountChanged(selectedCount, adapter.getNotificationCount());
    }
    @Override
    public void onDeleteSelectedInSection(List<NotificationLog> selectedInSection) {
        if (selectedInSection == null || selectedInSection.isEmpty()) return;

        // Copy the deleted items
        List<NotificationLog> deletedCopy = new ArrayList<>(selectedInSection);

        // Delete from DB
        new Thread(() -> {
            for (NotificationLog notif : deletedCopy) {
                db.notificationLogDao().delete(notif);
            }

            runOnUiThread(() -> {
                adapter.clearSelection();
                loadAndGroupNotifications();

                // Show undo Snackbar
                View rootView = findViewById(android.R.id.content);
                Snackbar.make(rootView, deletedCopy.size() + " deleted", Snackbar.LENGTH_LONG)
                        .setAction("Undo", v -> {
                            // Restore the deleted items
                            new Thread(() -> {
                                for (NotificationLog notif : deletedCopy) {
                                    db.notificationLogDao().insert(notif);
                                }
                                runOnUiThread(this::loadAndGroupNotifications);
                            }).start();
                        })
                        .show();
            });
        }).start();
    }

}
