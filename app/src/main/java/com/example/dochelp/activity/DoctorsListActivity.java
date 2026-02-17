package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DoctorsListActivity extends AppCompatActivity {

    private final List<Doctor> allDoctors = new ArrayList<>();
    private DoctorCardAdapter doctorAdapter;
    private ProgressBar progressDoctors;
    private TextView tvEmptyDoctors;

    static class Doctor {
        final String id;
        final String name;
        final String speciality;

        Doctor(String id, String name, String speciality) {
            this.id = id;
            this.name = (name == null ? "Doctor" : name);
            this.speciality = (speciality == null ? "" : speciality);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctors_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup RecyclerView
        RecyclerView rvAllDoctors = findViewById(R.id.rvAllDoctors);
        rvAllDoctors.setLayoutManager(new GridLayoutManager(this, 2)); // 2 columns
        doctorAdapter = new DoctorCardAdapter(allDoctors, this::showSlotPicker);
        rvAllDoctors.setAdapter(doctorAdapter);
        progressDoctors = findViewById(R.id.progressDoctors);
        tvEmptyDoctors = findViewById(R.id.tvEmptyDoctors);

        // Load data
        loadAllDoctors();
    }

    private void loadAllDoctors() {
        setLoading(true);
        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("role", "doctor")
                .get()
                .addOnSuccessListener(snap -> {
                    setLoading(false);
                    allDoctors.clear();
                    if (snap != null && !snap.isEmpty()) {
                        for (DocumentSnapshot ds : snap.getDocuments()) {
                            allDoctors.add(new Doctor(
                                    ds.getId(),
                                    ds.getString("name"),
                                    ds.getString("specialty")
                            ));
                        }
                    }
                    doctorAdapter.notifyDataSetChanged();
                    updateEmptyState();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    updateEmptyState();
                    Toast.makeText(this, "Failed to load doctors: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean on) {
        if (progressDoctors != null) progressDoctors.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState() {
        if (tvEmptyDoctors != null) {
            tvEmptyDoctors.setVisibility(allDoctors.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showSlotPicker(Doctor d) {
        String doctorId = d.id;
        FirebaseFirestore.getInstance()
                .collection("users").document(doctorId).collection("availability")
                .whereEqualTo("active", true)
                .whereGreaterThan("start", new Timestamp(new Date()))
                .orderBy("start")
                .limit(20)
                .get()
                .addOnSuccessListener(snap -> {
                    List<DocumentSnapshot> slots = snap.getDocuments();
                    if (slots.isEmpty()) {
                        Toast.makeText(this,"No available slots",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CharSequence[] labels = new CharSequence[slots.size()];
                    for (int i=0;i<slots.size();i++){
                        Timestamp s = slots.get(i).getTimestamp("start");
                        Timestamp e = slots.get(i).getTimestamp("end");
                        labels[i] = DateFormat.format("EEE, dd MMM • h:mm", s.toDate())
                                + " - " + DateFormat.format("h:mm a", e.toDate());
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Choose a slot for " + d.name)
                            .setItems(labels, (dlg, which) -> {
                                // You'll need the transaction logic here or in a helper class
                                Toast.makeText(this, "Booking slot...", Toast.LENGTH_SHORT).show();
                            }).show();
                });
    }

    static class DoctorCardAdapter extends RecyclerView.Adapter<DoctorCardAdapter.VH> {
        interface OnBook {
            void click(Doctor d);
        }

        private final List<Doctor> data;
        private final OnBook onBook;

        DoctorCardAdapter(List<Doctor> d, OnBook ob) {
            data = d;
            onBook = ob;
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView name, spec;
            View btn;

            VH(View v) {
                super(v);
                name = v.findViewById(R.id.tvDocName);
                spec = v.findViewById(R.id.tvSpec);
                btn = v.findViewById(R.id.btnBook);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_doctor_card, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            Doctor d = data.get(i);
            h.name.setText(d.name);
            h.spec.setText(d.speciality);
            h.btn.setOnClickListener(v -> onBook.click(d));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
