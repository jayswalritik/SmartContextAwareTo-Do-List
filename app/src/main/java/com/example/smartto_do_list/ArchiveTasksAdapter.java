package com.example.smartto_do_list;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArchiveTasksAdapter extends RecyclerView.Adapter<ArchiveTasksAdapter.ViewHolder> {

    public interface Listener {
        void onDelete(Task task);
        void onSelectionCountChanged(int selectedCount, int totalCount);
        boolean isSelectionModeActive();
        void onSelectTask();
        void onClearSelection();
    }

    private List<Task> tasks;
    private Listener listener;
    private Set<Integer> selectedTaskIds = new HashSet<>();

    private static final long TAP_THROTTLE_MS = 200;
    private final Map<Integer, Runnable> pendingRunnables = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TaskDao taskDao;
    private Context context;

    public ArchiveTasksAdapter(List<Task> tasks, TaskDao taskDao, Context context, Listener listener) {
        this.tasks = tasks;
        this.taskDao = taskDao;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ArchiveTasksAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.archivetasklist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArchiveTasksAdapter.ViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.titleTextView.setText(task.title);
        holder.taskCreatedDate.setText("" + task.date);
        holder.taskCompletedDate.setText("" + task.completedDate);

        // Set checkbox checked, since all tasks here are completed
        holder.checkBox.setOnCheckedChangeListener(null); // prevent unwanted triggers
        holder.checkBox.setChecked(true);

        // ✅ When unchecked, update status and remove from list
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                task.taskStatus = "pending";
                task.completedDate = "";

                new Thread(() -> {
                    taskDao.updateTaskStatusAndCompletedDate(task.id, "pending", "");

                    // Must use correct position tracking for removal on UI thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        int index = holder.getAdapterPosition();
                        if (index != RecyclerView.NO_POSITION) {
                            tasks.remove(index);
                            notifyItemRemoved(index);
                            notifyItemRangeChanged(index, tasks.size());

                            if (listener != null && tasks.isEmpty()) {
                                listener.onClearSelection();
                            }
                        }
                    });
                }).start();
            }
        });

        // Existing selection visuals
        MaterialCardView card = holder.cardView;

        if (selectedTaskIds.contains(task.id)) {
            int selectedColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.selectedtaskbackground);
            int strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.primaryColor);
            card.setCardBackgroundColor(selectedColor);
            card.setStrokeWidth(4);
            card.setStrokeColor(strokeColor);
        } else {
            int defaultColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.taskliststbackgroundcolor);
            card.setCardBackgroundColor(defaultColor);
            card.setStrokeWidth(0);
        }

        // Existing click and long click behavior
        holder.itemView.setOnClickListener(v -> {
            int id = task.id;

            Runnable existing = pendingRunnables.get(id);
            if (existing != null) {
                handler.removeCallbacks(existing);
                pendingRunnables.remove(id);
                return;
            }

            Runnable toggleAction = () -> {
                pendingRunnables.remove(id);
                if (isSelectionModeActive()) {
                    toggleSelection(task);
                }
            };

            pendingRunnables.put(id, toggleAction);
            handler.postDelayed(toggleAction, TAP_THROTTLE_MS);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!selectedTaskIds.contains(task.id)) {
                toggleSelection(task);
            }
            if (listener != null) listener.onSelectTask();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public boolean isSelectionModeActive() {
        return listener != null && listener.isSelectionModeActive();
    }

    public void toggleSelection(Task task) {
        int id = task.id;
        if (selectedTaskIds.contains(id)) {
            selectedTaskIds.remove(id);
        } else {
            selectedTaskIds.add(id);
        }
        notifyDataSetChanged();

        if (listener != null) {
            int selectedCount = selectedTaskIds.size();
            int totalCount = getItemCount();
            listener.onSelectionCountChanged(selectedCount, totalCount);
            if (selectedCount == 0) {
                listener.onClearSelection();
            }
        }
    }

    public void clearSelection() {
        selectedTaskIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(0, getItemCount());
            listener.onClearSelection();
        }
    }

    public void selectAll() {
        for (Task task : tasks) {
            selectedTaskIds.add(task.id);
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(selectedTaskIds.size(), getItemCount());
        }
    }

    public List<Task> getSelectedTasks() {
        List<Task> selected = new ArrayList<>();
        for (Task task : tasks) {
            if (selectedTaskIds.contains(task.id)) {
                selected.add(task);
            }
        }
        return selected;
    }

    public void updateList(List<Task> newList) {
        this.tasks = newList;
        selectedTaskIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(0, getItemCount());
            listener.onClearSelection();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, taskCreatedDate, taskCompletedDate;
        MaterialCardView cardView;

        CheckBox checkBox;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            titleTextView = itemView.findViewById(R.id.taskTitleTextView);
            taskCreatedDate = itemView.findViewById(R.id.taskcreateddatetext);
            taskCompletedDate = itemView.findViewById(R.id.taskcompleteddatetext);
            checkBox = itemView.findViewById(R.id.checkbox);

        }
    }

    public Task getTaskAt(int position) {
        return tasks.get(position);
    }

    public List<Task> getCurrentList() {
        return tasks;
    }



}
