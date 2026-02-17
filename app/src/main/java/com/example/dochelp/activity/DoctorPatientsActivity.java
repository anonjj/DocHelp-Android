package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DoctorPatientsActivity extends AppCompatActivity {

    private ChipGroup chipsStatus;
    private Button btnDate;
    private Spinner spTags;
    private TextInputEditText etSearch;
    private RecyclerView rvPatients;
    private TextView tvEmptyPatients;

    private final List<PatientRow> rows = new ArrayList<>();
    private final List<PatientRow> filtered = new ArrayList<>();
    private RecyclerView.Adapter<VH> adapter;

    private ListenerRegistration reg;
    private Timestamp dateFrom = null, dateTo = null; // null = any
    private String tagFilter = "Any";
    private String statusFilter = "All";
    private String searchQuery = "";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String doctorId;


    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_doctor_patients);
        doctorId = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();

        chipsStatus = findViewById(R.id.chipsStatus);
        btnDate = findViewById(R.id.btnDate);
        spTags = findViewById(R.id.spTags);
        etSearch = findViewById(R.id.etSearch);
        rvPatients = findViewById(R.id.rvPatients);
        tvEmptyPatients = findViewById(R.id.tvEmptyPatients);

        // simple tag list for now; you can fetch from Firestore later
        ArrayAdapter<String> tagsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("Any","Cardiology","Dermatology","Neurology","Diabetes","Pediatrics","Orthopedics","Psychiatry"));
        spTags.setAdapter(tagsAdapter);

        rvPatients.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerView.Adapter<VH>() {
            @NonNull
            @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v){
                View row = LayoutInflater.from(p.getContext())
                        .inflate(R.layout.item_doctor_patient_row, p, false);
                return new VH(row);
            }
            @Override public void onBindViewHolder(@NonNull VH h, int i){
                if (i < 0 || i >= filtered.size()) return; // guard against async updates
                PatientRow r = filtered.get(i);

                h.tvName.setText(r.name != null ? r.name : r.patientId);
                h.tvLast.setText(r.lastLine);
                h.tvTags.setText(r.tagsLine);

                h.itemView.setOnClickListener(v -> {
                    // Put context in singleton (optional)
                    if (r.apptId != null) {
                        com.example.dochelp.session.AppointmentSession.get().set(r.apptId, r.patientId, doctorId);
                    }

                    // Also pass extras (nice fallback)
                    Intent intent = new Intent(v.getContext(), PatientProfileActivity.class);
                    intent.putExtra("patientId", r.patientId);
                    if (r.apptId != null) intent.putExtra("apptId", r.apptId);
                    v.getContext().startActivity(intent);

                });
            }
            @Override public int getItemCount(){ return filtered.size(); }
        };
        rvPatients.setAdapter(adapter);
        applyFilters();

        // listeners
        chipsStatus.setOnCheckedStateChangeListener((g, ids) -> {
            statusFilter = getStatusFromChips();
            applyFilters();
        });
        spTags.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                tagFilter = (String) parent.getItemAtPosition(position);
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        btnDate.setOnClickListener(v -> pickDateRange());
        etSearch.addTextChangedListener(new SimpleTextWatcher(s -> {
            searchQuery = s.toLowerCase(Locale.US).trim();
            applyFilters();
        }));

    }

    @Override
    protected void onStart() {
        super.onStart();

        // Step 1: build your Firestore query
        Query q = db.collection("appointments")
                .whereEqualTo("doctorId", doctorId);
        // optional: you can filter client-side instead of using whereNotEqualTo

        // Step 2: start realtime listener
        reg = q.addSnapshotListener((snap, e) -> {
            if (e != null) {
                Log.e("Appointments", "Listen failed", e);
                Toast.makeText(this, "Failed to load patients: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            if (snap == null || snap.isEmpty()) {
                rows.clear();
                applyFilters();
                Log.d("Appointments", "No appointments found for doctor.");
                return;
            }

            // Step 3: collect appointments one by one (each will be a row)
            List<PatientRow> tempRows = new ArrayList<>();

            for (DocumentSnapshot d : snap.getDocuments()) {
                String patientId = d.getString("patientId");
                if (patientId == null) continue;

                PatientRow row = new PatientRow(patientId);
                row.lastTs = d.getTimestamp("slot");
                row.lastStatus = d.getString("status");
                row.apptId = d.getId();
                row.tags = (List<String>) d.get("tags");
                tempRows.add(row);

                Log.d("Appointments", "Fetched appointment: " + row.patientId + " status=" + row.lastStatus);
            }

            // Step 4: update the rows list and refresh adapter
            rows.clear();
            rows.addAll(tempRows);
            adapter.notifyDataSetChanged();

            // Step 5: fetch names in parallel
            for (PatientRow r : rows) {
                db.collection("users").document(r.patientId).get()
                        .addOnSuccessListener(u -> {
                            if (u.exists()) {
                                r.name = u.getString("name");
                                applyFilters(); // update name in UI
                            }
                        })
                        .addOnFailureListener(err -> {
                            Log.e("Appointments", "Name fetch failed for " + r.patientId, err);
                            Toast.makeText(this, "Failed to load patient name", Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }


    @Override protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }

    // ----- Helpers -----

    private void applyFilters() {
        filtered.clear();
        for (PatientRow r : rows) {
            if (!statusMatch(r)) continue;
            if (!dateMatch(r)) continue;
            if (!tagMatch(r)) continue;
            if (!nameMatch(r)) continue;

            // format lines
            String when = (r.lastTs != null)
                    ? android.text.format.DateFormat.format("EEE, dd MMM • h:mm a",
                    new java.util.Date(r.lastTs.getSeconds() * 1000L)).toString()
                    : "-";
            String stat = r.lastStatus != null ? cap(r.lastStatus) : "-";
            r.lastLine = "Last: " + when + " (" + stat + ")";
            r.tagsLine = "Tags: " + ((r.tags == null || r.tags.isEmpty()) ? "-" : String.join(", ", r.tags));

            filtered.add(r);
        }
        adapter.notifyDataSetChanged();
        if (tvEmptyPatients != null) {
            tvEmptyPatients.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean statusMatch(PatientRow r) {
        if ("All".equals(statusFilter)) return true;
        return statusFilter.equalsIgnoreCase(r.lastStatus);
    }

    private boolean dateMatch(PatientRow r) {
        if (r.lastTs == null) return true;
        if (dateFrom != null && r.lastTs.compareTo(dateFrom) < 0) return false;
        if (dateTo != null && r.lastTs.compareTo(dateTo) > 0) return false;
        return true;
    }

    private boolean tagMatch(PatientRow r) {
        if ("Any".equals(tagFilter)) return true;
        if (r.tags == null) return false;
        for (String t : r.tags) if (tagFilter.equalsIgnoreCase(t)) return true;
        return false;
    }

    private boolean nameMatch(PatientRow r) {
        if (searchQuery.isEmpty()) return true;
        String n = r.name != null ? r.name.toLowerCase(Locale.US) : "";
        return n.contains(searchQuery);
    }

    private String getStatusFromChips(){
        int id = chipsStatus.getCheckedChipId();
        if (id == R.id.chipPending) return "pending";
        if (id == R.id.chipConfirmed) return "confirmed";
        if (id == R.id.chipCompleted) return "completed";
        return "All";
    }

    private void pickDateRange(){
        // very simple “from today to +30d” demo; swap to MaterialDatePicker.Range if you like
        // For now, toggle between "Any" and "Next 30 days" for brevity
        if (dateFrom == null && dateTo == null) {
            Calendar c = Calendar.getInstance();
            dateFrom = new Timestamp(new Date());
            c.add(Calendar.DAY_OF_YEAR, 30);
            dateTo = new Timestamp(new Date(c.getTimeInMillis()));
            btnDate.setText("Date: Next 30d");
        } else {
            dateFrom = dateTo = null;
            btnDate.setText("Date: Any");
        }
        applyFilters();
    }

    private static String cap(String s){ return (s==null||s.isEmpty())? "" : Character.toUpperCase(s.charAt(0))+s.substring(1); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvLast, tvTags;
        VH(@NonNull View v){ super(v);
            tvName=v.findViewById(R.id.tvName);
            tvLast=v.findViewById(R.id.tvLast);
            tvTags=v.findViewById(R.id.tvTags);
        }
    }


    // row model used by adapter
    static class PatientRow {
        final String patientId;
        String name;
        Timestamp lastTs;
        String lastStatus;
        String apptId;
        List<String> tags;
        String lastLine, tagsLine;
        PatientRow(String id){ this.patientId=id; }
    }

    // tiny text watcher
    static class SimpleTextWatcher implements android.text.TextWatcher {
        interface On { void on(String s); }
        private final On on; SimpleTextWatcher(On on){ this.on=on; }
        @Override public void beforeTextChanged(CharSequence s,int a,int b,int c) {}
        @Override public void onTextChanged(CharSequence s,int a,int b,int c) { on.on(String.valueOf(s)); }
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
