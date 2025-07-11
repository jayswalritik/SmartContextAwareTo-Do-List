package com.example.smartto_do_list;

import android.annotation.SuppressLint;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_TASK = 1;

    private final Context context;
    private final TaskActionListener listener;
    private String highlightQuery = "";

    private List<TaskListItem> items = new ArrayList<>();
    private final Set<Integer> selectedTaskIds = new HashSet<>();
    private Map<Integer, String> locationMap = new HashMap<>();

    public interface TaskActionListener {
        void onView(Task task);
        void onEdit(Task task);
        void onDelete(Task task);
        void onTaskCompletionChanged(Task task, boolean isCompleted);
        void onSelectTask(); // Called to enter selection mode
        boolean isSelectionModeActive(); // Is selection mode active
        void onClearSelection();
        void onSelectionCountChanged(int selectedCount, int totalCount);
    }

    public TaskAdapter(Context context, List<Task> tasks, TaskActionListener listener) {
        this.context = context;
        this.listener = listener;
        updateTasks(tasks);
    }

    public void setHighlightQuery(String query) {
        this.highlightQuery = query != null ? query.trim() : "";
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof TaskSectionHeader) return VIEW_TYPE_HEADER;
        return VIEW_TYPE_TASK;
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

        if (holder instanceof TaskViewHolder && item instanceof TaskItem) {
            Task task = ((TaskItem) item).task;
            TaskViewHolder taskHolder = (TaskViewHolder) holder;

            SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String formattedDate = task.date != null && !task.date.isEmpty()
                    ? "📅 " + task.date
                    : "📅 No Date";

            String formattedTime = task.getTime() != null && !task.getTime().isEmpty()
                    ? "🕐 " + task.getTime()
                    : "🕐 N/A";

            String formattedCategory = task.getCategory() != null && !task.getCategory().isEmpty()
                    ? "📋 " + task.getCategory()
                    : "📋 N/A";

            String locationLabel = "📍 " + task.getLocationLabel(locationMap);

            highlightText(taskHolder.title, task.title, highlightQuery);
            highlightText(taskHolder.category, formattedCategory, highlightQuery);
            highlightText(taskHolder.time, formattedTime, highlightQuery);
            highlightText(taskHolder.date, formattedDate, highlightQuery);
            highlightText(taskHolder.location, locationLabel, highlightQuery);

            View dot = taskHolder.itemView.findViewById(R.id.prioritydot);
            int dotColor = getPriorityColor(task.priority);
            dot.getBackground().setTint(dotColor);

            taskHolder.checkBox.setOnCheckedChangeListener(null);
            taskHolder.checkBox.setChecked("completed".equalsIgnoreCase(task.taskStatus));

            if (listener.isSelectionModeActive()) {
                taskHolder.checkBox.setVisibility(View.GONE);
            } else {
                taskHolder.checkBox.setVisibility(View.VISIBLE);
                taskHolder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    listener.onTaskCompletionChanged(task, isChecked);
                    buttonView.setEnabled(false);
                    buttonView.postDelayed(() -> buttonView.setEnabled(true), 500);
                });
            }

            GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (listener.isSelectionModeActive()) {
                        toggleSelection(task);
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
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (!listener.isSelectionModeActive()) {
                        listener.onSelectTask(); // enter selection mode
                    }
                    toggleSelection(task);
                }
            });

            taskHolder.itemView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });

            MaterialCardView cardView = (MaterialCardView) taskHolder.itemView;

            if (selectedTaskIds.contains(task.getId())) {
                int selectedColor = ContextCompat.getColor(context, R.color.selectedtaskbackground);
                int strokeColor = ContextCompat.getColor(context, R.color.primaryColor);
                cardView.setCardBackgroundColor(selectedColor);
                cardView.setStrokeWidth(4);
                cardView.setStrokeColor(strokeColor);
            } else {
                int defaultColor = ContextCompat.getColor(context, R.color.taskliststbackgroundcolor);
                cardView.setCardBackgroundColor(defaultColor);
                cardView.setStrokeWidth(0);
            }
        }
    }

    private int getPriorityColor(String priority) {
        if (priority == null) return Color.parseColor("#388E3C");
        switch (priority.toLowerCase()) {
            case "high":
                return Color.parseColor("#D32F2F");
            case "medium":
                return Color.parseColor("#FBC02D");
            default:
                return Color.parseColor("#388E3C");
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

        public void setCheckboxEnabled(boolean enabled) {
            checkBox.setEnabled(enabled); // or checkBox.setClickable(enabled);
        }
    }


    public interface TaskListItem {}

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
        items.clear();
        for (Task task : newTasks) {
            items.add(new TaskItem(task));
        }
        notifyDataSetChanged();
    }

    public void toggleSelection(Task task) {
        int changedPos = -1;
        for (int i = 0; i < items.size(); i++) {
            TaskListItem item = items.get(i);
            if (item instanceof TaskItem && ((TaskItem) item).task.getId() == task.getId()) {
                changedPos = i;
                break;
            }
        }

        if (selectedTaskIds.contains(task.getId())) {
            selectedTaskIds.remove(task.getId());
        } else {
            selectedTaskIds.add(task.getId());
        }

        if (changedPos != -1) {
            notifyItemChanged(changedPos);
        }

        if (listener != null) {
            int selectedCount = selectedTaskIds.size();
            int totalCount = getTotalTaskCount();
            listener.onSelectionCountChanged(selectedCount, totalCount);
            if (selectedCount == 0) listener.onClearSelection();
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
        selectedTaskIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(0, getTotalTaskCount());
            listener.onClearSelection();
        }
    }

    public void setLocationMap(Map<Integer, String> map) {
        this.locationMap = map != null ? map : new HashMap<>();
        notifyDataSetChanged();
    }

    public boolean areAllSelectedInCurrentTab() {
        int totalTasks = 0;
        int selected = 0;
        for (TaskListItem item : items) {
            if (item instanceof TaskItem) {
                totalTasks++;
                Task task = ((TaskItem) item).task;
                if (selectedTaskIds.contains(task.getId())) selected++;
            }
        }
        return totalTasks > 0 && selected == totalTasks;
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

    public void triggerViewTask(Task task) {
        if (listener != null) {
            listener.onView(task);
        }
    }

    public Task getTaskAt(int position) {
        TaskListItem item = getItem(position);
        if (item instanceof TaskItem) {
            return ((TaskItem) item).task;
        }
        return null;
    }
    public void triggerEditTask(Task task) {
        if (listener != null) {
            listener.onEdit(task);
        }
    }

    public void enterSelectionMode() {
        if (listener != null) {
            listener.onSelectTask();
        }
    }


    public int getPositionForTask(Task task) {
        for (int i = 0; i < items.size(); i++) {
            TaskListItem item = items.get(i); // ✅ Fix: Use TaskListItem here
            if (item instanceof TaskItem) {
                TaskItem taskItem = (TaskItem) item;
                if (taskItem.task.getId() == task.getId()) {
                    return i;
                }
            }
        }
        return RecyclerView.NO_POSITION;
    }
    public boolean isTaskSelected(Task task) {
        return selectedTaskIds.contains(task.getId());
    }

}
