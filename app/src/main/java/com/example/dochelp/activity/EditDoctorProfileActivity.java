package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class EditDoctorProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditDoctorProfile";

    // Views for all profile fields
    // NOTE: If etSpec is an AutoCompleteTextView in XML, change the type here.
    private EditText etDocName, etSpec, etLicense, etExperience, etHospital, etConsultationFee, etPhone;
    private Button btnSaveProfile;

    private String doctorId;
    private DocumentReference doctorRef;

    private static final int PICK_IMAGE_REQUEST = 1001;
    private ImageView imgProfile;
    private String base64Image = "";
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_doctor_profile);

        // 1. Get Doctor ID and Firestore Reference
        doctorId = FirebaseAuth.getInstance().getUid();
        if (doctorId == null) {
            Toast.makeText(this, "Doctor ID not found. Please sign in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        doctorRef = FirebaseFirestore.getInstance().collection("users").document(doctorId);

        // 2. Initialize Views (IDs must match the XML)
        etDocName = findViewById(R.id.etDocName);
        etSpec = findViewById(R.id.actvSpecialization);
        etLicense = findViewById(R.id.etLicense);
        etExperience = findViewById(R.id.etExperience);
        etHospital = findViewById(R.id.etHospital);
        etConsultationFee = findViewById(R.id.etConsultationFee);
        etPhone = findViewById(R.id.etPhone);

        btnSaveProfile = findViewById(R.id.btnSaveProfile);

        // --- NEW FEATURE INTEGRATION ---

        // A. Specialization Dropdown Setup
        String[] specializations = {
                "Cardiologist", "Dermatologist", "Neurologist", "Orthopedic Surgeon",
                "Pediatrician", "Psychiatrist", "General Physician", "ENT Specialist",
                "Gynecologist", "Ophthalmologist", "Dentist", "Radiologist",
                "Urologist", "Pulmonologist", "Gastroenterologist", "Endocrinologist",
                "Oncologist", "Anesthesiologist"
        };

        // Assume etSpec is declared as an EditText but is an AutoCompleteTextView in XML
        // It's safer to use the actual ID you gave the AutoCompleteTextView in the XML
        // NOTE: Based on your XML structure, R.id.etSpec might not be the correct ID for the dropdown.
        // Assuming your dropdown is R.id.actvSpecialization as per previous conversion:
        AutoCompleteTextView actvSpecialization = (AutoCompleteTextView) etSpec; // Cast the EditText to ACTV

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                specializations
        );
        actvSpecialization.setAdapter(adapter);

        // B. License Number Formatting (MCI-XXXXX-YYYY)
        etLicense.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;

                isFormatting = true;
                // Remove all non-alphanumeric characters
                String input = s.toString().replaceAll("[^A-Z0-9]", "");

                // Ensure it starts with MCI (simulating official format)
                if (input.length() > 0 && !input.startsWith("MCI")) {
                    input = "MCI" + input;
                }
                if (input.length() > 3 && !input.substring(0, 3).equals("MCI")) {
                    input = "MCI" + input.substring(3);
                }


                StringBuilder formatted = new StringBuilder();
                int length = input.length();

                if (length > 0) {
                    formatted.append(input.substring(0, Math.min(3, length)));
                }
                if (length > 3) {
                    formatted.append("-").append(input.substring(3, Math.min(8, length)));
                }
                if (length > 8) {
                    formatted.append("-").append(input.substring(8, Math.min(12, length)));
                }

                // Limit total length (MCI-XXXXX-YYYY is 14 chars plus 2 hyphens = 16)
                if (formatted.length() > 16) {
                    formatted.setLength(16);
                }

                s.replace(0, s.length(), formatted.toString());
                isFormatting = false;
            }
        });

        // C. Phone number validation (10 digits)
        etPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Limit to 10 digits
                if (s.length() > 10) {
                    s.delete(10, s.length());
                    Toast.makeText(EditDoctorProfileActivity.this, "Phone number limited to 10 digits", Toast.LENGTH_SHORT).show();
                }
            }
        });


        // --- END NEW FEATURE INTEGRATION ---

        // 3. Load Current Data
        loadCurrentProfileData();

        // 4. Set up Save Button Listener
        btnSaveProfile.setOnClickListener(v -> saveProfileChanges());

        imgProfile = findViewById(R.id.imgProfile);
        Button btnUploadPhoto = findViewById(R.id.btnUploadPhoto);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnUploadPhoto.setOnClickListener(v -> openFileChooser());

// Load existing profile image if it exists
        loadProfilePhoto();
    }

    /**
     * Fetches the doctor's current profile data from Firestore and populates the EditTexts.
     * Note: This function now populates the etSpec which is cast as an AutoCompleteTextView.
     */
    private void loadCurrentProfileData() {
        doctorRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // ... (Name, License, Hospital, Fee, Phone logic is fine)
                        etDocName.setText(documentSnapshot.getString("name"));

                        // Set specialization into the AutoCompleteTextView
                        ((AutoCompleteTextView) etSpec).setText(documentSnapshot.getString("specialization"), false);

                        etLicense.setText(documentSnapshot.getString("licenseNumber"));

                        // Remove " Years" suffix before editing
                        String experience = documentSnapshot.getString("experience");
                        if (experience != null && experience.endsWith(" Years")) {
                            experience = experience.replace(" Years", "");
                        }
                        etExperience.setText(experience);

                        etHospital.setText(documentSnapshot.getString("hospitalName"));

                        // Remove "₹" prefix before editing
                        String fee = documentSnapshot.getString("consultationFee");
                        if (fee != null && fee.startsWith("₹")) {
                            fee = fee.substring(1);
                        }
                        etConsultationFee.setText(fee);

                        etPhone.setText(documentSnapshot.getString("phone"));
                        // Note: etEmail is often read-only or updated separately via Auth
                    } else {
                        Log.d(TAG, "No profile document found for ID: " + doctorId);
                        Toast.makeText(this, "Profile not found. Starting fresh.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading profile data: " + e.getMessage());
                    Toast.makeText(this, "Failed to load profile data.", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Collects data from EditTexts, validates it (basic), and pushes updates to Firestore.
     */
    private void saveProfileChanges() {
        // Collect data from the views
        String name = etDocName.getText().toString().trim();
        String specialization = etSpec.getText().toString().trim(); // Collects text from the ACTV/EditText
        String license = etLicense.getText().toString().trim();
        String experience = etExperience.getText().toString().trim();
        String hospital = etHospital.getText().toString().trim();
        String fee = etConsultationFee.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (name.isEmpty() || specialization.isEmpty() || license.isEmpty()) {
            Toast.makeText(this, "Name, Specialization, and License are required.", Toast.LENGTH_LONG).show();
            return;
        }

        // Basic validation for license format (optional, but good practice)
        if (!license.matches("MCI-[A-Z0-9]{5}-[A-Z0-9]{4}")) {
            Toast.makeText(this, "License must follow the format MCI-XXXXX-YYYY.", Toast.LENGTH_LONG).show();
            // You can comment out the return if you rely solely on the TextWatcher for formatting
            // return;
        }


        // 1. Prepare data for update
        Map<String, Object> profileUpdates = new HashMap<>();
        profileUpdates.put("name", name);
        profileUpdates.put("specialization", specialization);
        profileUpdates.put("licenseNumber", license);
        // Ensure " Years" and "₹" suffixes/prefixes are added back for consistent storage
        profileUpdates.put("experience", experience + " Years");
        profileUpdates.put("hospitalName", hospital);
        profileUpdates.put("consultationFee", "₹" + fee);
        profileUpdates.put("phone", phone);

        // 2. Perform the Firestore update
        btnSaveProfile.setEnabled(false); // Prevent double-tap
        doctorRef.update(profileUpdates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile Updated Successfully! ✨", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating profile: " + e.getMessage());
                    Toast.makeText(this, "Error saving profile. Try again.", Toast.LENGTH_LONG).show();
                    btnSaveProfile.setEnabled(true);
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