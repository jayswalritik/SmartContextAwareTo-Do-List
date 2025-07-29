package com.example.smartto_do_list;

import android.Manifest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartto_do_list.services.MotionDetectionService;
import com.example.smartto_do_list.utils.WorkerUtils;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.getkeepsafe.taptargetview.TapTarget;

import com.example.smartto_do_list.utils.TaskUtils;



public class MainActivity extends AppCompatActivity {
    private RelativeLayout actionRow;
    private ImageButton notificationButton;
    private boolean suppressFocusAfterTutorial = false;

    private TapTargetView tapTargetView; // Make sure this is declared in your MainActivity


    private TaskAdapter.TaskActionListener taskActionListener;

    private RelativeLayout homePageRow;
    private boolean isSwipeEnabled = true; // default true, false during tutorial


    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private List<Task> taskList = new ArrayList<>();
    private List<Task> allTasks = new ArrayList<>();
    private List<Task> filteredTasks = new ArrayList<>();
    private TaskDatabase db;

    private FloatingActionButton addTaskButton;
    private TextInputEditText searchBar;
    private TextInputLayout searchBarContainer;
    private TextView emptyStateView;
    ColorStateList colorStateList;
    private ImageButton menuButton;
    private DrawerLayout drawerLayout;
    private TextView homePageView;
    private RadioButton selectAllRadioButton;
    private ImageView deleteAllButton;
    private CheckBox markCompletedAllCheckbox;

    private String currentCategory = "All";
    private LayoutAnimationController fallDownFadeInAnimation;
    private LayoutAnimationController fadeInLayoutAnimation;
    private List<Button> categoryButtons = new ArrayList<>();
    private TabLayout statusTabLayout;
    private String currentStatus = "Pending"; // Default selected tab/status
    private boolean isSelectionMode = false;
    List<Task> rawTasks;
    Map<Integer, String> locationMap = new HashMap<>();
    private final List<Task> globalAllTasks = new ArrayList<>();
    private TouchBlockerOverlay touchBlockerOverlay;

    private long backPressedTime = 0;
    private static final int BACK_PRESS_INTERVAL = 2000; // 2 seconds
    private Toast backToast;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🔔 Ask for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean warningDismissed = prefs.getBoolean("notification_warning_dismissed", false);

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED && !warningDismissed) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        1001);
            }
        }

        // 🧠 UI and logic setup (unchanged from your version)
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        if (getIntent() != null) {
            onNewIntent(getIntent());
        }

        setupWindowInsets();
        initViews();
        initSearchBar();
        setupCategoryButtons();
        setupAddTaskButton();
        setupRecyclerView();
        setupStatusTabs();

        currentCategory = "All";
        loadTasksForCategory(currentCategory);
        setSelectedCategoryButton(currentCategory);

        menuButton.setOnClickListener(v -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
            } else if (id == R.id.nav_darkmode) {
                toggleTheme();
            } else if (id == R.id.nav_savedlocations) {
                startActivity(new Intent(this, SavedLocationsActivity.class));
            } else if (id == R.id.nav_archive) {
                startActivity(new Intent(this, ArchiveTasksActivity.class));
            } else if (id == R.id.nav_feedback) {
                startActivity(new Intent(this, FeedbackActivity.class));
            } else if (id == R.id.nav_tutorial) {
                startActivity(new Intent(this, TutorialActivity.class));
            }
            drawerLayout.closeDrawers();
            return true;
        });

        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotificationListActivity.class);
            startActivity(intent);
        });

        selectAllRadioButton.setOnClickListener(v -> radioButtonAction());

        deleteAllButton.setOnClickListener(v -> {
            Set<Integer> selectedIds = adapter.getSelectedTaskIds();
            List<Task> selectedTasks = new ArrayList<>();
            for (Task task : globalAllTasks) {
                if (selectedIds.contains(task.getId())) {
                    selectedTasks.add(task);
                }
            }

            if (selectedTasks.isEmpty()) {
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Delete Selected Tasks")
                    .setMessage("Are you sure you want to delete " + selectedTasks.size() + " selected task(s)?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        adapter.clearSelection();
                        deleteTasksWithUndo(selectedTasks);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        markCompletedAllCheckbox.setOnClickListener(v -> {
            markCompletedAllCheckbox.setChecked(true);

            Set<Integer> selectedIds = adapter.getSelectedTaskIds();
            List<Task> selectedTasks = new ArrayList<>();
            for (Task task : globalAllTasks) {
                if (selectedIds.contains(task.getId())) {
                    selectedTasks.add(task);
                }
            }

            if (selectedTasks.isEmpty()) {
                return;
            }

            markCompletedAllCheckbox.postDelayed(() -> {
                markMultipleTasksCompleted(selectedTasks);
            }, 400);
        });

        // ✅ Schedule notification for saved tasks
        new Thread(() -> {
            TaskDatabase db = TaskDatabase.getInstance(getApplicationContext());
            List<Task> allTasks = db.taskDao().getAllTasks();
            for (Task task : allTasks) {
                NotificationScheduler.scheduleTaskNotification(getApplicationContext(), task);
            }
        }).start();

        // ✅ Schedule repeating tasks
        WorkerUtils.scheduleDynamicRepeatWorker(getApplicationContext());

        // 📍 Request location permissions if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        },
                        1002);
            }
        }

        // 🛰️ Start motion detection service
        Intent serviceIntent = new Intent(this, MotionDetectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                // Show warning only if user denied
                showNotificationPermissionWarning();
            }
        }
    }
    private void showNotificationPermissionWarning() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Notifications Disabled")
                .setMessage("This app won't be able to send you task reminders unless you allow notification permission.\n\n" +
                        "To enable it manually:\n" +
                        "1. Open Settings\n" +
                        "2. Tap 'Notifications'\n" +
                        "3. Enable permission for this app.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    // Save flag so dialog doesn’t keep showing
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("notification_warning_dismissed", true)
                            .apply();
                })
                .setNegativeButton("Open Settings", (dialog, which) -> {
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .show();
    }


    private void initViews() {
        actionRow = findViewById(R.id.actionrow);
        homePageRow =findViewById(R.id.homepagerow);
        menuButton = findViewById(R.id.menubutton);
        notificationButton = findViewById(R.id.notificationbutton);
        drawerLayout = findViewById(R.id.drawer_layout);
        homePageView = findViewById(R.id.homepageview);
        touchBlockerOverlay = findViewById(R.id.touch_blocker_overlay);

        selectAllRadioButton = findViewById(R.id.selectallradiobutton);
        colorStateList = ColorStateList.valueOf(Color.WHITE);
        selectAllRadioButton.setButtonTintList(colorStateList);

        deleteAllButton = findViewById(R.id.deleteallicon);
        markCompletedAllCheckbox = findViewById(R.id.markcompletedallcheckbox);
        //markCompletedAllCheckbox.setButtonTintList(colorStateList);

        recyclerView = findViewById(R.id.taskrecyclerview);
        emptyStateView = findViewById(R.id.emptyview);
        searchBarContainer = findViewById(R.id.searchbar_textinputlayout);
        searchBar = findViewById(R.id.searchbar);
        addTaskButton = findViewById(R.id.addtaskbutton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fallDownFadeInAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.falldownfadeinlayoutanimation);
        fadeInLayoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.fadeinlayoutanimation);
        recyclerView.setLayoutAnimation(fallDownFadeInAnimation);

        db = TaskDatabase.getInstance(getApplicationContext());
        List<SavedLocations> locations = db.savedLocationDao().getAllSavedLocations();
        for (SavedLocations loc : locations) {
            locationMap.put(loc.getId(), loc.getLabel());
        }

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    addTaskButton.show(); // Show FAB when scrolling stops
                } else {
                    addTaskButton.hide(); // Hide FAB while scrolling
                }
            }
        });

    }
    private void setupRecyclerView() {
        taskActionListener = new TaskAdapter.TaskActionListener() {
            public void onView(Task task) {
                if (!isSelectionMode) {
                    Intent intent = new Intent(MainActivity.this, TaskDetailsActivity.class);
                    intent.putExtra("task_id", task.id);
                    intent.putExtra("is_view_only", true);
                    startActivity(intent);
                }
            }

            public void onEdit(Task task) {

                Intent intent = new Intent(MainActivity.this, TaskDescription.class);
                intent.putExtra("task_id", task.id);
                intent.putExtra("is_view_only", false);
                startActivity(intent);
            }

            @Override
            public void onDelete(Task task) {
                List<Task> singleTask = new ArrayList<>();
                singleTask.add(task);
                deleteTasksWithUndo(singleTask);
            }

            @Override
            public void onTaskCompletionChanged(Task task, boolean isCompleted) {
                markTaskCompleted(task, isCompleted);
            }

            public void onSelectTask() {
                // Make Menu and homepage invisible
                homePageRow.setVisibility(View.GONE);
                // Make the action icons at top visible
                actionRow.setVisibility(View.VISIBLE);
                isSelectionMode = true;
                // Reset the 'markCompletedAllCheckbox' state
                markCompletedAllCheckbox.setChecked(false);
            }

            public boolean isSelectionModeActive() {
                return isSelectionMode; // your Activity's boolean field
            }

            public void onClearSelection() {
                // Show HomePage and menu
                homePageRow.setVisibility(View.VISIBLE);
                menuButton.setVisibility(View.VISIBLE);

                // Hide action icons
                actionRow.setVisibility(View.GONE);

                isSelectionMode = false;
                recyclerView.setLayoutAnimation(fallDownFadeInAnimation);
                recyclerView.post(recyclerView::scheduleLayoutAnimation);
            }

            @Override
            public void onSelectionCountChanged(int selectedCount, int totalCount) {
                if (adapter.areAllSelectedInCurrentTab()) {
                    selectAllRadioButton.setChecked(true);
                } else {
                    selectAllRadioButton.setChecked(false);
                }
            }
        };
        adapter = new TaskAdapter(this, taskList, taskActionListener);
        adapter.setLocationMap(locationMap);

        // Attach adapter to RecyclerView
        recyclerView.setAdapter(adapter);

        // Optional: attach swipe-to-delete
        attachSwipeToDelete();
    }
    private void loadTasksForCategoryAndStatus(String category, String status) {
        String today = getFormattedDate(0);
        SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();

        // Fetch tasks by category
        if ("completed".equalsIgnoreCase(status)) {
            switch (category) {
                case "All":
                    rawTasks = db.taskDao().getTasksByStatus("completed");
                    break;
                case "Today":
                case "Tomorrow":
                    String date = category.equals("Today") ? getFormattedDate(0) : getFormattedDate(1);
                    rawTasks = db.taskDao().getTasksByDateAndStatus(date, "completed");
                    break;
                case "High":
                case "Medium":
                case "Low":
                    rawTasks = db.taskDao().getTasksByPriorityAndStatus(category, "completed");
                    break;
                default:
                    rawTasks = db.taskDao().getTasksByCategoryAndStatus(category, "completed");
                    break;
            }
        } else {
            switch (category) {
                case "All":
                    rawTasks = db.taskDao().getAllTasksCustomOrdered(today);
                    break;
                case "Today":
                case "Tomorrow":
                    String date = category.equals("Today") ? getFormattedDate(0) : getFormattedDate(1);
                    rawTasks = db.taskDao().getTasksByDateWithStatuses(date);
                    break;
                case "High":
                case "Medium":
                case "Low":
                    rawTasks = db.taskDao().getTasksByPriorityWithStatuses(category, today);
                    break;
                default:
                    rawTasks = db.taskDao().getTasksByCategoryWithStatuses(category, today);
                    break;
            }
        }

        // Filter by status (Pending, Overdue, Completed)
        List<Task> filteredByStatus = new ArrayList<>();
        for (Task task : rawTasks) {
            String taskStatus = task.taskStatus != null ? task.taskStatus.toLowerCase() : "pending";

            TaskUtils utils = new TaskUtils(this, TaskDatabase.getInstance(this));
            utils.handleRepeatingTask(task); // ✅ reuse shared version

            boolean isOverdue = isTaskOverdue(task);

            switch (status.toLowerCase()) {
                case "pending":
                    if ("pending".equals(taskStatus) && !isOverdue) filteredByStatus.add(task);
                    break;
                case "overdue":
                    if (isOverdue) filteredByStatus.add(task);
                    break;
                case "completed":
                    if ("completed".equals(taskStatus)) {
                        String completedDateStr = task.getCompletedDate(); // Ensure you store this date in the task object
                        if (completedDateStr != null && !completedDateStr.isEmpty()) {
                            try {
                                Date completedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(completedDateStr);
                                if (completedDate != null) {
                                    long days = (new Date().getTime() - completedDate.getTime()) / (1000 * 60 * 60 * 24);
                                    if (days <= 30) {
                                        filteredByStatus.add(task); // ✅ only include if <= 30 days old
                                    }
                                }
                            } catch (ParseException e) {
                                // If date parsing fails, optionally include or exclude
                                filteredByStatus.add(task); // or skip if you prefer
                            }
                        } else {
                            filteredByStatus.add(task); // if no date, you can default to showing it
                        }
                    }
                    break;

            }
        }

        // 🔽 Sort filtered list by due date and time
        Collections.sort(filteredByStatus, (task1, task2) -> {
            try {
                Date date1 = task1.getDate() != null ? dbDateFormat.parse(task1.getDate()) : null;
                Date date2 = task2.getDate() != null ? dbDateFormat.parse(task2.getDate()) : null;

                if (date1 != null && date2 != null) {
                    int cmp = date1.compareTo(date2);
                    if (cmp != 0) return cmp;
                } else if (date1 != null) return -1;
                else if (date2 != null) return 1;

                try {
                    Date t1 = task1.getTime() != null && !task1.getTime().isEmpty() ? timeFormat.parse(task1.getTime()) : null;
                    Date t2 = task2.getTime() != null && !task2.getTime().isEmpty() ? timeFormat.parse(task2.getTime()) : null;
                    if (t1 != null && t2 != null) return t1.compareTo(t2);
                    else if (t1 != null) return -1;
                    else if (t2 != null) return 1;
                } catch (ParseException ignored) {}

            } catch (ParseException e) {
                return 0;
            }
            return 0;
        });

        taskList.clear();
        filteredTasks.clear();

        // Apply current search query
        String currentQuery = searchBar.getText().toString().trim().toLowerCase();
        List<Task> matchingTasks = new ArrayList<>();
        List<Task> nonMatchingTasks = new ArrayList<>();

        for (Task task : filteredByStatus) {
            if (!currentQuery.isEmpty() && matchesTask(task, currentQuery)) {
                matchingTasks.add(task);
            } else {
                nonMatchingTasks.add(task);
            }
        }

        List<Task> sortedTasks = new ArrayList<>();
        sortedTasks.addAll(matchingTasks);
        sortedTasks.addAll(nonMatchingTasks);

        List<TaskAdapter.TaskListItem> sectionedItems = new ArrayList<>();
        if (!sortedTasks.isEmpty()) {
            sectionedItems.add(new TaskAdapter.TaskSectionHeader(status + " Tasks"));
            for (Task t : sortedTasks) {
                sectionedItems.add(new TaskAdapter.TaskItem(t));
            }
        }

        adapter.setItems(sectionedItems);
        taskList.addAll(sortedTasks);
        allTasks.clear();
        allTasks.addAll(filteredByStatus);
        toggleEmptyState(taskList.isEmpty());

        recyclerView.setLayoutAnimation(fallDownFadeInAnimation);
        recyclerView.post(recyclerView::scheduleLayoutAnimation);
    }

    private void loadTasksForCategory(String category) {
        currentCategory = category;
        loadTasksForCategoryAndStatus(category, currentStatus);
    }

    private void setupStatusTabs() {
        statusTabLayout = findViewById(R.id.statusTabLayout);

        statusTabLayout.addTab(statusTabLayout.newTab().setText("Pending"));
        statusTabLayout.addTab(statusTabLayout.newTab().setText("Overdue"));
        statusTabLayout.addTab(statusTabLayout.newTab().setText("Completed"));

        statusTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentStatus = tab.getText().toString();
                loadTasksForCategoryAndStatus(currentCategory, currentStatus);

                if (isSelectionMode) {
                    actionRow.setVisibility(View.VISIBLE);
                    homePageRow.setVisibility(View.GONE);
                } else {
                    actionRow.setVisibility(View.GONE);
                    homePageRow.setVisibility(View.VISIBLE);
                }

                selectAllRadioButton.setChecked(adapter.areAllSelectedInCurrentTab());
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private boolean matchesStatus(Task task, String statusTab) {
        if (task == null || task.getTaskStatus() == null) return false;
        String status = task.getTaskStatus().toLowerCase();

        switch (statusTab.toLowerCase()) {
            case "pending":
                return status.equals("pending") && !isTaskOverdue(task);
            case "completed":
                return status.equals("completed");
            case "overdue":
                return status.equals("pending") && isTaskOverdue(task);
            default:
                return true;
        }
    }

    // ✅ Reusable helper to check if a task is overdue considering date and time
    private boolean isTaskOverdue(Task task) {
        try {
            if (!"completed".equalsIgnoreCase(task.getTaskStatus())) {
                SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

                Date taskDate = task.getDate() != null ? dbDateFormat.parse(task.getDate()) : null;
                String taskTimeStr = task.getTime();

                if (taskDate != null) {
                    Calendar taskCal = Calendar.getInstance();
                    taskCal.setTime(taskDate);

                    if (taskTimeStr != null && !taskTimeStr.isEmpty()) {
                        Date taskTime = timeFormat.parse(taskTimeStr);
                        Calendar timeCal = Calendar.getInstance();
                        timeCal.setTime(taskTime);

                        taskCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                        taskCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                        taskCal.set(Calendar.SECOND, 59);
                        taskCal.set(Calendar.MILLISECOND, 999);
                    } else {
                        taskCal.set(Calendar.HOUR_OF_DAY, 23);
                        taskCal.set(Calendar.MINUTE, 59);
                        taskCal.set(Calendar.SECOND, 59);
                        taskCal.set(Calendar.MILLISECOND, 999);
                    }

                    return taskCal.getTime().before(new Date());
                }
            }
        } catch (ParseException ignored) {}
        return false;
    }

    private String getFormattedDate(int daysToAdd) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, daysToAdd);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private void toggleEmptyState(boolean isEmpty) {
        emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }



    public void filterTasks(String query) {
        filteredTasks.clear();
        adapter.setHighlightQuery(query);

        if (query == null || query.trim().isEmpty()) {
            for (Task task : allTasks) {
                if (matchesStatus(task, currentStatus)) {
                    filteredTasks.add(task);
                }
            }
            adapter.updateTasks(filteredTasks);
            toggleEmptyState(filteredTasks.isEmpty());
            return;
        }

        String fullQuery = query.trim();
        for (Task task : allTasks) {
            if (!matchesStatus(task, currentStatus)) continue;
            if (matchesTask(task, fullQuery)) {
                filteredTasks.add(task);
            }
        }

        adapter.updateTasks(filteredTasks);
        toggleEmptyState(filteredTasks.isEmpty());
    }


    private boolean matchesTask(Task task, String query) {
        if (query == null || query.trim().isEmpty()) return false;

        String lowerQuery = query.toLowerCase().trim();
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat nlDate = new SimpleDateFormat("MMMM d", Locale.getDefault());
        Calendar today = Calendar.getInstance();

        Calendar rangeStart = null, rangeEnd = null;
        String filterPriority = null, filterCategory = null, filterStatus = null;
        List<String> keywords = new ArrayList<>();

        // Check for date-range pattern "June 10 - June 20"
        Matcher rangeMatcher = Pattern.compile("(\\p{L}+\\s+\\d{1,2})\\s*-\\s*(\\p{L}+\\s+\\d{1,2})").matcher(lowerQuery);
        if (rangeMatcher.find()) {
            try {
                Date s = nlDate.parse(rangeMatcher.group(1));
                Date e = nlDate.parse(rangeMatcher.group(2));
                rangeStart = Calendar.getInstance(); rangeStart.setTime(s);
                 rangeEnd   = Calendar.getInstance(); rangeEnd.setTime(e);
                rangeStart.set(Calendar.YEAR, today.get(Calendar.YEAR));
                rangeEnd.set(Calendar.YEAR, today.get(Calendar.YEAR));
                lowerQuery = lowerQuery.replace(rangeMatcher.group(0), "");
            } catch (Exception ignored) {}
        }

        String[] words = lowerQuery.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.startsWith("priority:")) {
                filterPriority = w.substring("priority:".length());
            } else if (w.startsWith("category:")) {
                filterCategory = w.substring("category:".length());
            } else if (w.equals("completed") || w.equals("done")) {
                filterStatus = "completed";
            } else if (w.equals("pending") || w.equals("incomplete") || w.equals("notdone")) {
                filterStatus = "pending";
            } else if (w.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try {
                    Date d = dbFormat.parse(w);
                    Calendar c = Calendar.getInstance(); c.setTime(d);
                    rangeStart = (Calendar) c.clone();
                    rangeEnd = (Calendar) c.clone();
                } catch (Exception ignored) {}
            } else {
                // Try "June 10"
                if (i + 1 < words.length) {
                    String combo = w + " " + words[i + 1];
                    try {
                        nlDate.parse(combo);
                        keywords.add(combo);
                        i++;
                        continue;
                    } catch (Exception ignored) {}
                }
                keywords.add(w);
            }
        }

        // Filter checks
        if (filterPriority != null && (task.priority == null || !task.priority.equalsIgnoreCase(filterPriority)))
            return false;

        if (filterCategory != null && (task.category == null || !task.category.toLowerCase().contains(filterCategory)))
            return false;

        if (filterStatus != null) {
            String s = task.taskStatus == null ? "" : task.taskStatus.toLowerCase();
            if (filterStatus.equals("completed") && !s.equals("completed")) return false;
            if (filterStatus.equals("pending") && s.equals("completed")) return false;
        }

        if ((rangeStart != null || rangeEnd != null) && task.date != null) {
            try {
                Date td = dbFormat.parse(task.date);
                Calendar tc = Calendar.getInstance(); tc.setTime(td);
                if (rangeStart != null && tc.before(rangeStart)) return false;
                if (rangeEnd != null && tc.after(rangeEnd)) return false;
            } catch (Exception ignored) {}
        }

        // Full-phrase check
        String phrase = query.toLowerCase().trim();
        String[] searchableFields = {
                task.title != null ? task.title.toLowerCase() : "",
                task.category != null ? task.category.toLowerCase() : "",
                task.priority != null ? task.priority.toLowerCase() : "",
                task.taskStatus != null ? task.taskStatus.toLowerCase() : "",
                task.time != null ? task.time.toLowerCase() : "",
                task.date != null ? task.date.toLowerCase() : ""
        };

        boolean phraseMatched = false;
        for (String field : searchableFields) {
            if (field.contains(phrase)) {
                phraseMatched = true;
                break;
            }
        }

        // If phrase doesn't match, fall back to keyword check
        if (!phraseMatched) {
            for (String kw : keywords) {
                kw = kw.toLowerCase();
                boolean matched = false;

                // Natural language date match
                try {
                    Date nd = nlDate.parse(kw);
                    if (nd != null && task.date != null) {
                        Date td = dbFormat.parse(task.date);
                        Calendar nc = Calendar.getInstance(); nc.setTime(nd);
                        Calendar tc = Calendar.getInstance(); tc.setTime(td);
                        if (nc.get(Calendar.MONTH) == tc.get(Calendar.MONTH) &&
                                nc.get(Calendar.DAY_OF_MONTH) == tc.get(Calendar.DAY_OF_MONTH)) {
                            matched = true;
                        }
                    }
                } catch (Exception ignored) {}

                if (!matched && task.date != null) {
                    try {
                        Date td = dbFormat.parse(task.date);
                        Calendar tc = Calendar.getInstance(); tc.setTime(td);
                        String taskMonth = tc.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toLowerCase();
                        if (taskMonth.contains(kw)) {
                            matched = true;
                        }
                    } catch (Exception ignored) {}
                }

                if (!matched) {
                    if (task.title != null && task.title.toLowerCase().contains(kw)) matched = true;
                }

                if (!matched) return false;
            }
        }

        return true;
    }
    private void setSelectedCategoryButton(String category) {
        for (Button btn : categoryButtons) {
            if (btn.getText().toString().equalsIgnoreCase(category)) {
                btn.setSelected(true);
                btn.setTextColor(ContextCompat.getColor(this, R.color.buttontextcolor));
            } else {
                btn.setSelected(false);
                btn.setTextColor(ContextCompat.getColor(this, R.color.buttontextcolor));
            }
        }
    }


    /// /////////////////////////////////////////////////////////////////////////////////////////////////////
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initSearchBar() {
        searchBarContainer.setStartIconOnClickListener(v -> handleSearchIconClick(v));
        searchBar.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTasks(s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });

        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void handleSearchIconClick(View v) {
        String query = searchBar.getText() != null ? searchBar.getText().toString() : "";
        if (!query.isEmpty()) {
            v.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                v.setAlpha(1f);
                filterTasks(query);
                searchBar.clearFocus();
                hideKeyboard(searchBar);
            }).start();
        } else {
            searchBar.requestFocus();
            showKeyboard(searchBar);
        }
    }

    private void performSearch() {
        String query = searchBar.getText() != null ? searchBar.getText().toString().trim() : "";
        filterTasks(query);
        hideKeyboard(searchBar);
        searchBar.clearFocus();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void setupCategoryButtons() {
        ChipGroup categoryChipGroup = findViewById(R.id.categorychipgroup);

        String[] categories = {
                "All", "Today", "Tomorrow", "High", "Medium", "Low",
                "Work", "Personal", "Urgent", "Health", "Study", "Shopping", "Others"
        };

        categoryButtons.clear(); // Clear previous buttons if this gets re-initialized
        categoryChipGroup.removeAllViews(); // In case of re-setup

        for (String category : categories) {
            Button btn = new Button(this);
            btn.setText(category);
            btn.setAllCaps(false);
            btn.setTextColor(ContextCompat.getColor(this, R.color.buttontextcolor));
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.selectablebuttonbackground));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(12, 6, 12, 6);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                currentCategory = category;

                String defaultStatus = "Pending";
                currentStatus = defaultStatus;

                // Load tasks for new category and reset status filter to "Pending"
                loadTasksForCategoryAndStatus(currentCategory, defaultStatus);
                setSelectedCategoryButton(currentCategory);

                // ✅ Select the "Pending" tab in TabLayout
                TabLayout.Tab pendingTab = statusTabLayout.getTabAt(0);
                if (pendingTab != null) {
                    pendingTab.select();
                }
            });

            categoryButtons.add(btn);
            categoryChipGroup.addView(btn);
        }

        // Set default selected category when buttons are first created
        setSelectedCategoryButton(currentCategory);
    }

    private void setupAddTaskButton() {
        addTaskButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskDescription.class);
            startActivity(intent); // Starts TaskDescription
        });
    }

    private void attachSwipeToDelete() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // Prevent swipe delete if selection mode is active
                if (isSelectionMode) {
                    adapter.notifyItemChanged(viewHolder.getAdapterPosition());
                    return;
                }

                int position = viewHolder.getAdapterPosition();
                TaskAdapter.TaskListItem item = adapter.getItem(position);

                if (!(item instanceof TaskAdapter.TaskItem)) {
                    adapter.notifyItemChanged(position); // e.g., section headers – do not delete
                    return;
                }

                Task taskToDelete = ((TaskAdapter.TaskItem) item).task;

                NotificationScheduler.cancelTaskNotification(MainActivity.this, taskToDelete.getId());
                NotificationScheduler.cancelTaskReminder(MainActivity.this, taskToDelete.getId()); // ✅ Add this

                // Remove from DB and refresh
                db.taskDao().delete(taskToDelete);
                loadTasksForCategory(currentCategory);

                // Undo option
                Snackbar.make(recyclerView, "Task deleted", Snackbar.LENGTH_LONG)
                        .setAction("UNDO", v -> {
                            db.taskDao().insert(taskToDelete);
                            // Re-schedule notification on undo
                            NotificationScheduler.scheduleTaskNotification(MainActivity.this, taskToDelete);
                            NotificationScheduler.scheduleTaskReminder(MainActivity.this, taskToDelete);
                            loadTasksForCategory(currentCategory);
                        }).show();
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive && dX < 0 && !isSelectionMode) {
                    Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    c.drawRect(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom(), paint);

                    Drawable deleteIcon = ContextCompat.getDrawable(MainActivity.this, R.drawable.deleteicon);
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

                float maxSwipe = recyclerView.getWidth() * 0.7f;
                itemView.setTranslationX(Math.max(dX, -maxSwipe));
            }
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (isSelectionMode || !isSwipeEnabled) {
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

    private void deleteTasksWithUndo(List<Task> tasksToDelete) {
        if (tasksToDelete == null || tasksToDelete.isEmpty()) return;

        // Backup tasks to restore if undo is clicked
        List<Task> backup = new ArrayList<>(tasksToDelete);

        // Delete from database
        for (Task task : tasksToDelete) {
            NotificationScheduler.cancelTaskNotification(MainActivity.this, task.getId());
            NotificationScheduler.cancelTaskReminder(MainActivity.this, task.getId()); // 🔔 Add this
            db.taskDao().delete(task);
        }

        adapter.clearSelection();
        actionRow.setVisibility(View.GONE);

        //Show homepage view at the top
        homePageRow.setVisibility(View.VISIBLE);

        loadTasksForCategory(currentCategory);
        isSelectionMode = false;
        recyclerView.setLayoutAnimation(fallDownFadeInAnimation);
        recyclerView.post(recyclerView::scheduleLayoutAnimation);

        // Show undo option
        Snackbar.make(recyclerView, tasksToDelete.size() == 1 ?
                                "Task deleted" : tasksToDelete.size() + " tasks deleted",
                        Snackbar.LENGTH_LONG)
                .setAction("UNDO", v -> {
                    for (Task task : backup) {
                        db.taskDao().insert(task);
                        // Re-schedule notification on undo
                        NotificationScheduler.scheduleTaskNotification(MainActivity.this, task);
                        NotificationScheduler.scheduleTaskReminder(MainActivity.this, task); // 🔁 Add this
                    }
                    loadTasksForCategory(currentCategory);
                }).show();
    }

    private void markTaskCompleted(Task task, boolean isCompleted) {
        String previousStatus = task.getTaskStatus();
        String newStatus = isCompleted ? "completed" : "pending";

        String previousCompletedDate = task.getCompletedDate();
        task.setTaskStatus(newStatus);

        String completedDate = null;
        if (isCompleted) {
            completedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            task.setCompletedDate(completedDate);

            // ❌ Cancel notification if marked completed
            NotificationScheduler.cancelTaskNotification(MainActivity.this, task.getId());
            NotificationScheduler.cancelTaskReminder(MainActivity.this, task.getId()); // 🔔 Add this
        } else {
            task.setCompletedDate(null);

            // ✅ Reschedule if marked pending again
            NotificationScheduler.scheduleTaskNotification(MainActivity.this, task);
            NotificationScheduler.scheduleTaskReminder(MainActivity.this, task); // 🔁 Add this
        }

        db.taskDao().updateTaskStatusAndCompletedDate(task.getId(), newStatus, completedDate);

        Snackbar.make(recyclerView, isCompleted ? "Task marked completed" : "Task marked pending", Snackbar.LENGTH_LONG)
                .setAction("UNDO", v -> {
                    task.setTaskStatus(previousStatus);
                    task.setCompletedDate(previousCompletedDate);
                    db.taskDao().updateTaskStatusAndCompletedDate(task.getId(), previousStatus, previousCompletedDate);

                    // 🔁 Restore notification based on previous status
                    if ("completed".equalsIgnoreCase(previousStatus)) {
                        NotificationScheduler.cancelTaskNotification(MainActivity.this, task.getId());
                        NotificationScheduler.cancelTaskReminder(MainActivity.this, task.getId()); // 🔔 Add this
                    } else {
                        NotificationScheduler.scheduleTaskNotification(MainActivity.this, task);
                        NotificationScheduler.scheduleTaskReminder(MainActivity.this, task); // 🔁 Add this
                    }

                    loadTasksForCategory(currentCategory);
                }).show();

        recyclerView.postDelayed(() -> loadTasksForCategory(currentCategory), 300);
    }

    private void markMultipleTasksCompleted(List<Task> tasksToMark) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Completion")
                .setMessage("Mark selected tasks as completed?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Save previous states for UNDO
                    List<Task> previousStates = new ArrayList<>();
                    for (Task task : tasksToMark) {
                        Task copy = new Task();
                        copy.id = task.getId();
                        copy.taskStatus = task.getTaskStatus();
                        copy.setCompletedDate(task.getCompletedDate()); // Save old completedDate
                        previousStates.add(copy);

                        // Mark completed
                        task.setTaskStatus("completed");
                        String completedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        task.setCompletedDate(completedDate);

                        // ❌ Cancel notification
                        NotificationScheduler.cancelTaskNotification(MainActivity.this, task.getId());
                        NotificationScheduler.cancelTaskReminder(MainActivity.this, task.getId()); // 🔔 Add this
                    }

                    // Update DB asynchronously
                    new Thread(() -> {
                        for (Task task : tasksToMark) {
                            db.taskDao().updateTaskStatusAndCompletedDate(task.getId(), task.getTaskStatus(), task.getCompletedDate());
                        }
                        runOnUiThread(() -> {
                            adapter.clearSelection();
                            actionRow.setVisibility(View.GONE);
                            homePageRow.setVisibility(View.VISIBLE);
                            recyclerView.setLayoutAnimation(fallDownFadeInAnimation);
                            recyclerView.post(recyclerView::scheduleLayoutAnimation);
                            markCompletedAllCheckbox.setChecked(false);
                            loadTasksForCategory(currentCategory);
                        });
                    }).start();

                    // Show Snackbar with Undo
                    Snackbar.make(recyclerView, "Marked completed", Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> {
                                new Thread(() -> {
                                    for (Task old : previousStates) {
                                        db.taskDao().updateTaskStatusAndCompletedDate(old.id, old.taskStatus, old.getCompletedDate());

                                        // 🔁 Restore notification if previously pending
                                        if ("pending".equalsIgnoreCase(old.taskStatus)) {
                                            Task restored = db.taskDao().getTaskById(old.id);
                                            NotificationScheduler.scheduleTaskNotification(MainActivity.this, restored);
                                            NotificationScheduler.scheduleTaskReminder(MainActivity.this, restored);
                                        } else {
                                            NotificationScheduler.cancelTaskNotification(MainActivity.this, old.id);
                                            NotificationScheduler.cancelTaskReminder(MainActivity.this, old.id); // 🔔 Add this
                                        }
                                    }
                                    runOnUiThread(this::refreshGlobalAllTasksAndReloadUI);
                                }).start();
                            })
                            .show();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    markCompletedAllCheckbox.setChecked(false);
                })
                .show();
    }

    private void radioButtonAction() {
        if (adapter.areAllSelectedInCurrentTab()) {
            adapter.clearSelectionInCurrentTab();
            selectAllRadioButton.setChecked(false);

            // ✅ FIX: Don't hide action row just based on current tab
            if (adapter.getSelectedTaskIds().isEmpty()) {
                actionRow.setVisibility(View.GONE);
                homePageRow.setVisibility(View.VISIBLE);
                isSelectionMode = false;
            }
        } else {
            adapter.selectAllInCurrentTab();
            selectAllRadioButton.setChecked(true);
            isSelectionMode = true;
        }
    }



    private void refreshLocationMap() {
        locationMap.clear();  // Clear old entries
        List<SavedLocations> locations = db.savedLocationDao().getAllSavedLocations();
        for (SavedLocations loc : locations) {
            locationMap.put(loc.getId(), loc.getLabel());
        }
        adapter.setLocationMap(locationMap);  // Important: update the adapter
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);

        // First, check if drawer is open
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }

        // Then check if selection mode is active
        if (isSelectionMode) {
            adapter.clearSelection();
            actionRow.setVisibility(View.GONE);
            homePageRow.setVisibility(View.VISIBLE);
            selectAllRadioButton.setChecked(false);

            isSelectionMode = false;
            recyclerView.setLayoutAnimation(fallDownFadeInAnimation);
            recyclerView.post(recyclerView::scheduleLayoutAnimation);
            return;
        }

        // ⬇️ Double-back to exit logic
        if (backPressedTime + BACK_PRESS_INTERVAL > System.currentTimeMillis()) {
            if (backToast != null) backToast.cancel();

            // ⬅️ Force finish all activities and remove from recent apps
            finishAffinity(); // Closes all activities in the task
            System.exit(0);   // Ensures JVM cleanup (optional)
        } else {
            backToast = Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT);
            backToast.show();
            backPressedTime = System.currentTimeMillis();
        }

    }
    @Override
    protected void onResume() {
        super.onResume();
        refreshLocationMap();

        if (currentCategory == null || currentCategory.isEmpty()) {
            currentCategory = "All";
        }

        loadTasksForCategory(currentCategory);

        new Thread(() -> {
            List<Task> all = db.taskDao().getAllTasks();
            runOnUiThread(() -> {
                globalAllTasks.clear();
                globalAllTasks.addAll(all);
            });
        }).start();

        setSelectedCategoryButton(currentCategory);

        // ✅ Clear search bar focus if returning from tutorial
        if (suppressFocusAfterTutorial) {
            suppressFocusAfterTutorial = false;
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
            }
            if (searchBar != null) {
                searchBar.clearFocus();  // Optional but safe
            }
        }
    }
    private void suppressSearchBarFocus() {
        if (searchBar != null) {
            searchBar.clearFocus();
            searchBar.setFocusable(false);
            searchBar.setFocusableInTouchMode(false);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (searchBar != null) {
                    searchBar.setFocusable(true);
                    searchBar.setFocusableInTouchMode(true);
                }
            }, 500);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        View searchBar = findViewById(R.id.searchbar);
        if (searchBar != null) searchBar.clearFocus();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    hideKeyboard(v);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
    private void refreshGlobalAllTasksAndReloadUI() {
        new Thread(() -> {
            List<Task> all = db.taskDao().getAllTasks();  // Must return full task list, no filters
            runOnUiThread(() -> {
                globalAllTasks.clear();
                globalAllTasks.addAll(all);
                loadTasksForCategory(currentCategory);
            });
        }).start();
    }

    private void toggleTheme() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            editor.putInt("night_mode", AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            editor.putInt("night_mode", AppCompatDelegate.MODE_NIGHT_YES);
        }

        editor.apply();

        // Close the drawer before recreating
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }

        recreate(); // Restart activity to apply theme
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent != null) {
            if (intent.getBooleanExtra("open_drawer", false)) {
                DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
                drawerLayout.openDrawer(GravityCompat.START);
            }

            if (intent.getBooleanExtra("show_create_task_tutorial", false)) {
                showCreateTaskTutorial();
            }
            if (intent.getBooleanExtra("show_view_task_tutorial", false)) {
                showViewTaskTutorial();
            }
            if (intent.getBooleanExtra("show_edit_task_tutorial", false)) {
                showEditTaskTutorial();
            }
            if (intent.getBooleanExtra("show_delete_task_tutorial", false)) {
                showDeleteTutorial();
            }
            if (intent.getBooleanExtra("show_select_task_tutorial", false)) {
                showSelectTaskTutorial();
            }
            if (intent.getBooleanExtra("show_task_mark_completed_tutorial", false)) {
                showTaskMarkCompletedTutorialAfterClick();
            }
        }
    }



    //     ***************************************   Tutorial methods ********************************

    private void showCreateTaskTutorial() {
        disableTaskCheckboxes();

        tapTargetView = TapTargetView.showFor(
                this,
                TapTarget.forView(addTaskButton, "Create a Task", "Tap here to start creating a new task.")
                        .outerCircleColor(R.color.teal_700)
                        .targetCircleColor(android.R.color.white)
                        .titleTextSize(18)
                        .descriptionTextSize(14)
                        .cancelable(true)
                        .transparentTarget(true)
                        .drawShadow(true),
                new TapTargetView.Listener() {
                    @Override
                    public void onTargetClick(TapTargetView view) {
                        super.onTargetClick(view);
                        if (tapTargetView != null) {
                            suppressFocusAfterTutorial = true;
                            tapTargetView.dismiss(true);
                            addTaskButton.performClick();
                            enableTaskCheckboxes();
                            tapTargetView = null;

                            suppressSearchBarFocus();
                        }
                    }

                    @Override
                    public void onTargetCancel(TapTargetView view) {
                        super.onTargetCancel(view);

                        suppressSearchBarFocus();
                        enableTaskCheckboxes();
                        tapTargetView = null;

                        Toast.makeText(MainActivity.this, "Tutorial cancelled.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        tapTargetView.setSwipable(false);
    }


    private void showViewTaskTutorial() {
        recyclerView.post(() -> {
            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(0);

            if (viewHolder != null && adapter.getItem(0) instanceof TaskAdapter.TaskItem) {
                View firstTaskItem = viewHolder.itemView;
                Task task = ((TaskAdapter.TaskItem) adapter.getItem(0)).task;

                disableTaskCheckboxes();

                tapTargetView = TapTargetView.showFor(
                        MainActivity.this,
                        TapTarget.forView(firstTaskItem, "View Task", "Tap once to view task details.")
                                .outerCircleColor(R.color.teal_700)
                                .targetCircleColor(android.R.color.white)
                                .titleTextSize(18)
                                .descriptionTextSize(14)
                                .cancelable(true) // Allow back press to cancel
                                .transparentTarget(true)
                                .drawShadow(true)
                                .targetRadius(130),
                        new TapTargetView.Listener() {
                            @Override
                            public void onTargetClick(TapTargetView view) {
                                super.onTargetClick(view);
                                if (tapTargetView != null) {
                                    tapTargetView.dismiss(true);
                                    adapter.triggerViewTask(task);
                                    enableTaskCheckboxes();
                                    tapTargetView = null;

                                    suppressSearchBarFocus();
                                }
                            }

                            @Override
                            public void onOuterCircleClick(TapTargetView view) {
                                // Block outer circle dismiss, do nothing here
                            }

                            @Override
                            public void onTargetCancel(TapTargetView view) {
                                super.onTargetCancel(view);

                                enableTaskCheckboxes();
                                tapTargetView = null;

                                Toast.makeText(MainActivity.this, "Tutorial cancelled.", Toast.LENGTH_SHORT).show();

                                suppressSearchBarFocus();
                            }
                        }
                );

                tapTargetView.setSwipable(false);
            } else {
                Toast.makeText(MainActivity.this, "No task available to show the tutorial.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditTaskTutorial() {
        recyclerView.post(() -> {
            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(0);

            if (viewHolder != null && adapter.getItem(0) instanceof TaskAdapter.TaskItem) {
                View firstTaskItem = viewHolder.itemView;
                Task task = ((TaskAdapter.TaskItem) adapter.getItem(0)).task;

                disableTaskCheckboxes();

                tapTargetView = TapTargetView.showFor(
                        MainActivity.this,
                        TapTarget.forView(firstTaskItem, "Edit Task", "Double tap to edit this task.")
                                .outerCircleColor(R.color.teal_700)
                                .targetCircleColor(android.R.color.white)
                                .titleTextSize(18)
                                .descriptionTextSize(14)
                                .cancelable(true)
                                .transparentTarget(true)
                                .drawShadow(true)
                                .targetRadius(130),
                        new TapTargetView.Listener() {
                            @Override
                            public void onTargetClick(TapTargetView view) {
                                // Override but do nothing to disable single tap action
                            }

                            @Override
                            public void onTargetCancel(TapTargetView view) {
                                super.onTargetCancel(view);
                                enableTaskCheckboxes();
                                tapTargetView = null;
                                Toast.makeText(MainActivity.this, "Tutorial cancelled.", Toast.LENGTH_SHORT).show();

                                suppressSearchBarFocus();
                            }
                        }
                );

                tapTargetView.setOnDoubleClickListener(() -> {
                    if (tapTargetView != null) {
                        suppressFocusAfterTutorial = true;
                        tapTargetView.dismiss(true);
                        adapter.triggerEditTask(task);
                        enableTaskCheckboxes();
                        tapTargetView = null;

                        suppressSearchBarFocus();
                    }
                });

                tapTargetView.setSwipable(false);
            } else {
                Toast.makeText(MainActivity.this, "No task available to show the tutorial.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeleteTutorial() {
        recyclerView.post(() -> {
            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(0);

            if (viewHolder != null && adapter.getItem(0) instanceof TaskAdapter.TaskItem) {
                View firstTaskItem = viewHolder.itemView;
                Task task = ((TaskAdapter.TaskItem) adapter.getItem(0)).task;
                int position = viewHolder.getAdapterPosition();

                disableTaskCheckboxes();

                tapTargetView = TapTargetView.showFor(
                        MainActivity.this,
                        TapTarget.forView(firstTaskItem, "Delete Task", "Swipe left on this task to delete it.")
                                .outerCircleColor(R.color.teal_700)
                                .targetCircleColor(android.R.color.white)
                                .titleTextSize(18)
                                .descriptionTextSize(14)
                                .cancelable(true)
                                .transparentTarget(true)
                                .drawShadow(true)
                                .targetRadius(130),
                        new TapTargetView.Listener() {
                            @Override
                            public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                                super.onTargetDismissed(view, userInitiated);
                                enableTaskCheckboxes();
                                tapTargetView = null;
                                suppressSearchBarFocus();
                            }

                            @Override
                            public void onTargetCancel(TapTargetView view) {
                                super.onTargetCancel(view);
                                enableTaskCheckboxes();
                                tapTargetView = null;
                                Toast.makeText(MainActivity.this, "Tutorial cancelled.", Toast.LENGTH_SHORT).show();
                                suppressSearchBarFocus();
                            }

                            @Override
                            public void onTargetClick(TapTargetView view) {
                                // Disable single tap during delete tutorial
                            }

                            @Override
                            public void onOuterCircleClick(TapTargetView view) {
                                // No-op: prevent dismiss on outside tap
                            }
                        }
                );

                // ✅ Perform real delete on swipe
                tapTargetView.setOnSwipeLeftListener(() -> {
                    if (tapTargetView != null) {
                        suppressFocusAfterTutorial = true;
                        tapTargetView.dismiss(true);
                        enableTaskCheckboxes();
                        tapTargetView = null;

                        // ✅ Actually delete the task
                        db.taskDao().delete(task);
                        loadTasksForCategory(currentCategory);

                        // ✅ Show Undo option
                        Snackbar.make(recyclerView, "Task deleted", Snackbar.LENGTH_LONG)
                                .setAction("UNDO", v -> {
                                    db.taskDao().insert(task);
                                    loadTasksForCategory(currentCategory);
                                }).show();
                    }
                });

                tapTargetView.setSwipable(true);
            } else {
                Toast.makeText(MainActivity.this, "No task available to show the tutorial.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSelectTaskTutorial() {
        recyclerView.post(() -> {
            RecyclerView.ViewHolder viewHolder1 = recyclerView.findViewHolderForAdapterPosition(0);

            if (viewHolder1 != null && adapter.getItem(0) instanceof TaskAdapter.TaskItem) {
                View firstTaskItem = viewHolder1.itemView;
                TaskAdapter.TaskItem taskItem = (TaskAdapter.TaskItem) adapter.getItem(0);
                Task task = taskItem.task;

                disableTaskCheckboxes();

                tapTargetView = TapTargetView.showFor(
                        MainActivity.this,
                        TapTarget.forView(firstTaskItem, "Select Task", "Long press to select this task.")
                                .outerCircleColor(R.color.teal_700)
                                .targetCircleColor(android.R.color.white)
                                .titleTextSize(18)
                                .descriptionTextSize(14)
                                .cancelable(true) // allows dismissal via back or outside tap
                                .transparentTarget(true)
                                .drawShadow(true)
                                .targetRadius(130),
                        new TapTargetView.Listener() {

                            @Override
                            public void onTargetClick(TapTargetView view) {
                                // ❌ Ignore single tap
                            }

                            @Override
                            public void onOuterCircleClick(TapTargetView view) {
                                // ❌ Ignore tap outside
                            }

                            @Override
                            public void onTargetLongClick(TapTargetView view) {
                                super.onTargetLongClick(view);

                                // ✅ Dismiss current coach mark
                                if (tapTargetView != null) {
                                    tapTargetView.dismiss(true);
                                    tapTargetView = null;
                                }

                                // ✅ Select the task
                                selectTaskForTutorial(task);

                                // ✅ Show next tutorial (tap to deselect)
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    showTapToDeselectTutorial(task);
                                }, 400);
                            }

                            @Override
                            public void onTargetCancel(TapTargetView view) {
                                super.onTargetCancel(view);
                                enableTaskCheckboxes();
                                suppressSearchBarFocus();
                                tapTargetView = null;
                                Toast.makeText(MainActivity.this, "Tutorial cancelled.", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                                super.onTargetDismissed(view, userInitiated);
                                enableTaskCheckboxes();
                                suppressSearchBarFocus();
                                tapTargetView = null;
                            }
                        }
                );

                // ❌ Prevent swipe to dismiss
                tapTargetView.setSwipable(false);
            } else {
                Toast.makeText(MainActivity.this, "Not enough tasks for tutorial.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void showTapToDeselectTutorial(Task task) {
        recyclerView.post(() -> {
            int position = adapter.getPositionForTask(task);
            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(position);

            if (viewHolder != null) {
                View taskView = viewHolder.itemView;

                tapTargetView = TapTargetView.showFor(
                        MainActivity.this,
                        TapTarget.forView(taskView, "Toggle Selection", "Tap again to deselect this task.")
                                .outerCircleColor(R.color.teal_700)
                                .targetCircleColor(android.R.color.white)
                                .titleTextSize(18)
                                .descriptionTextSize(14)
                                .cancelable(true)
                                .transparentTarget(true)
                                .drawShadow(true)
                                .targetRadius(130),
                        new TapTargetView.Listener() {
                            @Override
                            public void onTargetClick(TapTargetView view) {
                                super.onTargetClick(view);

                                // ✅ Deselect task
                                adapter.toggleSelection(task);
                                int newPosition = adapter.getPositionForTask(task);
                                if (newPosition != RecyclerView.NO_POSITION) {
                                    adapter.notifyItemChanged(newPosition);
                                }

                                // ✅ End tutorial
                                if (tapTargetView != null) {
                                    suppressFocusAfterTutorial = true; // ⬅️ Flag to suppress focus
                                    tapTargetView.dismiss(true);
                                    tapTargetView = null;
                                    enableTaskCheckboxes();
                                }
                            }

                            @Override
                            public void onTargetCancel(TapTargetView view) {
                                super.onTargetCancel(view);
                                Toast.makeText(MainActivity.this, "Tutorial cancelled.", Toast.LENGTH_SHORT).show();
                                suppressSearchBarFocus(); // ⬅️ Prevent search bar focus
                                adapter.toggleSelection(task);
                                tapTargetView = null;
                                enableTaskCheckboxes();
                            }

                            @Override
                            public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                                super.onTargetDismissed(view, userInitiated);
                                Toast.makeText(MainActivity.this, "Tutorial complete!", Toast.LENGTH_SHORT).show();
                                suppressSearchBarFocus(); // ⬅️ Prevent search bar focus
                                tapTargetView = null;
                                enableTaskCheckboxes();
                            }
                        }
                );

                tapTargetView.setSwipable(false);
            }
        });
    }
    private void selectTaskForTutorial(Task task) {
        if (!isSelectionMode) {
            adapter.enterSelectionMode();
        }

        adapter.toggleSelection(task);
        int position = adapter.getPositionForTask(task);
        if (position != RecyclerView.NO_POSITION) {
            adapter.notifyItemChanged(position);
        }
    }
    private void showTaskMarkCompletedTutorialAfterClick() {
        final RecyclerView recyclerView = findViewById(R.id.taskrecyclerview);
        recyclerView.scrollToPosition(0);

        recyclerView.postDelayed(() -> {
            final RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(0);
            if (vh == null) return;

            final View checkbox = vh.itemView.findViewById(R.id.checkbox);
            if (checkbox == null) return;

            // ✅ Get checkbox state (already marked or not)
            final boolean isChecked = ((CheckBox) checkbox).isChecked();
            final String title = isChecked ? "Mark Incomplete" : "Mark Complete";
            final String description = isChecked
                    ? "Tap this checkbox to unmark this task as completed."
                    : "Tap this checkbox to mark this task as complete.";

            checkbox.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    checkbox.getViewTreeObserver().removeOnPreDrawListener(this);

                    int[] loc = new int[2];
                    checkbox.getLocationOnScreen(loc);
                    int width = checkbox.getWidth();
                    int height = checkbox.getHeight();

                    if (width == 0 || height == 0) return true;

                    Rect bounds = new Rect(loc[0], loc[1], loc[0] + width, loc[1] + height);

                    checkbox.postDelayed(() -> {
                        tapTargetView = TapTargetView.showFor(
                                MainActivity.this,
                                TapTarget.forBounds(bounds, title, description)
                                        .transparentTarget(true)
                                        .outerCircleColor(R.color.teal_700)
                                        .targetCircleColor(android.R.color.white)
                                        .textColor(android.R.color.white)
                                        .titleTextSize(18)
                                        .descriptionTextSize(14)
                                        .drawShadow(true)
                                        .targetRadius(80)
                                        .cancelable(true),
                                new TapTargetView.Listener() {
                                    @Override
                                    public void onTargetClick(TapTargetView view) {
                                        super.onTargetClick(view);
                                        checkbox.performClick();
                                        checkbox.setEnabled(false);
                                        checkbox.postDelayed(() -> checkbox.setEnabled(true), 500);

                                        suppressFocusAfterTutorial = true;
                                        suppressSearchBarFocus();

                                        if (tapTargetView != null) {
                                            tapTargetView.dismiss(true);
                                            tapTargetView = null;
                                        }
                                    }

                                    @Override
                                    public void onTargetCancel(TapTargetView view) {
                                        super.onTargetCancel(view);
                                        suppressSearchBarFocus();
                                        tapTargetView = null;
                                        Toast.makeText(MainActivity.this, "Tutorial cancelled.", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                                        super.onTargetDismissed(view, userInitiated);
                                        suppressFocusAfterTutorial = true;
                                        suppressSearchBarFocus();
                                        tapTargetView = null;
                                    }

                                    @Override
                                    public void onOuterCircleClick(TapTargetView view) {
                                        // No-op: block outside touches
                                    }
                                }
                        );

                        tapTargetView.setSwipable(false);
                    }, 10);

                    return true;
                }
            });
        }, 10);
    }


    private void disableTaskCheckboxes() {
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (holder instanceof TaskAdapter.TaskViewHolder) {
                ((TaskAdapter.TaskViewHolder) holder).setCheckboxEnabled(false);
            }
        }
    }

    private void enableTaskCheckboxes() {
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (holder instanceof TaskAdapter.TaskViewHolder) {
                ((TaskAdapter.TaskViewHolder) holder).setCheckboxEnabled(true);
            }
        }
    }




}
