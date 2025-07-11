package com.example.smartto_do_list;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FeedbackActivity extends AppCompatActivity {

    private TextInputLayout nameTextInputLayout, emailTextInputLayout, feedbackTextInputLayout;
    private TextInputEditText nameTextInputEditText, emailTextInputEditText, feedbackTextInputEditText;
    private RatingBar ratingStar;
    private MaterialButton attachScreenshotButton, sendFeedbackButton;
    private ImageButton backIconButton;
    private TextView ratingTextLabel;
    private LinearLayout fileListContainer;

    private List<Uri> attachedScreenshotUris = new ArrayList<>();

    private static final String PREFS_NAME = "feedback_prefs";
    private static final String KEY_FEEDBACK_SENT = "feedback_sent";
    private static final int PICK_IMAGE_REQUEST = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        initViews();
        setupListeners();
        backIconButton.setOnClickListener(v -> onBackPressed());
    }

    private void initViews() {
        backIconButton = findViewById(R.id.backiconbutton);
        nameTextInputLayout = findViewById(R.id.nametextinputlayout);
        nameTextInputEditText = findViewById(R.id.nametextinputedittext);
        emailTextInputLayout = findViewById(R.id.emailtextinputlayout);
        emailTextInputEditText = findViewById(R.id.emailtextinputedittext);
        feedbackTextInputLayout = findViewById(R.id.feedbacktextinputlayout);
        feedbackTextInputEditText = findViewById(R.id.feedbacktextinputedittext);
        ratingStar = findViewById(R.id.ratingstar);
        ratingTextLabel = findViewById(R.id.ratingTextLabel);
        attachScreenshotButton = findViewById(R.id.attachscreenshotbutton);
        sendFeedbackButton = findViewById(R.id.sendfeedbackbutton);
        fileListContainer = findViewById(R.id.fileListContainer);
    }

    private void setupListeners() {
        feedbackTextInputEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) feedbackTextInputLayout.setError(null);
        });

        setupEmailValidation();
        setupRatingListener();

        sendFeedbackButton.setOnClickListener(v -> submitFeedback());

        attachScreenshotButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Enable multiple selection
            startActivityForResult(Intent.createChooser(intent, "Select Screenshot"), PICK_IMAGE_REQUEST);
        });

        backIconButton.setOnClickListener(v -> finish());
    }

    private void setupEmailValidation() {
        final Handler handler = new Handler();
        final int DELAY_MS = 600;

        final Runnable emailValidationRunnable = () -> {
            String email = emailTextInputEditText.getText().toString().trim();
            if (!email.isEmpty() && !isEmailValid(email)) {
                emailTextInputLayout.setError("Invalid email format");
            } else {
                emailTextInputLayout.setError(null);
            }
        };

        emailTextInputEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                handler.removeCallbacks(emailValidationRunnable);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(emailValidationRunnable);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                handler.postDelayed(emailValidationRunnable, DELAY_MS);
            }
        });
    }

    private boolean isEmailValid(String email) {
        String emailRegex = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$";
        return email != null && email.matches("(?i)" + emailRegex);  // case-insensitive
    }

    private void setupRatingListener() {
        ratingStar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            if (ratingTextLabel.getVisibility() != TextView.VISIBLE) {
                ratingTextLabel.setVisibility(TextView.VISIBLE);
            }

            String message;
            switch ((int) rating) {
                case 5: message = "Amazing! Thank you!"; break;
                case 4: message = "Great! We appreciate it."; break;
                case 3: message = "Thanks! We'll keep improving."; break;
                case 2: message = "Sorry it wasn't great."; break;
                case 1: message = "We’d love to hear what went wrong."; break;
                default: message = ""; break;
            }
            ratingTextLabel.setText(message);
        });
    }

    private void submitFeedback() {
        String name = getText(nameTextInputEditText);
        String email = getText(emailTextInputEditText);
        String feedback = getText(feedbackTextInputEditText);
        int rating = (int) ratingStar.getRating();

        if (feedback.isEmpty()) {
            feedbackTextInputLayout.setError("Feedback is required");
            feedbackTextInputEditText.requestFocus();
            return;
        }

        if (!email.isEmpty() && !isEmailValid(email)) {
            emailTextInputLayout.setError("Enter a valid email");
            emailTextInputEditText.requestFocus();
            return;
        }

        String recipient = "rockr8379@gmail.com"; // Replace with your actual email
        String subject = "App Feedback from " + (name.isEmpty() ? "Anonymous" : name);
        String body = "Name: " + name + "\n"
                + "Email: " + email + "\n"
                + "Rating: " + rating + " star" + (rating == 1 ? "" : "s") + "\n\n"
                + "Feedback:\n" + feedback;

        Intent intent;
        if (attachedScreenshotUris.size() > 1) {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(attachedScreenshotUris));
        } else {
            intent = new Intent(Intent.ACTION_SEND);
            if (!attachedScreenshotUris.isEmpty()) {
                intent.putExtra(Intent.EXTRA_STREAM, attachedScreenshotUris.get(0));
            }
        }

        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(intent, "Send Feedback via"));

            // Save flag to SharedPreferences indicating user opened feedback intent
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_FEEDBACK_SENT, true)
                    .apply();

        } else {
            Toast.makeText(this, "No app found to send feedback", Toast.LENGTH_SHORT).show();
        }
    }
    private void showThankYouDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Thank You!")
                .setMessage("Your feedback has been submitted successfully.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    private String getText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
            if (data == null) return;

            try {
                // Do NOT clear existing attachments here to allow incremental adding

                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count && attachedScreenshotUris.size() < 3; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        Uri compressedUri = resizeAndCompressImage(uri);
                        if (compressedUri != null) {
                            attachedScreenshotUris.add(compressedUri);
                            addFileNameToView(compressedUri);
                        }
                    }
                    if (count + attachedScreenshotUris.size() > 3) {
                        Toast.makeText(this, "You can attach up to 3 images only.", Toast.LENGTH_SHORT).show();
                    }
                } else if (data.getData() != null) {
                    if (attachedScreenshotUris.size() >= 3) {
                        Toast.makeText(this, "You can attach up to 3 images only.", Toast.LENGTH_SHORT).show();
                    } else {
                        Uri uri = data.getData();
                        Uri compressedUri = resizeAndCompressImage(uri);
                        if (compressedUri != null) {
                            attachedScreenshotUris.add(compressedUri);
                            addFileNameToView(compressedUri);
                        }
                    }
                }

                Toast.makeText(this, "Image(s) attached", Toast.LENGTH_SHORT).show();

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Image processing failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Uri resizeAndCompressImage(Uri uri) throws IOException {
        Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);

        // Resize if too big
        int maxWidth = 1080;
        float ratio = (float) originalBitmap.getHeight() / originalBitmap.getWidth();
        if (originalBitmap.getWidth() > maxWidth) {
            int targetHeight = (int) (maxWidth * ratio);
            originalBitmap = Bitmap.createScaledBitmap(originalBitmap, maxWidth, targetHeight, true);
        }

        // Compress
        File file = new File(getCacheDir(), "compressed_" + System.currentTimeMillis() + ".jpg");
        FileOutputStream out = new FileOutputStream(file);
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, 60, out);
        out.close();

        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
    }

    private void addFileNameToView(Uri fileUri) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.item_attached_image, fileListContainer, false);

        TextInputLayout textInputLayout = (TextInputLayout) view;
        TextInputEditText editText = view.findViewById(R.id.imageNameEditText);

        editText.setText(fileUri.getLastPathSegment());

        // Make editText non-editable but focusable to show clear icon on focus
        editText.setKeyListener(null);   // disables typing
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setClickable(true);

        // Handle clear icon click
        textInputLayout.setEndIconOnClickListener(v -> {
            editText.setText("");
            editText.clearFocus();

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

            fileListContainer.removeView(view);
            attachedScreenshotUris.remove(fileUri);
        });

        fileListContainer.addView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean feedbackSent = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_FEEDBACK_SENT, false);

        if (feedbackSent) {
            // Show thank you dialog once
            showThankYouDialog();

            // Clear the flag after showing dialog
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_FEEDBACK_SENT, false)
                    .apply();
        }
    }

    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        Intent intent = new Intent(FeedbackActivity.this, MainActivity.class);
        intent.putExtra("open_drawer", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();

    }

}
