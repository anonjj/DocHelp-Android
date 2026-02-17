package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar; // For the loading indicator
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dochelp.utils.KeyboardUtils;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterDoctorActivity extends AppCompatActivity {

    private static final int REQ_PICK_DOC = 201;
    private TextInputEditText etName, etEmail, etPassword, etRegNo, etAuthority;
    private AutoCompleteTextView etSpecialty;
    private TextView tvFileName;
    private Uri picked;
    private ProgressBar loadingBar; // Reference to a ProgressBar in your layout
    private View btnRegisterDoctor;
    private View btnUploadId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_doctor);

        // --- Find all your views (Cleaned up the duplicates) ---
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etRegNo = findViewById(R.id.etRegNo);
        etAuthority = findViewById(R.id.etAuthority);
        etSpecialty = findViewById(R.id.etSpecialty); // MaterialAutoCompleteTextView
        tvFileName = findViewById(R.id.tvFileName);
        loadingBar = findViewById(R.id.loadingBar); // ProgressBar

        // --- Set up your click listeners ---
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnUploadId = findViewById(R.id.btnUploadId);
        btnUploadId.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/jpeg", "image/png"});
            startActivityForResult(i, REQ_PICK_DOC);
        });

        //  Use isFormValid() to gate the robust onSubmitDoctor()
        btnRegisterDoctor = findViewById(R.id.btnRegisterDoctor);
        btnRegisterDoctor.setOnClickListener(v -> {
            if (isFormValid()) {
                KeyboardUtils.hideKeyboard(this);
                onSubmitDoctor(); // This is the single source of truth for submission
            }
        });

        // --- Specialty Adapter Setup (Keep this clean) ---
        String[] specialties = {"General Medicine", "Cardiology", "Neurology", "Pediatrics", "Orthopedics", "Dermatology", "Psychiatry", "Radiology", "Surgery", "Ophthalmology"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, specialties);
        ((AutoCompleteTextView) findViewById(R.id.etSpecialty)).setAdapter(adapter);
    }

    private boolean isFormValid() {
        // --- Get the text values once ---
        String name = val(etName);
        String email = val(etEmail);
        String password = val(etPassword);
        String regNo = val(etRegNo);
        String authority = val(etAuthority);
        String specialty = val(etSpecialty);

        // --- 1. Name Validation ---
        if (name.isEmpty()) {
            etName.setError("Required");
            etName.requestFocus();
            return false;
        }
        if (!name.matches("^[\\p{L} .'-]+$")) {
            etName.setError("Please enter a valid name (letters and '.- only)");
            etName.requestFocus();
            return false;
        }

        // --- 2. Email Validation ---
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Invalid email format");
            etEmail.requestFocus();
            return false;
        }

        // --- 3. Password Validation ---
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }

        // --- 4. License/Registration No. Validation ---
        if (regNo.isEmpty()) {
            etRegNo.setError("Required");
            etRegNo.requestFocus();
            return false;
        }
        // NEW: Regex check for format (allows letters, numbers, and hyphens)
        if (!regNo.matches("^[a-zA-Z0-9-]+$")) {
            etRegNo.setError("Invalid format (only A-Z, 0-9, and - allowed)");
            etRegNo.requestFocus();
            return false;
        }
        // We can keep the min length check, it's still good practice
        if (regNo.length() < 5) {
            etRegNo.setError("License number seems too short");
            etRegNo.requestFocus();
            return false;
        }


        // --- 5. Authority Validation ---
        if (authority.isEmpty()) {
            etAuthority.setError("Required");
            etAuthority.requestFocus();
            return false;
        }

        // --- 6. Specialty Validation ---
        if (specialty.isEmpty()) {
            etSpecialty.setError("Required");
            etSpecialty.requestFocus();
            return false;
        }

        // --- 7. File Validation ---
        if (picked == null) {
            Toast.makeText(this, "Please upload your ID/Diploma", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true; // All checks passed!
    }

    private void onSubmitDoctor() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();
        String regNo = etRegNo.getText().toString().trim();
        String authority = etAuthority.getText().toString().trim();   // if you have this field
        String specialty = etSpecialty.getText().toString().trim();   // if you have this field
        setLoading(true);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    String uid = r.getUser().getUid();

                    r.getUser().updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build());

                    Map<String, Object> d = new HashMap<>();
                    d.put("role", "doctor");
                    d.put("name", name);
                    d.put("email", email);
                    d.put("regNo", regNo);
                    d.put("authority", authority);
                    d.put("specialty", specialty);
                    d.put("verified", false);
                    d.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("users").document(uid).set(d)
                            .addOnSuccessListener(x -> {
                                setLoading(false);
                                Toast.makeText(this, "Doctor registered (pending verification).", Toast.LENGTH_LONG).show();
                                startActivity(new Intent(this, DoctorDashboardActivity.class));
                                finishAffinity();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Save profile failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });

    }

    private void setLoading(boolean on) {
        if (loadingBar != null) {
            loadingBar.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        if (btnRegisterDoctor != null) btnRegisterDoctor.setEnabled(!on);
        if (btnUploadId != null) btnUploadId.setEnabled(!on);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_DOC && resultCode == RESULT_OK && data != null && data.getData() != null) {
            picked = data.getData();
            // You might want a better way to get a displayable name, but this is a start
            tvFileName.setText("File selected!");
        }
    }

    // --- HELPER METHODS ---

    private void showLoading(boolean isLoading) {
        if (loadingBar != null) {
            loadingBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    private String val(TextView e) { // Use the common ancestor, TextView
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }
}
