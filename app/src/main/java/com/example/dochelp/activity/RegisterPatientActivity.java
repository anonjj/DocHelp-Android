package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.dochelp.utils.KeyboardUtils;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterPatientActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword, etDob, etAllergies, etMeds;
    private TextInputEditText etPhone, etHeight, etWeight; // <-- NEW
    private TextInputEditText etEmergencyName, etEmergencyPhone; // <-- NEW

    private AutoCompleteTextView etBlood, actvGender; // <-- NEW: actvGender
    private CheckBox cbTerms, cbMarketing;
    private View btnRegisterPatient;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_patient);

        // Link to Sign In screen (optional but recommended for UX)
        findViewById(R.id.tvSignInLink)
                .setOnClickListener(v -> finish());

        // Doctor link (already there)
        findViewById(R.id.btnHelpDoctor)
                .setOnClickListener(v -> startActivity(new Intent(this, RegisterDoctorActivity.class)));

        // --- VIEW INITIALIZATION ---
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone); // <-- NEW
        etPassword = findViewById(R.id.etPassword);
        etDob = findViewById(R.id.etDob);
        actvGender = findViewById(R.id.actvGender); // <-- NEW
        etBlood = findViewById(R.id.etBlood);
        etHeight = findViewById(R.id.etHeight); // <-- NEW
        etWeight = findViewById(R.id.etWeight); // <-- NEW
        etAllergies = findViewById(R.id.etAllergies);
        etMeds = findViewById(R.id.etMeds);
        etEmergencyName = findViewById(R.id.etEmergencyName); // <-- NEW
        etEmergencyPhone = findViewById(R.id.etEmergencyPhone); // <-- NEW
        cbTerms = findViewById(R.id.cbTerms);
        cbMarketing = findViewById(R.id.cbMarketing); // <-- NEW

        // --- DROPDOWN ADAPTER SETUP ---
        // (Assuming you added the BLOOD_GROUPS and GENDERS arrays from the previous answer)

        ArrayAdapter<String> bloodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, BLOOD_GROUPS);
        etBlood.setAdapter(bloodAdapter);

        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, GENDERS);
        actvGender.setAdapter(genderAdapter); // Use the new Gender field

        // --- LISTENERS ---
        btnRegisterPatient = findViewById(R.id.btnRegisterPatient);
        btnRegisterPatient.setOnClickListener(v -> {
            KeyboardUtils.hideKeyboard(this);
            submit();
        });
        findViewById(R.id.etDob).setOnClickListener(v -> showDatePicker()); // <-- NEW
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date of Birth")
                .setSelection(MaterialDatePicker.thisMonthInUtcMilliseconds() - (long) (20 * 365.25 * 24 * 60 * 60 * 1000))
                .build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            // Convert the selected epoch time (Long) to a readable format (DD/MM/YYYY)
            // Note: The format "dd/MM/yyyy" is standard and simple to save.
            String dateString = android.text.format.DateFormat.format("dd/MM/yyyy", new java.util.Date(selection)).toString();

            etDob.setText(dateString);
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER_TAG");
    }

    private void submit() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = val(etPhone);
        String pass  = etPassword.getText().toString().trim();

        // Medical/Profile required fields
        String dob = val(etDob);
        String gender = actvGender.getText().toString().trim(); // Use new Gender field
        String blood = etBlood.getText().toString().trim();

        // Medical optional fields
        String height = val(etHeight);
        String weight = val(etWeight);
        String allergies = val(etAllergies);
        String meds = val(etMeds);

        // Emergency optional fields
        String eName = val(etEmergencyName);
        String ePhone = val(etEmergencyPhone);

        if (name.isEmpty()) { etName.setError("Required"); etName.requestFocus(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { etEmail.setError("Invalid"); etEmail.requestFocus(); return; }
        if (phone.length() != 10) { etPhone.setError("10 digits required"); etPhone.requestFocus(); return; } // Assuming +91 is handled by prefix
        if (pass.length() < 6) { etPassword.setError("Min 6 chars"); etPassword.requestFocus(); return; }

        if (dob.isEmpty()) { etDob.setError("Required"); etDob.requestFocus(); return; }
        if (gender.isEmpty()) { actvGender.setError("Required"); actvGender.requestFocus(); return; }
        if (blood.isEmpty()) { etBlood.setError("Required"); etBlood.requestFocus(); return; }

        // Terms check
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "You must accept the Terms of Service.", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    String uid = r.getUser().getUid();

                    // optional: set display name in Auth profile
                    r.getUser().updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build());

                    // write profile to Firestore
                    Map<String, Object> p = new HashMap<>();
                    p.put("role", "patient");
                    p.put("name", name);
                    p.put("email", email);
                    p.put("phone", phone);
                    p.put("dob", dob); // <-- NEW
                    p.put("gender", gender); // <-- NEW
                    p.put("bloodGroup", blood); // <-- NEW
                    p.put("height", height); // <-- NEW (Optional, may be empty)
                    p.put("weight", weight);
                    p.put("allergies", allergies);
                    p.put("medications", meds);
                    if (!eName.isEmpty()) p.put("emergencyName", eName);
                    if (!ePhone.isEmpty()) p.put("emergencyPhone", ePhone); // <-- NEW
                    p.put("marketingConsent", cbMarketing.isChecked()); // <-- NEW
                    p.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("users").document(uid).set(p)
                            .addOnSuccessListener(x -> {
                                setLoading(false);
                                startActivity(new Intent(this, PatientDashboardActivity.class));
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

    // small helper to show/hide a ProgressBar with id @+id/progress
    private void setLoading(boolean on){
        View p = findViewById(R.id.progress);
        if (p != null) p.setVisibility(on ? View.VISIBLE : View.GONE);
        if (btnRegisterPatient != null) btnRegisterPatient.setEnabled(!on);
    }

    private static final String[] BLOOD_GROUPS = {
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    };

    // Gender options
    private static final String[] GENDERS = {
            "Male", "Female", "Other", "Prefer not to say"
    };
    private void showLoading(boolean on){ findViewById(R.id.progress).setVisibility(on?View.VISIBLE: View.GONE); }

    private String val(TextInputEditText e){ return String.valueOf(e.getText()).trim(); }
}
