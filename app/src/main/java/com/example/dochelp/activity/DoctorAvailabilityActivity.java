package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class DoctorAvailabilityActivity extends AppCompatActivity {

    private RecyclerView rvSlots;
    private final List<DocumentSnapshot> slots = new ArrayList<>();
    private RecyclerView.Adapter<VH> adapter;
    private ListenerRegistration reg;
    private View emptyState;
    private TextView tvSlotCount;

    private String myUid;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_doctor_availability);

        myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) { finish(); return; }

        rvSlots = findViewById(R.id.rvSlots);
        emptyState = findViewById(R.id.emptyState);
        tvSlotCount = findViewById(R.id.tvSlotCount);
        rvSlots.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerView.Adapter<VH>() {
            @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
                View row = LayoutInflater.from(p.getContext())
                        .inflate(R.layout.item_availability_slot, p, false);
                return new VH(row);
            }
            @Override public void onBindViewHolder(@NonNull VH h, int i) {
                DocumentSnapshot d = slots.get(i);
                Timestamp s = d.getTimestamp("start");
                Timestamp endTime = d.getTimestamp("end");
                Boolean active = d.getBoolean("active");
                Date sd = s != null ? s.toDate() : null;
                Date ed = endTime != null ? endTime.toDate() : null;

                String day = sd != null ? DateFormat.format("EEE, dd MMM", sd).toString() : "-";
                String range = (sd != null && ed != null)
                        ? DateFormat.format("h:mm a", sd) + " - " + DateFormat.format("h:mm a", ed)
                        : "-";
                h.tvWhen.setText(day);
                h.tvRange.setText(range);
                h.swActive.setOnCheckedChangeListener(null);
                h.swActive.setChecked(active == null || active);

                h.swActive.setOnCheckedChangeListener((btn, isOn) ->
                        d.getReference().update("active", isOn)
                                .addOnFailureListener(e ->
                                        Toast.makeText(DoctorAvailabilityActivity.this, "Failed to update slot: " + e.getMessage(), Toast.LENGTH_LONG).show()));

                h.btnDelete.setOnClickListener(v ->
                        d.getReference().delete()
                                .addOnFailureListener(e ->
                                        Toast.makeText(DoctorAvailabilityActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show()));
                h.btnEdit.setOnClickListener(v -> editSlot(d));
            }
            @Override public int getItemCount() { return slots.size(); }
        };
        rvSlots.setAdapter(adapter);
        renderSlotState();

        findViewById(R.id.btnAddSlot).setOnClickListener(v -> addSingleSlot());
        findViewById(R.id.btnAddWeek).setOnClickListener(v -> addWeekTemplate());
    }

    @Override protected void onStart() {
        super.onStart();
        // live list, newest upcoming first
        reg = FirebaseFirestore.getInstance()
                .collection("users").document(myUid).collection("availability")
                .orderBy("start", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Failed to load slots: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    slots.clear();
                    if (snap != null) slots.addAll(snap.getDocuments());
                    adapter.notifyDataSetChanged();
                    renderSlotState();
                });
    }

    @Override protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }

    private void addSingleSlot() {
        // 1) pick date
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date").build();
        datePicker.addOnPositiveButtonClickListener(sel -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(sel);
            // clear time
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            final long dayStartMs = cal.getTimeInMillis();

            // 2) pick start time
            MaterialTimePicker startPicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(10).setMinute(0)
                    .setTitleText("Start time").build();
            startPicker.addOnPositiveButtonClickListener(v -> {
                int sh = startPicker.getHour(), sm = startPicker.getMinute();

                // 3) pick end time (default +30 min)
                MaterialTimePicker endPicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        .setHour((sh + ((sm+30)/60))%24).setMinute((sm+30)%60)
                        .setTitleText("End time").build();
                endPicker.addOnPositiveButtonClickListener(v2 -> {
                    int eh = endPicker.getHour(), em = endPicker.getMinute();

                    Calendar c1 = Calendar.getInstance();
                    c1.setTimeInMillis(dayStartMs);
                    c1.set(Calendar.HOUR_OF_DAY, sh);
                    c1.set(Calendar.MINUTE, sm);

                    Calendar c2 = Calendar.getInstance();
                    c2.setTimeInMillis(dayStartMs);
                    c2.set(Calendar.HOUR_OF_DAY, eh);
                    c2.set(Calendar.MINUTE, em);

                    Date sd = c1.getTime(), ed = c2.getTime();
                    if (!ed.after(sd)) {
                        Toast.makeText(this, "End must be after start", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveSlot(new Timestamp(sd), new Timestamp(ed));
                });
                endPicker.show(getSupportFragmentManager(), "end");
            });
            startPicker.show(getSupportFragmentManager(), "start");
        });
        datePicker.show(getSupportFragmentManager(), "date");
    }

    private void saveSlot(Timestamp start, Timestamp end) {
        // Overlap check: fetch slots that start before new end, then filter client-side
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(myUid).collection("availability")
                .whereLessThan("start", end)
                .orderBy("start", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    boolean overlaps = false;
                    if (snap != null) {
                        for (DocumentSnapshot d : snap) {
                            Timestamp s = d.getTimestamp("start");
                            Timestamp e = d.getTimestamp("end");
                            if (s == null || e == null) continue;
                            // overlap if existing.start < new.end && existing.end > new.start
                            if (s.compareTo(end) < 0 && e.compareTo(start) > 0) { overlaps = true; break; }
                        }
                    }
                    if (overlaps) {
                        Toast.makeText(this, "Overlaps existing slot", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Map<String,Object> m = new HashMap<>();
                    m.put("start", start);
                    m.put("end", end);
                    m.put("active", true);
                    m.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("users").document(myUid).collection("availability")
                            .add(m)
                            .addOnSuccessListener(r -> Toast.makeText(this, "Slot added", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void addWeekTemplate() {
        // quick helper: add 5 days at 10:00–12:00 for the selected week start (today)
        Calendar base = Calendar.getInstance();
        base.set(Calendar.HOUR_OF_DAY, 0);
        base.set(Calendar.MINUTE, 0);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);

        List<Map<String,Object>> batch = new ArrayList<>();
        for (int i=0;i<5;i++) {
            Calendar s = (Calendar) base.clone(); s.add(Calendar.DAY_OF_YEAR, i); s.set(Calendar.HOUR_OF_DAY, 10);
            Calendar e = (Calendar) base.clone(); e.add(Calendar.DAY_OF_YEAR, i); e.set(Calendar.HOUR_OF_DAY, 12);
            Map<String,Object> m = new HashMap<>();
            m.put("start", new Timestamp(s.getTime()));
            m.put("end", new Timestamp(e.getTime()));
            m.put("active", true);
            m.put("createdAt", FieldValue.serverTimestamp());
            batch.add(m);
        }
        WriteBatch wb = FirebaseFirestore.getInstance().batch();
        CollectionReference col = FirebaseFirestore.getInstance()
                .collection("users").document(myUid).collection("availability");
        for (Map<String,Object> m : batch) wb.set(col.document(), m);
        wb.commit()
                .addOnSuccessListener(v -> Toast.makeText(this, "Week template added", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
    }
    private void editSlot(DocumentSnapshot d) {
        Timestamp tsStart = d.getTimestamp("start");
        Timestamp tsEnd = d.getTimestamp("end");
        Date curStart = tsStart != null ? tsStart.toDate() : new Date();
        Date curEnd = tsEnd != null ? tsEnd.toDate() : new Date(curStart.getTime() + 3600000);

        Calendar c = Calendar.getInstance();
        c.setTime(curStart);

        // 1️⃣ Pick a new date
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Edit date")
                .setSelection(curStart.getTime())
                .build();

        datePicker.addOnPositiveButtonClickListener(selDate -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selDate);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long base = cal.getTimeInMillis();

            // 2️⃣ Pick start time
            MaterialTimePicker startPicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(c.get(Calendar.HOUR_OF_DAY))
                    .setMinute(c.get(Calendar.MINUTE))
                    .setTitleText("Edit start time")
                    .build();

            startPicker.addOnPositiveButtonClickListener(v -> {
                int sh = startPicker.getHour(), sm = startPicker.getMinute();

                // 3️⃣ Pick end time
                MaterialTimePicker endPicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(TimeFormat.CLOCK_12H)
                        .setHour((sh + 1) % 24)
                        .setMinute(sm)
                        .setTitleText("Edit end time")
                        .build();

                endPicker.addOnPositiveButtonClickListener(v2 -> {
                    int eh = endPicker.getHour(), em = endPicker.getMinute();

                    Calendar c1 = Calendar.getInstance();
                    c1.setTimeInMillis(base);
                    c1.set(Calendar.HOUR_OF_DAY, sh);
                    c1.set(Calendar.MINUTE, sm);

                    Calendar c2 = Calendar.getInstance();
                    c2.setTimeInMillis(base);
                    c2.set(Calendar.HOUR_OF_DAY, eh);
                    c2.set(Calendar.MINUTE, em);

                    if (!c2.getTime().after(c1.getTime())) {
                        Toast.makeText(this, "End must be after start", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("start", new com.google.firebase.Timestamp(c1.getTime()));
                    updates.put("end", new com.google.firebase.Timestamp(c2.getTime()));

                    d.getReference().update(updates)
                            .addOnSuccessListener(r -> Toast.makeText(this, "Slot updated", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                });

                endPicker.show(getSupportFragmentManager(), "endEdit");
            });

            startPicker.show(getSupportFragmentManager(), "startEdit");
        });

        datePicker.show(getSupportFragmentManager(), "dateEdit");
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvWhen, tvRange;
        Switch swActive;
        View btnDelete,btnEdit;
        VH(@NonNull View v) {
            super(v);
            tvWhen = v.findViewById(R.id.tvWhen);
            tvRange = v.findViewById(R.id.tvRange);
            swActive = v.findViewById(R.id.swActive);
            btnDelete = v.findViewById(R.id.btnDelete);
            btnEdit = v.findViewById(R.id.btnEdit);
        }
    }

    private void renderSlotState() {
        if (tvSlotCount != null) {
            tvSlotCount.setText(slots.size() + " slots");
        }
        if (emptyState != null) {
            emptyState.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (rvSlots != null) {
            rvSlots.setVisibility(slots.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}
