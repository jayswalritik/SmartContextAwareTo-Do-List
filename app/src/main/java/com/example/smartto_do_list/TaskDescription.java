package com.example.smartto_do_list;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.widget.Button;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.os.Looper;

import com.example.smartto_do_list.utils.GeofenceHelper;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;


public class TaskDescription extends AppCompatActivity {
    private TextInputLayout taskTitleLayout;

    private EditText taskTitleEditText;
    private TextInputLayout dateLayout;
    private EditText dateEditText;
    private String selectedDateString;
    private String selectedDateStr = null;
    private TextInputLayout timeLayout;
    private EditText timeEditText;
    private MaterialAutoCompleteTextView locationEditText;
    private List<SavedLocations> savedSavedLocationsList = new ArrayList<>();
    private TextInputLayout locationLayout;
    private double selectedLat = 0.0;
    private double selectedLon = 0.0;
    private int savedLocationId = -1;
    private TextInputLayout saveLocationLayout;
    private TextInputEditText manualLabelInput;
    private boolean isNewLocation = false;
    private boolean locationConflictResolved = false;
    private Pair<Double, Double> updatedCoordinates = null;
    private String lastConflictLabel = null;
    private final List<Button> priorityButtons = new ArrayList<>();
    private String selectedPriority = "Low";
    private MaterialAutoCompleteTextView categoryDropdown;
    private MaterialAutoCompleteTextView reminderDropdown;
    private MaterialAutoCompleteTextView repeatDropdown;
    TaskDao taskDao ;
    SavedLocationsDao savedLocationsDao;
    private boolean isEditMode = false;
    private int editingTaskId = -1;
    private Task taskBeingEdited = null;
    private boolean isLocationSelectedFromMap;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_description);
        initViews();
        initDatabase();
        setupTaskTitleValidation();
        setupListeners();
        updateLocationEndIcon();

        setupCalendarPicker();
        setupTimePicker();
        setupBackButton();
        setupPriorityButtons();
        setupDropdowns(categoryDropdown, reminderDropdown, repeatDropdown, dateEditText, timeEditText);

        // Map icon handler
        loadSavedLocationsAndSetupAutoComplete(savedLocationsDao);

        setupSaveButton(taskDao, categoryDropdown, reminderDropdown, repeatDropdown);

        editingTaskId = getIntent().getIntExtra("task_id", -1);
        isEditMode = editingTaskId != -1;
        if (isEditMode) loadTaskForEditing(editingTaskId);
    }

    private void initViews() {
        taskTitleLayout=findViewById(R.id.tasktitlelayout);
        taskTitleEditText = findViewById(R.id.tasktitleedittext);
        dateLayout = findViewById(R.id.datelayout);
        dateEditText = findViewById(R.id.dateedittext);
        timeLayout = findViewById(R.id.timelayout);
        timeEditText = findViewById(R.id.timeedittext);
        locationLayout = findViewById(R.id.locationlayout);
        locationEditText = findViewById(R.id.locationedittext);
        saveLocationLayout = findViewById(R.id.savelocationlayout);
        manualLabelInput = findViewById(R.id.savelocationedittext);
        categoryDropdown = findViewById(R.id.categorydropdown);
        reminderDropdown = findViewById(R.id.reminderdropdown);
        repeatDropdown = findViewById(R.id.repeatdropdown);

        updateLocationEndIcon();  // Set initial icon
    }

    private void initDatabase() {
        TaskDatabase db = TaskDatabase.getInstance(this);
        taskDao = db.taskDao();
        savedLocationsDao = db.savedLocationDao();
    }
    private void setupListeners() {
        locationEditText.setOnFocusChangeListener((v, hasFocus) -> {
            updateLocationEndIcon();
        });
        locationEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {

                    updateLocationEndIcon();
            }
        });

    }

    private final View.OnClickListener clearClickListener = v -> {
        clearLocation();
        updateLocationEndIcon(); // Ensure icon resets after clearing
    };

    private void updateLocationEndIcon() {
        String input = locationEditText.getText().toString().trim();
        boolean hasFocus = locationEditText.hasFocus();

        if (!input.isEmpty() && hasFocus) {
            // Use system clear icon (Material Design)
            locationLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);

            // Get the clear icon from Material Design Components
            Drawable clearIcon = AppCompatResources.getDrawable(this,
                    com.google.android.material.R.drawable.mtrl_ic_cancel);
            locationLayout.setEndIconDrawable(clearIcon);

            locationLayout.setEndIconOnClickListener(
                clearClickListener
            );
        } else {
            // Show location icon
            locationLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            locationLayout.setEndIconDrawable(R.drawable.locationicon);
            locationLayout.setEndIconOnClickListener(v -> openMapForLocation());
        }
    }
    private void clearLocation() {
        locationEditText.setText("");
        saveLocationLayout.setVisibility(View.GONE);
        locationLayout.setError(null);
        locationLayout.setErrorEnabled(false);
        locationEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        locationEditText.setKeyListener(TextKeyListener.getInstance());
        locationEditText.setFocusable(true);
        locationEditText.setFocusableInTouchMode(true);
        locationEditText.setClickable(true); // still lets user pick from dropdown

        isNewLocation = false;
        isLocationSelectedFromMap = false;
        savedLocationId = -1;
        selectedLat = selectedLon = 0.0;
        locationConflictResolved = false;

        // Reset to editable and location icon
        updateLocationEndIcon();
    }

    private void openMapForLocation() {
        Intent intent = new Intent(this, MapActivity.class);
        startActivityForResult(intent, 1001);
    }


    private void loadSavedLocationsAndSetupAutoComplete(SavedLocationsDao savedLocationsDao) {
        new Thread(() -> {
            savedSavedLocationsList = savedLocationsDao.getAllSavedLocations();
            List<String> locationLabels = new ArrayList<>();
            for (SavedLocations loc : savedSavedLocationsList) {
                locationLabels.add(loc.label);
            }

            runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        TaskDescription.this,
                        android.R.layout.simple_dropdown_item_1line,
                        locationLabels
                );

                locationEditText.setAdapter(adapter);
                locationEditText.setThreshold(1); // Show suggestions after typing 2 chars

                locationEditText.setOnClickListener(v -> {
                    if (!isNewLocation) {
                        locationEditText.showDropDown();
                    }
                });

                locationEditText.setOnItemClickListener((parent, view, position, id) -> {
                    String selectedLabel = (String) parent.getItemAtPosition(position);
                    SavedLocations selectedLocation = null;

                    for (SavedLocations loc : savedSavedLocationsList) {
                        if (loc.label.equals(selectedLabel)) {
                            selectedLocation = loc;
                            break;
                        }
                    }

                    if (selectedLocation != null) {
                        selectedLat = selectedLocation.latitude;
                        selectedLon = selectedLocation.longitude;
                        savedLocationId = selectedLocation.id;
                        isNewLocation = false;

                        // Hide manual input field
                        manualLabelInput.setVisibility(View.GONE);
                        manualLabelInput.setText("");
                    }
                });

                locationEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        // Call validation on every text change
                        validateLocationInput();
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {

                    }
                });
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            selectedLat = data.getDoubleExtra("selected_lat", 0.0);
            selectedLon = data.getDoubleExtra("selected_lon", 0.0);
            String name = data.getStringExtra("selected_name");

            SavedLocations nearbyLocation = findNearbyLocation(savedSavedLocationsList, selectedLat, selectedLon, 10);

            if (nearbyLocation != null) {
                showNearbyLocationDialog(nearbyLocation, name);
            } else {
                // ✅ This is where you should always restore correct state
                isNewLocation = true;
                isLocationSelectedFromMap = true;
                locationConflictResolved = true;

                applyNewLocationUI(name, true, -1);  // This re-shows the manual label input
            }
        }

    }

    private SavedLocations findNearbyLocation(List<SavedLocations> locations, double lat, double lon, float maxDistanceMeters) {
        float[] result = new float[1];
        for (SavedLocations loc : locations) {
            Location.distanceBetween(lat, lon, loc.latitude, loc.longitude, result);
            if (result[0] <= maxDistanceMeters) {
                return loc;
            }
        }
        return null;
    }

    private void showNearbyLocationDialog(SavedLocations nearbyLocation, String fallbackName ) {
        new AlertDialog.Builder(this)
                .setTitle("Nearby Saved Location Found")
                .setMessage("This location is near an existing saved location: '" + nearbyLocation.label + "'. What do you want to do?")
                .setPositiveButton("Use existing location", (dialog, which) -> {
                    selectedLat = nearbyLocation.latitude;
                    selectedLon = nearbyLocation.longitude;
                    applyNewLocationUI(nearbyLocation.label, false, nearbyLocation.id);
                    locationConflictResolved = true;
                })
                .setNegativeButton("Create new location", (dialog, which) -> {
                    // Use selectedLat, selectedLon as-is (new coords)
                    applyNewLocationUI(fallbackName, true, -1);
                    locationConflictResolved = true;
                })
                .setCancelable(true)
                .show();
    }

    private void applyNewLocationUI(String label, boolean isNew, int locationId) {
        isNewLocation = isNew;
        savedLocationId = locationId;

        // Clear keyboard/input
        locationEditText.setInputType(InputType.TYPE_NULL);
        locationEditText.setKeyListener(null);
        locationEditText.setFocusable(true);
        locationEditText.setClickable(true);
        locationEditText.setEnabled(true);

        locationEditText.setText(label);
        locationLayout.setError(null);
        locationLayout.setErrorEnabled(false);

        if (isNew) {
            // ✅ Force visibility regardless of previous state
            manualLabelInput.setVisibility(View.VISIBLE);
            manualLabelInput.setText(""); // reset

            saveLocationLayout.setVisibility(View.VISIBLE); // In case its parent is hidden

            Toast.makeText(this, "Label selected location.", Toast.LENGTH_LONG).show();

            if (manualLabelInput.getTag() == null) {
                manualLabelInput.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String inputLabel = s.toString().trim();

                        if (!inputLabel.isEmpty()) {
                            boolean labelExists = false;
                            for (SavedLocations loc : savedSavedLocationsList) {
                                if (loc.label.equalsIgnoreCase(inputLabel.trim())) {
                                    labelExists = true;
                                    break;
                                }
                            }

                            if (labelExists) {
                                saveLocationLayout.setError("Location label already exists");
                                saveLocationLayout.setErrorEnabled(true);
                                locationConflictResolved = false;
                            } else {
                                saveLocationLayout.setError(null);
                                saveLocationLayout.setErrorEnabled(false);
                                locationConflictResolved = true;
                            }
                        } else {
                            saveLocationLayout.setError(null);
                            saveLocationLayout.setErrorEnabled(false);
                            locationConflictResolved = false;
                        }
                    }

                    @Override public void afterTextChanged(Editable s) {}
                });

                manualLabelInput.setTag("watcher-attached");
            }
        } else {
            manualLabelInput.setVisibility(View.GONE);
            manualLabelInput.setText("");
            saveLocationLayout.setError(null);
            saveLocationLayout.setErrorEnabled(false);
        }
    }

    private void showConflictDialog(String currentLabel, SavedLocations existingLocation) {
        lastConflictLabel = currentLabel;

        new AlertDialog.Builder(this)
                .setTitle("Location Name Conflict")
                .setMessage("Location with name \"" + currentLabel + "\" already exists. Please select an option below.")
                .setPositiveButton("Update coordinates", (dialog, which) -> {
                    locationConflictResolved = true;
                    updatedCoordinates = new Pair<>(selectedLat, selectedLon);
                    lastConflictLabel = currentLabel;

                    // ✅ Clear any manual input error (optional but recommended)
                    EditText saveLocationEditText = findViewById(R.id.savelocationedittext);
                    if (saveLocationEditText != null) {
                        saveLocationLayout.setError(null);
                        saveLocationLayout.setErrorEnabled(false);
                    }
                })

                .setNegativeButton("Change name", (dialog, which) -> {
                    showLabelChangeDialog(currentLabel);
                })
                .setCancelable(true)
                .show();
    }

    private void showLabelChangeDialog(String originalLabel) {
        final EditText input = new EditText(this);
        input.setText(originalLabel);

        new AlertDialog.Builder(this)
                .setTitle("Change Label")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String newLabel = input.getText().toString().trim();
                    EditText saveLocationEditText = findViewById(R.id.savelocationedittext);

                    if (!newLabel.isEmpty() && !newLabel.equals(originalLabel)) {
                        TaskDatabase db = TaskDatabase.getInstance(TaskDescription.this);
                        SavedLocationsDao savedLocationsDao = db.savedLocationDao();

                        new Thread(() -> {
                            SavedLocations existing = savedLocationsDao.getLocationByLabel(newLabel);

                            runOnUiThread(() -> {
                                if (existing != null) {
                                    showConflictDialog(newLabel, existing);
                                    locationConflictResolved = false;
                                    lastConflictLabel = newLabel;
                                } else {
                                    saveLocationEditText.setText(newLabel);
                                    saveLocationLayout.setError(null);
                                    saveLocationLayout.setErrorEnabled(false);
                                    locationConflictResolved = true;
                                    updatedCoordinates = null;
                                    lastConflictLabel = null;
                                }
                            });
                        }).start();
                    } else {
                        saveLocationEditText.setText(originalLabel);
                        locationConflictResolved = false;
                        lastConflictLabel = originalLabel;
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    locationConflictResolved = false;
                    lastConflictLabel = originalLabel;
                })
                .show();
    }
    private void validateLocationInput() {
        String input = locationEditText.getText().toString().trim();

        if (!isNewLocation && !input.isEmpty()) {
            boolean possibleMatch = false;
            for (SavedLocations loc : savedSavedLocationsList) {
                if (loc.label.toLowerCase().startsWith(input.toLowerCase())) {
                    possibleMatch = true;
                    break;
                }
            }
            if (!possibleMatch) {
                locationLayout.setError("No saved location matches this input");
                locationLayout.setErrorEnabled(true);
            } else {
                locationLayout.setError(null);
                locationLayout.setErrorEnabled(false);
            }
        } else {
            // Clear error if new location or input is empty
            locationLayout.setError(null);
            locationLayout.setErrorEnabled(false);
        }
    }

    private void setupTaskTitleValidation() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final int delay = 500; // debounce delay in milliseconds

        taskTitleEditText.addTextChangedListener(new TextWatcher() {
            Runnable workRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (workRunnable != null) {
                    handler.removeCallbacks(workRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                String title = s.toString().trim();

                workRunnable = () -> {
                    if (!title.isEmpty()) {
                        new Thread(() -> {
                            try {
                                Task existingTask = taskDao.getPendingTaskByTitleIgnoreCase(title);
                                runOnUiThread(() -> {
                                    if (existingTask != null) {
                                        if (!(isEditMode && taskBeingEdited != null && existingTask.id == taskBeingEdited.id)) {
                                            taskTitleLayout.setError("Task title already exists");
                                            taskTitleLayout.setErrorEnabled(true);
                                        } else {
                                            taskTitleLayout.setError(null);
                                            taskTitleLayout.setErrorEnabled(false);
                                        }
                                    } else {
                                        taskTitleLayout.setError(null);
                                        taskTitleLayout.setErrorEnabled(false);
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                runOnUiThread(() -> {
                                    // Show generic error or clear error
                                    taskTitleLayout.setError(null);
                                    taskTitleLayout.setErrorEnabled(false);
                                });
                            }
                        }).start();
                    } else {
                        runOnUiThread(() -> {
                            taskTitleLayout.setError(null);
                            taskTitleLayout.setErrorEnabled(false);
                        });
                    }
                };

                handler.postDelayed(workRunnable, delay);
            }
        });
    }

    //Date setup:

    /**
     * Sets up intelligent formatting and validation for the date EditText field.
     * Auto-formats input as YYYY-MM-DD and checks if the date is valid and not in the past.
     */
    private void setupCalendarPicker() {
        // Find views from layout
        TextInputLayout dateLayout = findViewById(R.id.datelayout);
        EditText dateEditText = findViewById(R.id.dateedittext);
        EditText timeEditText = findViewById(R.id.timeedittext);
        Button saveButton = findViewById(R.id.savetaskbutton);
        Calendar calendar = Calendar.getInstance();

        // 📅 Handle calendar icon click to open DatePickerDialog
        dateLayout.setEndIconOnClickListener(v -> {
            preserveTimeError(timeEditText);

            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    TaskDescription.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        // Format and display selected date
                        Calendar selectedCalendar = Calendar.getInstance();
                        selectedCalendar.set(selectedYear, selectedMonth, selectedDay);

                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        String formattedDate = sdf.format(selectedCalendar.getTime());

                        selectedDateString = formattedDate;
                        dateEditText.setText(formattedDate);
                        Toast.makeText(TaskDescription.this, "Selected Date: " + formattedDate, Toast.LENGTH_SHORT).show();

                        // 🔥 Revalidate time after date change
                        isValidTimeInput(timeEditText, 10, isEditMode);
                    },
                    year, month, day
            );

            // ⛔ Prevent selecting past dates
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });

        // 🧠 TextWatcher to handle auto-formatting and validation
        dateEditText.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                // Remove non-digit characters
                String input = s.toString().replaceAll("[^\\d]", "");
                if (input.length() > 8) input = input.substring(0, 8);

                // Format strictly to YYYY-MM-DD
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < input.length(); i++) {
                    formatted.append(input.charAt(i));
                    if ((i == 3 || i == 5) && i != input.length() - 1) {
                        formatted.append("-");
                    }
                }

                String newFormattedDate = formatted.toString();

                if (!newFormattedDate.equals(dateEditText.getText().toString())) {
                    dateEditText.setText(newFormattedDate);
                    dateEditText.setSelection(newFormattedDate.length());
                }

                // Validate only when full date is entered
                if (newFormattedDate.length() == 10) {
                    isValidDateInput(dateEditText, isEditMode);
                    selectedDateStr = newFormattedDate;
                    isValidTimeInput(timeEditText, 10, isEditMode);
                } else {
                    dateLayout.setError(null);
                    dateLayout.setErrorEnabled(false);
                }

                isFormatting = false;
            }
        });

        // Validate date on focus loss
        dateEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                isValidDateInput(dateEditText, isEditMode);
            }
        });
    }

    // Only regex pattern check
    private boolean isValidPattern(String dateStr) {
        return dateStr.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    // Semantic day/month check (valid month and day count)
    private boolean isValidDateParts(String dateStr) {
        String[] parts = dateStr.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);

        if (month < 1 || month > 12) return false;

        int[] daysInMonth = {31, (isLeapYear(year) ? 29 : 28), 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        return day >= 1 && day <= daysInMonth[month - 1];
    }

    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    // Check not a past date (for non-edit mode)
    private boolean isValidDate(String inputDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(false);
            Date parsedDate = sdf.parse(inputDate);

            Calendar inputCal = Calendar.getInstance();
            inputCal.setTime(parsedDate);
            inputCal.set(Calendar.HOUR_OF_DAY, 0);
            inputCal.set(Calendar.MINUTE, 0);
            inputCal.set(Calendar.SECOND, 0);
            inputCal.set(Calendar.MILLISECOND, 0);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            return !inputCal.before(today);
        } catch (ParseException e) {
            return false;
        }
    }

    private void isValidDateInput(EditText et, boolean onlyCheckFormat) {
        String input = et.getText().toString();
        if (input.isEmpty()) return;

        if (input.length() != 10 || !isValidPattern(input)) {
            dateLayout.setError("Invalid Format");
            dateLayout.setErrorEnabled(true);
        } else if (!isValidDateParts(input)) {
            dateLayout.setError("Invalid Date");
            dateLayout.setErrorEnabled(true);
        } else if (!onlyCheckFormat && !isValidDate(input)) {
            dateLayout.setError("Date cannot be in the past");
            dateLayout.setErrorEnabled(true);
        } else {
            dateLayout.setError(null);
            dateLayout.setErrorEnabled(false);
        }
    }


    // Time Setup
    private void setupTimePicker() {
        TextInputLayout timeLayout = findViewById(R.id.timelayout);
        EditText timeEditText = findViewById(R.id.timeedittext);
        EditText dateEditText = findViewById(R.id.dateedittext);

        Calendar calendar = Calendar.getInstance();

        timeLayout.setEndIconOnClickListener(v -> {
            isValidTimeInput(timeEditText, 10, isEditMode);
            isValidDateInput(dateEditText, isEditMode);

            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            String currentTime = timeEditText.getText().toString();
            if (isValidTimeFormat(currentTime)) {
                String[] parts = currentTime.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            }

            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    TaskDescription.this,
                    (view, selectedHour, selectedMinute) -> {
                        String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
                        if (!isTimeValidWithBuffer(formattedTime, 10)) {
                            timeLayout.setError("Invalid Time (Keep 10 minutes ahead from now)");
                            timeLayout.setErrorEnabled(true);
                        } else {
                            timeLayout.setError(null);
                            timeLayout.setErrorEnabled(false);
                            timeEditText.setText(formattedTime);
                        }
                    },
                    hour, minute, true
            );
            timePickerDialog.show();
        });

        // 🧠 Auto-format and validate while typing
        timeEditText.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String input = s.toString().replaceAll("[^\\d]", "");
                if (input.length() > 4) input = input.substring(0, 4);

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < input.length(); i++) {
                    formatted.append(input.charAt(i));
                    if (i == 1 && input.length() > 2) {
                        formatted.append(":");
                    }
                }

                String newFormattedTime = formatted.toString();
                if (!newFormattedTime.equals(timeEditText.getText().toString())) {
                    timeEditText.setText(newFormattedTime);
                    timeEditText.setSelection(newFormattedTime.length());
                }

                if (newFormattedTime.length() == 5) {
                    isValidTimeInput(timeEditText, 10, isEditMode);
                }

                isFormatting = false;
            }
        });
        timeEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                preserveTimeError(timeEditText);
            }
        });

    }

    private boolean isValidTimeFormat(String timeStr) {
        return timeStr.matches("([01]\\d|2[0-3]):[0-5]\\d");
    }

    private boolean isTimeValidWithBuffer(String timeStr, int minMinutesAhead) {
        if(!isEditMode) {
            try {
                SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                EditText dateEditText = findViewById(R.id.dateedittext);
                // ✅ Use dateEditText content if valid, otherwise fallback to today
                String dateInput = dateEditText.getText().toString();
                String dateToUse = (dateInput != null && dateInput.length() == 10)
                        ? dateInput
                        : new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                Date selectedDateTime = dateTimeFormat.parse(dateToUse + " " + timeStr);

                Calendar now = Calendar.getInstance();
                Calendar selectedCal = Calendar.getInstance();
                selectedCal.setTime(selectedDateTime);

                if (isSameDay(now, selectedCal)) {
                    long diffMillis = selectedCal.getTimeInMillis() - now.getTimeInMillis();
                    return diffMillis >= (minMinutesAhead * 60 * 1000);
                } else {
                    return true;
                }
            } catch (ParseException e) {
                return false;
            }
        }
        else{
            return true;
        }
    }

    private void isValidTimeInput(EditText timeEditText, int minutesAhead, boolean onlyCheckFormat) {
        String input = timeEditText.getText().toString();

        if (input.isEmpty()) {
            timeLayout.setError(null);
            timeLayout.setErrorEnabled(false);
            return;
        }

        if (!isValidTimeFormat(input)) {
            timeLayout.setError("Invalid Format");
            timeLayout.setErrorEnabled(true);
            return;
        }

        if (!onlyCheckFormat && !isTimeValidWithBuffer(input, minutesAhead)) {
            timeLayout.setError("Invalid Time (Keep " + minutesAhead + " minutes ahead from now)");
            timeLayout.setErrorEnabled(true);
        } else {
            timeLayout.setError(null);
            timeLayout.setErrorEnabled(false);
        }
    }

    private void preserveTimeError(EditText timeEditText) {
        String input = timeEditText.getText().toString().trim();

        // ✅ Don't validate if the field is empty
        if (input.isEmpty()) {
            timeLayout.setError(null);
            timeLayout.setErrorEnabled(false);
            return;
        }

        CharSequence error = timeEditText.getError();

        if (error != null && error.toString().contains("Invalid Time Format")) {
            timeLayout.setError("Invalid Time Format");
            timeLayout.setErrorEnabled(true);
        } else if (error != null && error.toString().contains("Invalid Time")) {
            timeLayout.setError("Invalid Time (Keep 10 minutes ahead from now)");
            timeLayout.setErrorEnabled(true);
        } else if (!isValidTimeFormat(input)) {
            timeLayout.setError("Invalid Time Format");
            timeLayout.setErrorEnabled(true);
        } else if (!isTimeValidWithBuffer(input, 10)) {
            timeLayout.setError("Invalid Time (keep 10 minutes ahead from now)");
            timeLayout.setErrorEnabled(true);
        } else {
            timeLayout.setError(null); // Valid input
            timeLayout.setErrorEnabled(false);
        }
    }


    private boolean isSameDay(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private void setupBackButton() {
        ImageButton backButton = findViewById(R.id.backiconbutton);

        backButton.setOnClickListener(v -> {
            onBackPressed();
        });
    }

    private void setupPriorityButtons() {
        LinearLayout priorityContainer = findViewById(R.id.prioritycontainer);
        priorityButtons.clear();

        String[] priorities = {"Low", "Medium", "High"};

        for (String priority : priorities) {
            Button button = new Button(this);
            button.setText(priority);
            button.setAllCaps(false);
            button.setTextColor(ContextCompat.getColor(this, R.color.buttontextcolor));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            params.setMargins(12, 6, 12, 6);
            button.setLayoutParams(params);

            // Default unselected style for all buttons
            button.setBackground(ContextCompat.getDrawable(this, R.drawable.selectablebuttonbackground));

            priorityContainer.addView(button);
            priorityButtons.add(button);

            EditText dateEditText = findViewById(R.id.dateedittext);

            button.setOnClickListener(v -> {
                isValidDateInput(dateEditText, isEditMode);
                preserveTimeError(timeEditText);
                selectedPriority = priority;
                highlightPriority();
                Toast.makeText(this, "Priority set to: " + priority, Toast.LENGTH_SHORT).show();
            });
        }

        highlightPriority();
    }

    private void setupDropdowns(MaterialAutoCompleteTextView categoryDropdown,
                                MaterialAutoCompleteTextView reminderDropdown,
                                MaterialAutoCompleteTextView repeatDropdown,
                                EditText dateEditText,
                                EditText timeEditText) {

        // Category Dropdown
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                this, R.array.categories, android.R.layout.simple_dropdown_item_1line
        );
        categoryDropdown.setAdapter(categoryAdapter);

        // Reminder Dropdown
        ArrayAdapter<CharSequence> reminderAdapter = ArrayAdapter.createFromResource(
                this, R.array.reminders, android.R.layout.simple_dropdown_item_1line
        );
        reminderDropdown.setAdapter(reminderAdapter);

        // Repeat Dropdown
        ArrayAdapter<CharSequence> repeatAdapter = ArrayAdapter.createFromResource(
                this, R.array.repetitions, android.R.layout.simple_dropdown_item_1line
        );
        repeatDropdown.setAdapter(repeatAdapter);

        // Listener for Category
        categoryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            isValidDateInput(dateEditText, isEditMode);
            preserveTimeError(timeEditText);
        });

        // Listener for Reminder
        reminderDropdown.setOnItemClickListener((parent, view, position, id) -> {
            isValidDateInput(dateEditText, isEditMode);
            preserveTimeError(timeEditText);

            String selected = parent.getItemAtPosition(position).toString();
            if (selected.equals("Custom")) {
                showCustomDialog(reminderDropdown, "reminder");
            }
        });

        // Listener for Repeat
        repeatDropdown.setOnItemClickListener((parent, view, position, id) -> {
            isValidDateInput(dateEditText, isEditMode);
            preserveTimeError(timeEditText);

            String selected = parent.getItemAtPosition(position).toString();
            if (selected.equals("Custom")) {
                showCustomDialog(repeatDropdown, "repeat");
            }
        });
    }

    private boolean setDropdownSelection(MaterialAutoCompleteTextView dropdown, String value) {
        ArrayAdapter adapter = (ArrayAdapter) dropdown.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equals(value)) {
                dropdown.setText(adapter.getItem(i).toString(), false);
                break;
            }
        }
        return false;
    }

    private void showCustomDialog(MaterialAutoCompleteTextView dropdown, String type) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.custom_reminder, null);

        EditText valueInput = dialogView.findViewById(R.id.custom_value);
        Spinner unitSpinner = dialogView.findViewById(R.id.unit_spinner);

        ArrayAdapter<CharSequence> unitAdapter = ArrayAdapter.createFromResource(
                this, R.array.custom_units, android.R.layout.simple_spinner_item);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);

        // 🔁 Set hint and input type based on selected unit
        unitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String unit = unitSpinner.getSelectedItem().toString();

                if (unit.equalsIgnoreCase("hrs")) {
                    valueInput.setHint("HH:MM (08:30)");
                    valueInput.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
                } else if (unit.equalsIgnoreCase("mins")) {
                    valueInput.setHint("Value (1–59)");
                    valueInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                } else {
                    valueInput.setHint("Enter value");
                    valueInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                valueInput.setHint("Enter value");
            }
        });

        String title = type.equals("repeat") ? "Custom Repetition" : "Custom Reminder";

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton("Set", null)  // Overridden below
                .setNegativeButton("Cancel", null) // Overridden below
                .create();

        dialog.setOnShowListener(dlg -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            positive.setOnClickListener(v -> {
                String value = valueInput.getText().toString().trim();
                String unit = unitSpinner.getSelectedItem().toString();

                if (value.isEmpty()) {
                    valueInput.setError("Value required");
                    return;
                }

                if (unit.equalsIgnoreCase("hrs")) {
                    if (!value.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
                        valueInput.setError("Enter time in HH:MM format (00:00 to 23:59)");
                        return;
                    }
                } else if (unit.equalsIgnoreCase("mins")) {
                    try {
                        int mins = Integer.parseInt(value);
                        if (mins < 1 || mins > 59) {
                            valueInput.setError("Enter a value between 1 and 59 minutes");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        valueInput.setError("Invalid number");
                        return;
                    }
                }

                String formattedText;
                if (type.equals("repeat")) {
                    formattedText = "Every " + value + " " + unit;
                } else {
                    formattedText = value + " " + unit + " before";
                }

                dropdown.setText(formattedText, false);
                dialog.dismiss();
            });

            negative.setOnClickListener(v -> {
                dropdown.setText("", false); // Clear selection if canceled
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    // Sets up the save button to validate input and save task when clicked
    private void setupSaveButton(TaskDao taskDao, MaterialAutoCompleteTextView categoryDropdown,
                                 MaterialAutoCompleteTextView reminderDropdown, MaterialAutoCompleteTextView repeatDropdown) {
        Button saveButton = findViewById(R.id.savetaskbutton);

        saveButton.setOnClickListener(v -> {
            EditText titleEditText = findViewById(R.id.tasktitleedittext);
            EditText dateEditText = findViewById(R.id.dateedittext);
            EditText timeEditText = findViewById(R.id.timeedittext);
            TextInputLayout timeLayout = findViewById(R.id.timelayout);
            EditText noteEditText = findViewById(R.id.noteedittext);

            String title = titleEditText.getText().toString().trim();
            String dateInput = dateEditText.getText().toString().trim();
            String timeInput = timeEditText.getText().toString().trim();
            String noteText = noteEditText.getText().toString().trim();

            String locationLabel;
            if (isNewLocation) {
                locationLabel = manualLabelInput.getText().toString().trim();
            } else {
                locationLabel = locationEditText.getText().toString().trim();
            }

            if (!isNewLocation && locationEditText.getError() != null) {
                toast("Please fix the location before saving");
                return;
            }

            if (title.isEmpty()) {
                toast("Title is required");
                return;
            }

            if (!isEditMode && titleEditText.getError() != null) {
                toast("Please fix the title before saving");
                return;
            }

            String taskDate = resolveDate(dateInput, dateEditText);
            if (taskDate == null) return;

            boolean isTodayOrEmptyDate;
            if (dateInput.isEmpty()) {
                isTodayOrEmptyDate = true;
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String todayStr = sdf.format(new Date());
                isTodayOrEmptyDate = todayStr.equals(dateInput);
            }

            String reminderText = reminderDropdown.getText().toString().toLowerCase();
            String repeatText = repeatDropdown.getText().toString().toLowerCase();
            boolean usesShortTime = reminderText.matches(".*\\b(min|minute|mins|hr|hour|hrs|hours)\\b")
                    || repeatText.matches(".*\\b(min|minute|mins|hr|hour|hrs|hours)\\b");

            if (isTodayOrEmptyDate && usesShortTime && timeInput.isEmpty()) {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MINUTE, 0);
                SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                timeInput = tf.format(calendar.getTime());
                Toast.makeText(this, "Time auto-set (1 min ahead) during save", Toast.LENGTH_SHORT).show();
            }

            if (!timeInput.isEmpty()) {
                isValidTimeInput(timeEditText, 1, isEditMode);
                if (timeLayout.getError() != null) {
                    toast("Fix the time before saving");
                    return;
                }
            }

            String taskTime;
            if ((timeInput.isEmpty() || "00:00".equals(timeInput))
                    && !reminderText.matches(".*\\b(min|minute|mins|hr|hour|hrs|hours)\\b")
                    && !repeatText.matches(".*\\b(min|minute|mins|hr|hour|hrs|hours)\\b")) {
                taskTime = "";
            } else {
                taskTime = timeInput;
            }

            Log.d("DEBUG_TASK_TIME", "Saving taskTime: \"" + taskTime + "\" for repeat: " + repeatText);

            String currentStatus = "pending";
            if (isEditMode && taskBeingEdited != null && taskBeingEdited.taskStatus != null) {
                currentStatus = taskBeingEdited.taskStatus;
            }

            String completedDateToUse = null;
            if (isEditMode && taskBeingEdited != null) {
                if ("completed".equalsIgnoreCase(taskBeingEdited.taskStatus)) {
                    completedDateToUse = taskBeingEdited.completedDate;
                }
            }

            NoteDao noteDao = TaskDatabase.getInstance(this).noteDao();
            int noteId = -1;
            if (!noteText.isEmpty()) {
                if (isEditMode && taskBeingEdited != null && taskBeingEdited.noteId != -1) {
                    Note existingNote = noteDao.getNoteById(taskBeingEdited.noteId);
                    if (existingNote != null) {
                        existingNote.content = noteText;
                        noteDao.update(existingNote);
                        noteId = existingNote.id;
                    }
                } else {
                    Note note = new Note();
                    note.content = noteText;
                    noteId = (int) noteDao.insert(note);
                }
            }

            TaskDatabase db = TaskDatabase.getInstance(this);
            SavedLocationsDao savedLocationsDao = db.savedLocationDao();

            if (!isNewLocation && !locationLabel.isEmpty()) {
                boolean matchFound = false;
                for (SavedLocations loc : savedSavedLocationsList) {
                    if (loc.label.equalsIgnoreCase(locationLabel)) {
                        matchFound = true;
                        savedLocationId = loc.id;
                        break;
                    }
                }
                if (!matchFound) {
                    locationLayout.setError("Invalid location");
                    locationLayout.setErrorEnabled(true);
                    toast("Please select a valid saved location from the dropdown, or leave it empty");
                    return;
                }
            }

            if (locationEditText.getText().length() == 0 && locationLabel.isEmpty()) {
                Task task = buildTask(title, taskDate, taskTime, -1, categoryDropdown, reminderDropdown, repeatDropdown, noteId, currentStatus, completedDateToUse);
                saveTask(taskDao, task);
                return;
            }

            if (isNewLocation) {
                if (locationLabel.isEmpty()) {
                    saveTaskWithUnnamedLocation(taskDao, savedLocationsDao, title, taskDate, taskTime, categoryDropdown, reminderDropdown, repeatDropdown, noteId, completedDateToUse);

                } else {
                    saveTaskWithNamedLocation(taskDao, savedLocationsDao, locationLabel, title, taskDate, taskTime, categoryDropdown, reminderDropdown, repeatDropdown, noteId, completedDateToUse);
                }
            } else {
                Task task = buildTask(title, taskDate, taskTime, savedLocationId, categoryDropdown, reminderDropdown, repeatDropdown, noteId, currentStatus, completedDateToUse);
                saveTask(taskDao, task);
            }
        });
    }

    // Builds and returns a Task object
    private Task buildTask(String title, String date, String time, int locationId,
                           MaterialAutoCompleteTextView category, MaterialAutoCompleteTextView reminder,
                           MaterialAutoCompleteTextView repeat, int noteId, String taskStatus, String completedDate) {
        Task task = new Task();
        task.title = title;
        task.date = date;
        task.time = time;
        task.locationId = locationId;
        task.priority = selectedPriority;
        task.category = category.getText().toString().trim();
        task.reminder = reminder.getText().toString().trim();
        task.repeat = repeat.getText().toString().trim();
        task.noteId = noteId;
        task.taskStatus = taskStatus != null ? taskStatus : "pending";
        task.completedDate = completedDate;  // Preserve completedDate
        return task;
    }


    // Saves a task in the background thread and registers geofence if location exists
    private void saveTask(TaskDao taskDao, Task task) {
        new Thread(() -> {
            Context context = TaskDescription.this;
            TaskDatabase db = TaskDatabase.getInstance(context);

            // Insert or update the task
            if (isEditMode) {
                task.id = editingTaskId;
                taskDao.update(task);
            } else {
                long insertedId = taskDao.insert(task);
                task.id = (int) insertedId;  // Cast long to int safely
            }

            // Register geofence if task has a valid location
            if (task.locationId != -1) {
                SavedLocationsDao locationsDao = db.savedLocationDao();
                SavedLocations loc = locationsDao.getLocationById(task.locationId);

                if (loc != null) {
                    runOnUiThread(() -> {
                        // ✅ Step-by-Step Fix: Check for location permissions before registering geofence
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                            GeofenceHelper geofenceHelper = new GeofenceHelper(this);
                            geofenceHelper.registerGeofenceForTask(task, loc);

                        } else {
                            Toast.makeText(this, "Location permission missing — can't set geofence", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            // Schedule time-based notification
            NotificationScheduler.scheduleTaskNotification(context, task);

            // UI updates after saving
            runOnUiThread(() -> {
                locationEditText.setEnabled(true);
                locationEditText.setText("");
                manualLabelInput.setVisibility(View.GONE);
                manualLabelInput.setText("");
                isNewLocation = false;
                savedLocationId = -1;

                toast(isEditMode ? "Task updated" : "Task saved");
                finish();
            });
        }).start();
    }



    // Auto-generates a label for unnamed location and saves task
    private void saveTaskWithUnnamedLocation(TaskDao taskDao, SavedLocationsDao savedLocationsDao,
                                             String title, String date, String time,
                                             MaterialAutoCompleteTextView category, MaterialAutoCompleteTextView reminder,
                                             MaterialAutoCompleteTextView repeat, int noteId, String completedDate) {
        new Thread(() -> {
            int count = savedLocationsDao.getLocationsWithUnnamedLabel().size() + 1;
            String autoLabel = "Unnamed Location " + count;

            SavedLocations newLoc = new SavedLocations();
            newLoc.label = autoLabel;
            newLoc.latitude = selectedLat;
            newLoc.longitude = selectedLon;
            int locationId = (int) savedLocationsDao.insert(newLoc);

            Task task = buildTask(title, date, time, locationId, category, reminder, repeat, noteId,
                    isEditMode && taskBeingEdited != null ? taskBeingEdited.taskStatus : "pending",
                    completedDate);
            if (isEditMode) {
                task.id = editingTaskId;
            }
            saveTask(taskDao, task);
        }).start();
    }

    // Saves task with named location, handles conflict resolution if needed
    private void saveTaskWithNamedLocation(TaskDao taskDao, SavedLocationsDao savedLocationsDao,
                                           String label, String title, String date, String time,
                                           MaterialAutoCompleteTextView category, MaterialAutoCompleteTextView reminder,
                                           MaterialAutoCompleteTextView repeat, int noteId, String completedDate) {
        new Thread(() -> {
            SavedLocations existingLoc = savedLocationsDao.getLocationByLabel(label);

            runOnUiThread(() -> {
                if (existingLoc != null && (!locationConflictResolved || !label.equals(lastConflictLabel))) {
                    showConflictDialog(label, existingLoc);
                    toast("Resolve location conflict first.");
                    return;
                }

                new Thread(() -> {
                    int locationId;

                    if (existingLoc != null) {
                        if (updatedCoordinates != null) {
                            existingLoc.latitude = updatedCoordinates.first;
                            existingLoc.longitude = updatedCoordinates.second;
                            savedLocationsDao.update(existingLoc);
                        }
                        locationId = existingLoc.id;
                    } else {
                        SavedLocations newLoc = new SavedLocations();
                        newLoc.label = label;
                        newLoc.latitude = updatedCoordinates != null ? updatedCoordinates.first : selectedLat;
                        newLoc.longitude = updatedCoordinates != null ? updatedCoordinates.second : selectedLon;
                        locationId = (int) savedLocationsDao.insert(newLoc);
                    }

                    Task task = buildTask(title, date, time, locationId, category, reminder, repeat, noteId,
                            isEditMode && taskBeingEdited != null ? taskBeingEdited.taskStatus : "pending",
                            completedDate);
                    if (isEditMode) {
                        task.id = editingTaskId;
                    }

                    saveTask(taskDao, task);

                    updatedCoordinates = null;
                    locationConflictResolved = false;
                    lastConflictLabel = null;
                }).start();
            });
        }).start();
    }

    // Validates the date format and ensures it's not in the past
    private String resolveDate(String inputDate, EditText dateEditText) {
        if (inputDate.isEmpty()) {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            selectedDateString = today;
            return today;
        }

        // 🔍 Format pattern check (regex only)
        if (inputDate.length() != 10 || !isValidPattern(inputDate)) {
            dateLayout.setError("Invalid Format");
            dateLayout.setErrorEnabled(true);
            toast("Please fix the date format before saving.");
            return null;
        }

        // 🔍 Semantic date parts check (valid month/day)
        if (!isValidDateParts(inputDate)) {
            dateLayout.setError("Invalid Date");
            dateLayout.setErrorEnabled(true);
            toast("Please fix the date before saving.");
            return null;
        }

        // 🔍 Past date check (only if NOT editing)
        if (!isEditMode && !isValidDate(inputDate)) {
            dateLayout.setError("Date cannot be in the past");
            dateLayout.setErrorEnabled(true);
            toast("Please select a date that is today or later.");
            return null;
        }

        dateLayout.setError(null);
        dateLayout.setErrorEnabled(false);
        selectedDateString = inputDate;
        return inputDate;
    }

    // Shows toast message to user
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // When editing task:
    private void loadTaskForEditing(int taskId) {
        TaskDatabase db = TaskDatabase.getInstance(this);
        TaskDao taskDao = db.taskDao();
        SavedLocationsDao locationDao = db.savedLocationDao();
        NoteDao noteDao = db.noteDao();

        new Thread(() -> {
            Task task = taskDao.getTaskById(taskId);
            if (task == null) return;

            SavedLocations location = (task.locationId != -1) ? locationDao.getLocationById(task.locationId) : null;
            Note note = (task.noteId != -1) ? noteDao.getNoteById(task.noteId) : null;

            taskBeingEdited = task;
            editingTaskId = taskId;
            isEditMode = true;

            runOnUiThread(() -> {
                ((TextInputEditText) findViewById(R.id.tasktitleedittext)).setText(task.title);
                ((TextInputEditText) findViewById(R.id.dateedittext)).setText(task.date);
                ((TextInputEditText) findViewById(R.id.timeedittext)).setText(task.time);

                selectedPriority = task.priority;
                highlightPriority();

                setDropdownSelection(categoryDropdown, task.category);
                // For Reminder Dropdown
                if (!setDropdownSelection(reminderDropdown, task.reminder)) {
                    reminderDropdown.setText(task.reminder, false); // fallback for custom
                }

                // For Repeat Dropdown
                if (!setDropdownSelection(repeatDropdown, task.repeat)) {
                    repeatDropdown.setText(task.repeat, false); // fallback for custom
                }

                if (note != null) {
                    ((TextInputEditText) findViewById(R.id.noteedittext)).setText(note.content);
                }

                if (location != null) {
                    selectedLat = location.latitude;
                    selectedLon = location.longitude;
                    savedLocationId = location.id;
                    isNewLocation = false;

                    MaterialAutoCompleteTextView locationEdit = findViewById(R.id.locationedittext);
                    TextInputEditText saveLocationEdit = findViewById(R.id.savelocationedittext);

                    locationEdit.setText(location.label);
                    locationEdit.setFocusable(true);
                    locationEdit.setFocusableInTouchMode(true);
                    locationEdit.setClickable(true);
                    saveLocationEdit.setText(location.label);
                    saveLocationEdit.setVisibility(View.VISIBLE);
                    locationEdit.setEnabled(true);
                }

                // Hide and clear manuallabelinput
                manualLabelInput.setText("");  // Clear any text
                manualLabelInput.setVisibility(View.GONE);  // Hide the input field
            });
        }).start();
    }


    private void highlightPriority() {
        for (Button b : priorityButtons) {
            boolean isSelected = b.getText().toString().equalsIgnoreCase(selectedPriority);
            b.setSelected(isSelected); // Trigger selector drawable
            b.setTextColor(isSelected ? ContextCompat.getColor(this, R.color.buttontextcolor) : ContextCompat.getColor(this, R.color.buttontextcolor));
        }
    }

    public void onBackPressed(){
        super.onBackPressed();
    }

}
