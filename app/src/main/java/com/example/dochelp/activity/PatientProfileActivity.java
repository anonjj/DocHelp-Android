package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.dochelp.adapter.SimpleListAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

public class PatientProfileActivity extends AppCompatActivity {

    private TextView tvPatientName, tvPatientEmail;
    private ListView lvMedicalFiles, lvDoctorNotes;
    private EditText etDoctorNotes;
    private Button btnSaveNotes;

    private List<String> summaries = new ArrayList<>();
    private List<String> doctorNotes = new ArrayList<>();
    private SimpleListAdapter summariesAdapter, notesAdapter;
    private FirebaseFirestore db;
    private String doctorId, patientId;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_profile);

        tvPatientName = findViewById(R.id.tvPatientName);
        tvPatientEmail = findViewById(R.id.tvPatientEmail);
        lvMedicalFiles = findViewById(R.id.lvMedicalFiles);
        lvDoctorNotes = findViewById(R.id.lvDoctorNotes);
        etDoctorNotes = findViewById(R.id.etDoctorNotes);
        btnSaveNotes = findViewById(R.id.btnSaveNotes);

        db = FirebaseFirestore.getInstance();
        doctorId = FirebaseAuth.getInstance().getUid();
        patientId = getIntent().getStringExtra("patientId");

        if (patientId == null) {
            Toast.makeText(this, "No patient ID found!", Toast.LENGTH_LONG).show();
            Log.e("PatientProfile", "Missing patientId in intent");
            finish();
            return;
        }

        Log.d("PatientProfile", "Received patientId = " + patientId);

        // Initialize adapters
        summariesAdapter = new SimpleListAdapter(this, summaries);
        lvMedicalFiles.setAdapter(summariesAdapter);

        notesAdapter = new SimpleListAdapter(this, doctorNotes);
        lvDoctorNotes.setAdapter(notesAdapter);

        loadPatientProfile();
        loadPatientSummaries();
        loadDoctorNotes();

        btnSaveNotes.setOnClickListener(v -> saveDoctorNote());
    }

    private void loadPatientProfile() {
        db.collection("users").document(patientId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Patient profile not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    tvPatientName.setText(doc.getString("name"));
                    tvPatientEmail.setText(doc.getString("email"));
                })
                .addOnFailureListener(e ->
                {
                    Log.e("PatientProfile", "Error loading patient profile", e);
                    Toast.makeText(this, "Failed to load patient profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void loadPatientSummaries() {
        db.collection("users").document(patientId)
                .collection("summaries")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Log.e("PatientProfile", "Error loading summaries", e);
                        return;
                    }
                    summaries.clear();
                    if (snap != null) {
                        for (DocumentSnapshot s : snap.getDocuments()) {
                            String summary = s.getString("summary");
                            if (summary != null && !summary.trim().isEmpty())
                                summaries.add(summary);
                        }
                    }
                    summariesAdapter.notifyDataSetChanged();
                });
    }

    private void loadDoctorNotes() {
        db.collection("doctors").document(doctorId)
                .collection("notes")
                .whereEqualTo("patientId", patientId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Log.e("PatientProfile", "Error loading doctor notes", e);
                        return;
                    }
                    doctorNotes.clear();
                    if (snap != null) {
                        for (DocumentSnapshot n : snap.getDocuments()) {
                            String note = n.getString("notes");
                            if (note != null && !note.trim().isEmpty())
                                doctorNotes.add(note);
                        }
                    }
                    notesAdapter.notifyDataSetChanged();
                });
    }

    private void saveDoctorNote() {
        String noteText = etDoctorNotes.getText().toString().trim();
        if (noteText.isEmpty()) {
            Toast.makeText(this, "Please enter a note.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("patientId", patientId);
        noteData.put("doctorId", doctorId);
        noteData.put("notes", noteText);
        noteData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("doctors").document(doctorId)
                .collection("notes")
                .add(noteData)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Note saved successfully!", Toast.LENGTH_SHORT).show();
                    etDoctorNotes.setText("");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save note: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
