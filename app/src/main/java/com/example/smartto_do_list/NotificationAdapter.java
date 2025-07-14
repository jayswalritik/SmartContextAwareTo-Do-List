package com.example.smartto_do_list;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.*;

public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onSelectionCountChanged(int selectedCount, int totalCount);
        void onDeleteSelected(List<NotificationLog> selectedNotifications); // can be kept if used elsewhere
        void onDeleteSelectedInSection(List<NotificationLog> selectedInSection); // new
        boolean isSelectionModeActive();
        void onSelectionModeEntered();
        void onSelectionModeExited();
        void onSelectAllClicked(boolean isChecked, int headerPosition);
    }

    private List<NotificationListItem> items;
    private Listener listener;
    private Set<Integer> selectedNotificationIds = new HashSet<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    private final Map<Integer, Runnable> pendingRunnables = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long TAP_THROTTLE_MS = 200;

    private final List<HeaderViewHolder> headerViewHolders = new ArrayList<>();
    private final Map<Integer, List<Integer>> headerToNotificationIdsMap = new HashMap<>();

    public NotificationAdapter(List<NotificationListItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTitle;
        CheckBox selectAllCheckbox;
        ImageButton deleteIcon;

        HeaderViewHolder(View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(R.id.headerTitle);
            selectAllCheckbox = itemView.findViewById(R.id.selectAllCheckbox);
            deleteIcon = itemView.findViewById(R.id.deleteIcon);
        }
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView taskTitleTextView;
        TextView notificationTimeTextView;
        MaterialCardView notificationCard;

        NotificationViewHolder(View itemView) {
            super(itemView);
            taskTitleTextView = itemView.findViewById(R.id.notificationTitle);
            notificationTimeTextView = itemView.findViewById(R.id.notificationTime);
            notificationCard = itemView.findViewById(R.id.notificationCard);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == NotificationListItem.TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.notification_item_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false);
            return new NotificationViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);

        if (viewType == NotificationListItem.TYPE_HEADER) {
            NotificationListItem.HeaderItem headerItem = (NotificationListItem.HeaderItem) items.get(position);
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;

            headerHolder.headerTitle.setText(headerItem.getHeaderTitle());

            if (!headerViewHolders.contains(headerHolder)) {
                headerViewHolders.add(headerHolder);
            }

            boolean isSelectionMode = listener != null && listener.isSelectionModeActive();
            updateHeaderControls(isSelectionMode, areAllSelected());

        } else {
            NotificationListItem.NotificationItem notificationItem =
                    (NotificationListItem.NotificationItem) items.get(position);
            NotificationLog notification = notificationItem.getNotification();

            NotificationViewHolder vh = (NotificationViewHolder) holder;
            vh.taskTitleTextView.setText(notification.getTaskTitle());
            vh.notificationTimeTextView.setText(sdf.format(new Date(notification.getNotificationTime())));

            if (selectedNotificationIds.contains(notification.getId())) {
                int selectedColor = ContextCompat.getColor(vh.notificationCard.getContext(), R.color.selectedtaskbackground);
                int strokeColor = ContextCompat.getColor(vh.notificationCard.getContext(), R.color.primaryColor);
                vh.notificationCard.setCardBackgroundColor(selectedColor);
                vh.notificationCard.setStrokeWidth(4);
                vh.notificationCard.setStrokeColor(strokeColor);
            } else {
                int defaultColor = ContextCompat.getColor(vh.notificationCard.getContext(), R.color.taskliststbackgroundcolor);
                vh.notificationCard.setCardBackgroundColor(defaultColor);
                vh.notificationCard.setStrokeWidth(0);
            }

            holder.itemView.setOnClickListener(v -> {
                int id = notification.getId();
                Runnable existing = pendingRunnables.get(id);
                if (existing != null) {
                    handler.removeCallbacks(existing);
                    pendingRunnables.remove(id);
                    return;
                }

                Runnable action = () -> {
                    pendingRunnables.remove(id);
                    if (isSelectionMode()) {
                        toggleSelection(notification);
                    }
                };

                pendingRunnables.put(id, action);
                handler.postDelayed(action, TAP_THROTTLE_MS);
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode()) {
                    listener.onSelectionModeEntered();
                }
                toggleSelection(notification);
                return true;
            });
        }
    }

    private boolean isSelectionMode() {
        return listener != null && listener.isSelectionModeActive();
    }

    public void toggleSelection(NotificationLog notification) {
        int id = notification.getId();
        if (selectedNotificationIds.contains(id)) {
            selectedNotificationIds.remove(id);
        } else {
            selectedNotificationIds.add(id);
        }

        notifyDataSetChanged();
        updateHeaderControls(isSelectionMode(), false); // << UPDATE HERE

        if (listener != null) {
            int selectedCount = selectedNotificationIds.size();
            int totalCount = getNotificationCount();
            listener.onSelectionCountChanged(selectedCount, totalCount);
            if (selectedCount == 0) {
                listener.onSelectionModeExited();
            }
        }
    }

    public void selectAll() {
        for (NotificationListItem item : items) {
            if (item instanceof NotificationListItem.NotificationItem) {
                selectedNotificationIds.add(((NotificationListItem.NotificationItem) item).getNotification().getId());
            }
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(selectedNotificationIds.size(), getNotificationCount());
        }
    }

    public void clearSelection() {
        selectedNotificationIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(0, getNotificationCount());
        }
    }

    public boolean areAllSelected() {
        return selectedNotificationIds.size() == getNotificationCount() && getNotificationCount() > 0;
    }

    public int getNotificationCount() {
        int count = 0;
        for (NotificationListItem item : items) {
            if (item instanceof NotificationListItem.NotificationItem) count++;
        }
        return count;
    }

    public List<NotificationLog> getSelectedNotifications() {
        List<NotificationLog> selected = new ArrayList<>();
        for (NotificationListItem item : items) {
            if (item instanceof NotificationListItem.NotificationItem) {
                NotificationLog notif = ((NotificationListItem.NotificationItem) item).getNotification();
                if (selectedNotificationIds.contains(notif.getId())) {
                    selected.add(notif);
                }
            }
        }
        return selected;
    }

    public NotificationListItem getItem(int position) {
        return items.get(position);
    }

    public void updateList(List<NotificationListItem> newItems) {
        this.items = newItems;
        selectedNotificationIds.clear();
        headerViewHolders.clear();
        notifyDataSetChanged();

        buildHeaderNotificationMap();
        updateHeaderControls(false, false);
    }

    private void buildHeaderNotificationMap() {
        headerToNotificationIdsMap.clear();
        Integer currentHeaderPos = null;
        List<Integer> currentList = null;

        for (int i = 0; i < items.size(); i++) {
            NotificationListItem item = items.get(i);
            if (item instanceof NotificationListItem.HeaderItem) {
                currentHeaderPos = i;
                currentList = new ArrayList<>();
                headerToNotificationIdsMap.put(currentHeaderPos, currentList);
            } else if (item instanceof NotificationListItem.NotificationItem && currentList != null) {
                NotificationLog notif = ((NotificationListItem.NotificationItem) item).getNotification();
                currentList.add(notif.getId());
            }
        }
    }

    public List<Integer> getNotificationIdsForHeader(int headerPosition) {
        return headerToNotificationIdsMap.get(headerPosition);
    }

    public void selectNotifications(List<Integer> ids) {
        selectedNotificationIds.addAll(ids);
        notifyDataSetChanged();
        updateHeaderControls(true, false);
    }

    public void deselectNotifications(List<Integer> ids) {
        selectedNotificationIds.removeAll(ids);
        notifyDataSetChanged();
        updateHeaderControls(true, false);
    }

    public void updateHeaderControls(boolean isSelectionMode, boolean unusedGlobalFlag) {
        for (HeaderViewHolder headerHolder : headerViewHolders) {
            int headerPosition = headerHolder.getAdapterPosition();

            headerHolder.selectAllCheckbox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            headerHolder.deleteIcon.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);

            if (headerPosition != RecyclerView.NO_POSITION) {
                updateHeaderCheckboxState(headerPosition, headerHolder);
            }

            headerHolder.deleteIcon.setOnClickListener(v -> {
                if (listener != null && headerPosition != RecyclerView.NO_POSITION) {
                    List<NotificationLog> sectionSelected = getSelectedNotificationsForHeader(headerPosition);
                    listener.onDeleteSelectedInSection(sectionSelected);
                }
            });
        }
    }

    private void updateHeaderCheckboxState(int headerPosition, HeaderViewHolder headerHolder) {
        List<Integer> ids = headerToNotificationIdsMap.get(headerPosition);
        if (ids == null || ids.isEmpty()) return;

        boolean allSelected = true;
        for (int id : ids) {
            if (!selectedNotificationIds.contains(id)) {
                allSelected = false;
                break;
            }
        }

        headerHolder.selectAllCheckbox.setOnCheckedChangeListener(null);
        headerHolder.selectAllCheckbox.setChecked(allSelected);

        headerHolder.selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onSelectAllClicked(isChecked, headerPosition);
            }
        });
    }

    public List<NotificationLog> getSelectedNotificationsForHeader(int headerPosition) {
        List<Integer> ids = headerToNotificationIdsMap.get(headerPosition);
        if (ids == null) return Collections.emptyList();

        List<NotificationLog> selected = new ArrayList<>();
        for (NotificationListItem item : items) {
            if (item instanceof NotificationListItem.NotificationItem) {
                NotificationLog notif = ((NotificationListItem.NotificationItem) item).getNotification();
                if (selectedNotificationIds.contains(notif.getId()) && ids.contains(notif.getId())) {
                    selected.add(notif);
                }
            }
        }
        return selected;
    }


}
