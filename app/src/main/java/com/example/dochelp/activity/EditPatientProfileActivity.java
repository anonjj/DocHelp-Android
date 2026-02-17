package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.app.DatePickerDialog; // --- NEW ---
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable; // --- NEW ---
import android.text.TextWatcher; // --- NEW ---
import android.view.View; // --- NEW ---
import android.widget.ArrayAdapter; // --- NEW ---
import android.widget.AutoCompleteTextView; // --- NEW ---
import android.widget.Button; // --- NEW ---
import android.widget.FrameLayout; // --- NEW ---
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar; // --- NEW ---
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class EditPatientProfileActivity extends AppCompatActivity {

    // --- MODIFIED: Changed etGender/etBloodGroup to AutoCompleteTextView ---
    private TextInputEditText etName, etPhone, etDob, etHeight, etWeight,
            etEmergencyName, etEmergencyPhone;
    private AutoCompleteTextView actvGender, actvBloodGroup;
    private FrameLayout loadingOverlay;
    private DocumentReference userRef;

    private static final int PICK_IMAGE_REQUEST = 1001;
    private ImageView imgProfile;
    private String base64Image = "";
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_patient_profile);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "User not signed in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userRef = FirebaseFirestore.getInstance().collection("users").document(uid);

        // --- Initialize Views ---
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etDob = findViewById(R.id.etDob);
        etHeight = findViewById(R.id.etHeight);
        etWeight = findViewById(R.id.etWeight);
        etEmergencyName = findViewById(R.id.etEmergencyName);
        etEmergencyPhone = findViewById(R.id.etEmergencyPhone);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        // --- MODIFIED: Find AutoCompleteTextViews ---
        actvGender = findViewById(R.id.actvGender);
        actvBloodGroup = findViewById(R.id.actvBloodGroup);

        MaterialButton btnSaveChanges = findViewById(R.id.btnSaveChanges);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        // --- Toolbar Navigation ---
        toolbar.setNavigationOnClickListener(v -> finish());

        // --- Load existing data ---
        loadUserData();

        // --- NEW: Initialize Gender Dropdown ---
        String[] genders = {"Male", "Female", "Other", "Prefer not to say"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                genders);
        actvGender.setAdapter(genderAdapter);

        // --- NEW: Initialize Blood Group Dropdown ---
        String[] bloodGroups = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        ArrayAdapter<String> bloodGroupAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                bloodGroups);
        actvBloodGroup.setAdapter(bloodGroupAdapter);

        // --- NEW: Date of Birth Picker ---
        etDob.setFocusable(false); // Make it not editable by keyboard
        etDob.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String date = String.format("%02d/%02d/%04d",
                                selectedDay, selectedMonth + 1, selectedYear);
                        etDob.setText(date);
                    },
                    year, month, day
            );

            // Set maximum date to today (can't be born in future)
            datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());

            // Set minimum date to 120 years ago
            calendar.add(Calendar.YEAR, -120);
            datePickerDialog.getDatePicker().setMinDate(calendar.getTimeInMillis());

            datePickerDialog.show();
        });

        // --- NEW: Phone Number Validation (10 digits) ---
        etPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 10) {
                    s.delete(10, s.length());
                }
            }
        });

        // --- NEW: Emergency Phone Number Validation (10 digits) ---
        etEmergencyPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 10) {
                    s.delete(10, s.length());
                }
            }
        });

        // --- NEW: Height Validation (40-250 cm) ---
        etHeight.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    try {
                        double height = Double.parseDouble(s.toString());
                        if (height > 250) {
                            s.replace(0, s.length(), "250");
                        }
                    } catch (NumberFormatException e) { /* ignore */ }
                }
            }
        });

        // --- NEW: Weight Validation (1-500 kg) ---
        etWeight.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    try {
                        double weight = Double.parseDouble(s.toString());
                        if (weight > 500) {
                            s.replace(0, s.length(), "500");
                        }
                    } catch (NumberFormatException e) { /* ignore */ }
                }
            }
        });


        // --- MODIFIED: Save button listener with validation ---
        btnSaveChanges.setOnClickListener(v -> {
            if (validateInput()) {
                saveUserData();
            }
        });

        imgProfile = findViewById(R.id.imgProfile);
        Button btnUploadPhoto = findViewById(R.id.btnUploadPhoto);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnUploadPhoto.setOnClickListener(v -> openFileChooser());

// Load existing profile image if it exists
        loadProfilePhoto();
    }

    private void loadUserData() {
        userRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                etName.setText(doc.getString("name"));
                etPhone.setText(doc.getString("phone"));
                etDob.setText(doc.getString("dob"));
                etHeight.setText(doc.getString("height"));
                etWeight.setText(doc.getString("weight"));
                etEmergencyName.setText(doc.getString("emergencyName"));
                etEmergencyPhone.setText(doc.getString("emergencyPhone"));

                // --- MODIFIED: Set text for AutoCompleteTextViews ---
                // We use setText(value, false) to set the text without triggering the filter
                actvGender.setText(doc.getString("gender"), false);
                actvBloodGroup.setText(doc.getString("bloodGroup"), false);

            } else {
                Toast.makeText(this, "No profile data found.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    // --- NEW: Validation logic moved to its own method ---
    private boolean validateInput() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String dob = etDob.getText().toString().trim();
        String gender = actvGender.getText().toString().trim();
        String bloodGroup = actvBloodGroup.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Name is required");
            etName.requestFocus();
            return false;
        }

        if (phone.isEmpty() || phone.length() != 10) {
            etPhone.setError("Valid 10-digit phone number required");
            etPhone.requestFocus();
            return false;
        }

        if (dob.isEmpty()) {
            etDob.setError("Date of birth is required");
            etDob.requestFocus();
            return false;
        }

        if (gender.isEmpty()) {
            actvGender.setError("Gender is required");
            actvGender.requestFocus();
            return false;
        }

        if (bloodGroup.isEmpty()) {
            actvBloodGroup.setError("Blood group is required");
            actvBloodGroup.requestFocus();
            return false;
        }

        return true; // All good
    }

    private void saveUserData() {
        // --- NEW: Show loading overlay ---
        loadingOverlay.setVisibility(View.VISIBLE);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", Objects.requireNonNull(etName.getText()).toString().trim());
        updates.put("phone", Objects.requireNonNull(etPhone.getText()).toString().trim());
        updates.put("dob", Objects.requireNonNull(etDob.getText()).toString().trim());
        updates.put("height", Objects.requireNonNull(etHeight.getText()).toString().trim());
        updates.put("weight", Objects.requireNonNull(etWeight.getText()).toString().trim());
        updates.put("emergencyName", Objects.requireNonNull(etEmergencyName.getText()).toString().trim());
        updates.put("emergencyPhone", Objects.requireNonNull(etEmergencyPhone.getText()).toString().trim());

        // --- MODIFIED: Get text from AutoCompleteTextViews ---
        updates.put("gender", Objects.requireNonNull(actvGender.getText()).toString().trim());
        updates.put("bloodGroup", Objects.requireNonNull(actvBloodGroup.getText()).toString().trim());

        userRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    loadingOverlay.setVisibility(View.GONE); // --- NEW ---
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    finish(); // Close the activity after saving
                })
                .addOnFailureListener(e -> {
                    loadingOverlay.setVisibility(View.GONE); // --- NEW ---
                    Toast.makeText(this, "Error updating profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                imgProfile.setImageBitmap(bitmap);

                // Compress + encode
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 30, baos);
                byte[] imageBytes = baos.toByteArray();
                base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

                // Save to Firestore
                saveProfilePhoto();

            } catch (IOException e) {
                Toast.makeText(this, "Error reading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveProfilePhoto() {
        String uid = auth.getUid();
        if (uid == null) return;

        db.collection("users").document(uid)
                .update("profilePhoto", base64Image)
                .addOnSuccessListener(a -> Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadProfilePhoto() {
        String uid = auth.getUid();
        if (uid == null) return;

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.contains("profilePhoto")) {
                        String encoded = doc.getString("profilePhoto");
                        if (encoded != null && !encoded.isEmpty()) {
                            byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                            imgProfile.setImageBitmap(bitmap);
                        }
                    }
                });
    }

}