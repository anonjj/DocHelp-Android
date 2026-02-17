package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dochelp.utils.KeyboardUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignInActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private View btnSignIn, tvCreateAccount, progress;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in); // make sure this is your sign-in layout

        // Views (expect these IDs in your XML)
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvCreateAccount = findViewById(R.id.tvCreateAccount);
        progress = findViewById(R.id.progress); // ProgressBar with visibility="gone" in XML

        // Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Create account → go to Patient registration by default
        tvCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterPatientActivity.class)));

        // Sign in
        btnSignIn.setOnClickListener(v -> doSignIn());

    }

    private void doSignIn() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { etEmail.setError("Invalid email"); etEmail.requestFocus(); return; }
        if (pass.length() < 6) { etPassword.setError("Min 6 chars"); etPassword.requestFocus(); return; }

        KeyboardUtils.hideKeyboard(this);
        setLoading(true);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    String uid = res.getUser().getUid();

                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    // If you created the user in the Auth console, no profile exists yet -> create a default one.
                                    Map<String,Object> p = new HashMap<>();
                                    p.put("role", "patient"); // default; change to "doctor" if you know this user is a doctor
                                    p.put("email", res.getUser().getEmail());
                                    p.put("name", res.getUser().getDisplayName());
                                    p.put("createdAt", FieldValue.serverTimestamp());

                                    db.collection("users").document(uid).set(p)
                                            .addOnSuccessListener(v -> routeToDashboard("patient"))
                                            .addOnFailureListener(e -> {
                                                setLoading(false);
                                                Toast.makeText(this, "Create profile failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            });
                                } else {
                                    String role = doc.getString("role");
                                    setLoading(false);
                                    routeToDashboard(role);
                                }
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void routeToDashboard(String role){
        Intent i = "doctor".equalsIgnoreCase(role)
                ? new Intent(this, DoctorDashboardActivity.class)
                : new Intent(this, PatientDashboardActivity.class);
        startActivity(i);
        finishAffinity();
    }

    private void setLoading(boolean on){
        if (progress != null) progress.setVisibility(on ? View.VISIBLE : View.GONE);
        btnSignIn.setEnabled(!on);
        if (tvCreateAccount != null) tvCreateAccount.setEnabled(!on);
    }


    private void routeByRole(DocumentSnapshot doc) {
        setLoading(false);
        if (!doc.exists()) {
            Toast.makeText(this, "User profile not found", Toast.LENGTH_LONG).show();
            return;
        }
        routeToDashboard(doc.getString("role"));
    }

    // Auto-skip sign-in if already logged in
    @Override protected void onStart() {
        super.onStart();
        FirebaseUser u = auth.getCurrentUser();
        if (u != null) {
            db.collection("users").document(u.getUid()).get()
                    .addOnSuccessListener(this::routeByRole)
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to restore session: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
        }
    }

}
