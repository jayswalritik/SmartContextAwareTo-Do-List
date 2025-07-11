package com.example.smartto_do_list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SavedLocationsAdapter2 extends RecyclerView.Adapter<SavedLocationsAdapter2.ViewHolder> {

    public interface Listener {
        void onEdit(SavedLocations location);
        void onDelete(SavedLocations location);
    }

    private List<SavedLocations> locations;
    private Listener listener;

    public SavedLocationsAdapter2(List<SavedLocations> locations, Listener listener) {
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

        // Edit button click
        holder.editButton.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(loc);
        });

        // Whole item click does the same as edit
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(loc);
        });
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    public void updateList(List<SavedLocations> newList) {
        locations = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView labelTextView;
        ImageButton editButton;

        ViewHolder(View itemView) {
            super(itemView);
            labelTextView = itemView.findViewById(R.id.labelTextView);
            editButton = itemView.findViewById(R.id.editButton);
        }
    }
}
