package com.example.smartto_do_list;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskAdapter2 extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_TASK = 1;

    private final Context context;
    private final TaskActionListener listener;
    private String highlightQuery = "";

    // Use list of the nested TaskListItem type
    private List<TaskListItem> items = new ArrayList<>();
    Set<Integer> selectedTaskIds = new HashSet<>();
    private Map<Integer, String> locationMap = new HashMap<>();




    public interface TaskActionListener {
        void onView(Task task);
        void onEdit(Task task);
        void onDelete(Task task);
        void onTaskCompletionChanged(Task task, boolean isCompleted);
        void onSelectTask();
        boolean isSelectionModeActive();  // NEW method from main Activity to get if selection mode is active or not
        void onClearSelection();
        void onSelectionCountChanged(int selectedCount, int totalCount);

    }

    public TaskAdapter2(Context context, List<Task> tasks, TaskActionListener listener) {
        this.context = context;
        this.listener = listener;
        this.items = new ArrayList<>();
        updateTasks(tasks);
    }
    public void setHighlightQuery(String query) {
        this.highlightQuery = query != null ? query.trim() : "";
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof TaskSectionHeader) return VIEW_TYPE_HEADER;
        else return VIEW_TYPE_TASK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);

        View view = inflater.inflate(R.layout.tasklist, parent, false);
        return new TaskViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TaskListItem item = items.get(position);


        if (holder instanceof TaskViewHolder) {
            Task task = ((TaskItem) item).task;
            TaskViewHolder taskHolder = (TaskViewHolder) holder;

            // Format date
            SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String formattedDate;
            try {
                formattedDate = "📅 " + dbDateFormat.format(task.getDate());
            } catch (Exception e) {
                formattedDate = task.date != null && !task.date.isEmpty() ? "📅 " + task.date : "📅 No Date";
            }

            // Format time safely
            String formattedTime = task.getTime() != null && !task.getTime().isEmpty()
                    ? "🕐 " + task.getTime()
                    : "🕐 N/A";


            // Format category safely with icon
            String formattedCategory = task.getCategory() != null && !task.getCategory().isEmpty()
                    ? "📋 " + task.getCategory()
                    : "📋 N/A";

            String locationLabel = "📍 " + task.getLocationLabel(locationMap);


            // Highlight text
            highlightText(taskHolder.title, task.title, highlightQuery);
            highlightText(taskHolder.category, formattedCategory, highlightQuery);
            highlightText(taskHolder.time, formattedTime, highlightQuery);
            highlightText(taskHolder.date, formattedDate, highlightQuery);
            highlightText(taskHolder.location, locationLabel, highlightQuery);


            // Set priority dot color
            View dot = taskHolder.itemView.findViewById(R.id.prioritydot);
            int dotColor;
            if (task.priority != null) {
                switch (task.priority.toLowerCase()) {
                    case "high":
                        dotColor = Color.parseColor("#D32F2F");
                        break;
                    case "medium":
                        dotColor = Color.parseColor("#FBC02D");
                        break;
                    default:
                        dotColor = Color.parseColor("#388E3C");
                        break;
                }
            } else {
                dotColor = Color.parseColor("#388E3C");
            }
            dot.getBackground().setTint(dotColor);

            // Checkbox logic
            taskHolder.checkBox.setOnCheckedChangeListener(null);
            taskHolder.checkBox.setChecked("completed".equalsIgnoreCase(task.taskStatus));
            taskHolder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onTaskCompletionChanged(task, isChecked);
                    // Optional: disable checkbox briefly to prevent double taps
                    buttonView.setEnabled(false);
                    buttonView.postDelayed(() -> buttonView.setEnabled(true), 500);
                }
            });

            // Determine if task is overdue by comparing dates


            // Gesture detector setup
            GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (listener.isSelectionModeActive()) {
                        int position = holder.getAdapterPosition();
                        TaskListItem item = items.get(position);
                        if (item instanceof TaskItem) {
                            Task task = ((TaskItem) item).task;
                            toggleSelection(task);  // toggle by Task object
                        }
                    } else {
                        listener.onView(task);
                    }
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (!listener.isSelectionModeActive()) {
                        listener.onEdit(task);
                    }
                    return true; // Still consume the event
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    toggleSelection(task);  // Pass Task here, not position
                    listener.onSelectTask();
                }
            });
            taskHolder.itemView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
            MaterialCardView cardView = (MaterialCardView) holder.itemView;

            if (selectedTaskIds.contains(task.getId())) {
                // task is selected
                int selectedColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.selectedtaskbackground);
                int strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.primaryColor);
                cardView.setCardBackgroundColor(selectedColor);
                cardView.setStrokeWidth(4);
                cardView.setStrokeColor(strokeColor);
                //cardView.setStrokeColor(Color.parseColor("#2196F3"));
            } else {
                // Use the same background as defined in XML
                int defaultColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.taskliststbackgroundcolor);
                cardView.setCardBackgroundColor(defaultColor);
                cardView.setStrokeWidth(0);
            }

        }
    }

    private void highlightText(TextView textView, String text, String query) {
        if (text == null || text.isEmpty()) {
            textView.setText("");
            return;
        }

        if (query != null && !query.isEmpty()) {
            SpannableString spannable = new SpannableString(text);
            Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                spannable.setSpan(new BackgroundColorSpan(Color.YELLOW), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            textView.setText(spannable);
        } else {
            textView.setText(text);
        }
    }
    public TaskListItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void setItems(List<TaskListItem> newItems) {
        List<TaskListItem> filtered = new ArrayList<>();
        for (TaskListItem item : newItems) {
            if (item instanceof TaskItem) {
                filtered.add(item);
            }
        }
        this.items = filtered;
        notifyDataSetChanged();
    }

    // --- ViewHolders ---


    public static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView title, date, time, category, location;
        CheckBox checkBox;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tasktitleview);
            date = itemView.findViewById(R.id.dateview);
            time = itemView.findViewById(R.id.timeview);
            location = itemView.findViewById(R.id.locationView);
            category = itemView.findViewById(R.id.categoryview);
            checkBox = itemView.findViewById(R.id.checkbox);
        }
    }
    // --- Nested Supporting classes ---

    public interface TaskListItem { }

    public static class TaskSectionHeader implements TaskListItem {
        public final String title;

        public TaskSectionHeader(String title) {
            this.title = title;
        }
    }

    public static class TaskItem implements TaskListItem {
        public final Task task;

        public TaskItem(Task task) {
            this.task = task;
        }
    }

    public void updateTasks(List<Task> newTasks) {
        // Clear the current list
        items.clear();

        // Add all new tasks wrapped in your internal item class (e.g., TaskItem)
        for (Task task : newTasks) {
            items.add(new TaskItem(task));
        }

        // Notify adapter that data changed (refresh whole list)
        notifyDataSetChanged();
    }

    public void toggleSelection(Task task) {
        if (selectedTaskIds.contains(task.getId())) {
            selectedTaskIds.remove(task.getId());
        } else {
            selectedTaskIds.add(task.getId());
        }

        notifyDataSetChanged();

        if (listener != null) {
            int selectedCount = selectedTaskIds.size();
            int totalCount = getTotalTaskCount();
            listener.onSelectionCountChanged(selectedCount, totalCount);

            // 👇 Exit selection mode if no task is selected
            if (selectedCount == 0) {
                listener.onClearSelection();
            }
        }
    }

    private int getTotalTaskCount() {
        int count = 0;
        for (TaskListItem item : items) {
            if (item instanceof TaskItem) count++;
        }
        return count;
    }

    public List<Task> getSelectedTasks() {
        List<Task> selectedTasks = new ArrayList<>();
        for (TaskListItem item : items) {
            if (item instanceof TaskItem) {
                Task task = ((TaskItem) item).task;
                if (selectedTaskIds.contains(task.getId())) {
                    selectedTasks.add(task);
                }
            }
        }
        return selectedTasks;
    }

    public void clearSelection() {
        selectedTaskIds.clear();           // Clear the list of selected task IDs
        notifyDataSetChanged();           // Refresh the RecyclerView UI

        if (listener != null) {
            listener.onSelectionCountChanged(0, getTotalTaskCount());  // Update the radio button state
            listener.onClearSelection();  // Optional: handle UI like hiding selection toolbar
        }
    }
    public void setLocationMap(Map<Integer, String> map) {
        this.locationMap = map != null ? map : new HashMap<>();
        notifyDataSetChanged(); // So RecyclerView refreshes
    }

    public boolean areAllSelectedInCurrentTab() {
        int totalTasksInTab = 0;
        int selectedTasksInTab = 0;

        for (TaskListItem item : items) {
            if (item instanceof TaskItem) {
                totalTasksInTab++;
                Task task = ((TaskItem) item).task;
                if (selectedTaskIds.contains(task.getId())) {
                    selectedTasksInTab++;
                }
            }
        }
        return totalTasksInTab > 0 && selectedTasksInTab == totalTasksInTab;
    }
    public void selectAllInCurrentTab() {
        for (TaskListItem item : items) {
            if (item instanceof TaskItem) {
                Task task = ((TaskItem) item).task;
                selectedTaskIds.add(task.getId());
            }
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(selectedTaskIds.size(), getTotalTaskCount());
        }
    }

    public void clearSelectionInCurrentTab() {
        for (TaskListItem item : items) {
            if (item instanceof TaskItem) {
                Task task = ((TaskItem) item).task;
                selectedTaskIds.remove(task.getId());
            }
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(selectedTaskIds.size(), getTotalTaskCount());
        }
    }
    public Set<Integer> getSelectedTaskIds() {
        return new HashSet<>(selectedTaskIds);
    }



}
