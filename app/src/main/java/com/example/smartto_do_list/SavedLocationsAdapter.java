package com.example.smartto_do_list;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SavedLocationsAdapter extends RecyclerView.Adapter<SavedLocationsAdapter.ViewHolder> {

    public interface Listener {
        void onEdit(SavedLocations location);
        void onDelete(SavedLocations location);

        void onSelectionCountChanged(int selectedCount, int totalCount);
        boolean isSelectionModeActive();
        void onSelectLocation();
        void onClearSelection();
    }

    private List<SavedLocations> locations;
    private Listener listener;
    private Set<Integer> selectedLocationIds = new HashSet<>();

    private static final long TAP_THROTTLE_MS = 200;
    private final Map<Integer, Runnable> pendingRunnables = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public SavedLocationsAdapter(List<SavedLocations> locations, Listener listener) {
        this.locations = locations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.savedlocationslist, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavedLocations loc = locations.get(position);
        holder.labelTextView.setText(loc.getLabel());

        MaterialCardView cardView = holder.cardView;

        // Update card appearance based on selection
        if (selectedLocationIds.contains(loc.getId())) {
            int selectedColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.selectedtaskbackground);
            int strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.primaryColor);
            cardView.setCardBackgroundColor(selectedColor);
            cardView.setStrokeWidth(4);
            cardView.setStrokeColor(strokeColor);
        } else {
            int defaultColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.taskliststbackgroundcolor);
            cardView.setCardBackgroundColor(defaultColor);
            cardView.setStrokeWidth(0);
        }

        // Set edit button visibility based on selection mode
        if (isSelectionModeActive()) {
            holder.editButton.setVisibility(View.GONE);
        } else {
            holder.editButton.setVisibility(View.VISIBLE);
        }

        // Handle edit button click
        holder.editButton.setOnClickListener(v -> {
            if (!isSelectionModeActive()) {
                if (listener != null) listener.onEdit(loc);
            } else {
                toggleSelection(loc);
            }
        });

        // Handle single tap with delay-based throttle
        holder.itemView.setOnClickListener(v -> {
            int id = loc.getId();

            Runnable existing = pendingRunnables.get(id);
            if (existing != null) {
                handler.removeCallbacks(existing);
                pendingRunnables.remove(id);
                return;
            }

            Runnable toggleAction = () -> {
                pendingRunnables.remove(id);
                if (isSelectionModeActive()) {
                    toggleSelection(loc);
                } else {
                    if (listener != null) listener.onEdit(loc);
                }
            };

            pendingRunnables.put(id, toggleAction);
            handler.postDelayed(toggleAction, TAP_THROTTLE_MS);
        });

        // Handle long press for selection
        holder.itemView.setOnLongClickListener(v -> {
            if (!selectedLocationIds.contains(loc.getId())) {
                toggleSelection(loc);
            }
            if (listener != null) listener.onSelectLocation();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    private boolean isSelectionModeActive() {
        return listener != null && listener.isSelectionModeActive();
    }

    public void toggleSelection(SavedLocations location) {
        int id = location.getId();
        if (selectedLocationIds.contains(id)) {
            selectedLocationIds.remove(id);
        } else {
            selectedLocationIds.add(id);
        }
        notifyDataSetChanged();

        if (listener != null) {
            int selectedCount = selectedLocationIds.size();
            int totalCount = getTotalCount();
            listener.onSelectionCountChanged(selectedCount, totalCount);
            if (selectedCount == 0) {
                listener.onClearSelection();
            }
        }
    }

    public void clearSelection() {
        selectedLocationIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(0, getTotalCount());
            listener.onClearSelection();
        }
    }

    public void selectAll() {
        for (SavedLocations loc : locations) {
            selectedLocationIds.add(loc.getId());
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(selectedLocationIds.size(), getTotalCount());
        }
    }

    public boolean areAllSelected() {
        return locations.size() > 0 && selectedLocationIds.size() == locations.size();
    }

    public int getTotalCount() {
        return locations.size();
    }

    public Set<Integer> getSelectedLocationIds() {
        return new HashSet<>(selectedLocationIds);
    }

    public List<SavedLocations> getSelectedLocations() {
        List<SavedLocations> selected = new ArrayList<>();
        for (SavedLocations loc : locations) {
            if (selectedLocationIds.contains(loc.getId())) {
                selected.add(loc);
            }
        }
        return selected;
    }

    public void updateList(List<SavedLocations> newList) {
        this.locations = newList;
        selectedLocationIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(0, getTotalCount());
            listener.onClearSelection();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView labelTextView;
        ImageButton editButton;
        MaterialCardView cardView;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            labelTextView = itemView.findViewById(R.id.labelTextView);
            editButton = itemView.findViewById(R.id.editButton);
        }
    }
    public List<SavedLocations> getCurrentList() {
        return locations;
    }

}
