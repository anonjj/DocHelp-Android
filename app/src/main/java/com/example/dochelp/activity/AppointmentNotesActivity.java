package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;

import com.example.dochelp.utils.KeyboardUtils;
import com.example.dochelp.model.NoteItem;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class AppointmentNotesActivity extends AppCompatActivity {

    private RecyclerView rvNotes;
    private TextInputEditText etNote;
    private TextView tvEmptyNotes;
    private View btnSend, composer;

    private final List<NoteItem> notes = new ArrayList<>();
    private RecyclerView.Adapter<VH> adapter;
    private ListenerRegistration reg;

    private String apptId;
    private String myUid;
    private String myRole; // "doctor" or "patient" (optional UI behavior)

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_appointment_notes);

        // 1) Inputs (get apptId via intent OR from AppointmentSession if you built that)
        apptId = getIntent().getStringExtra("apptId");
        if (apptId == null && com.example.dochelp.session.AppointmentSession.get().apptId != null) {
            apptId = com.example.dochelp.session.AppointmentSession.get().apptId;
        }
        if (apptId == null) {
            Toast.makeText(this,"Missing apptId", Toast.LENGTH_LONG).show();
            finish(); return;
        }

        myUid = FirebaseAuth.getInstance().getUid();

        rvNotes = findViewById(R.id.rvNotes);
        etNote = findViewById(R.id.etNote);
        tvEmptyNotes = findViewById(R.id.tvEmptyNotes);
        btnSend = findViewById(R.id.btnSend);
        composer = findViewById(R.id.composer);

        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerView.Adapter<VH>() {
            @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
                View row = LayoutInflater.from(p.getContext())
                        .inflate(R.layout.item_note_bubble, p, false);
                return new VH(row);
            }
            @Override public void onBindViewHolder(@NonNull VH h, int i) {
                NoteItem n = notes.get(i);
                String when = n.createdAt != null
                        ? DateFormat.format("EEE, dd MMM • h:mm a",
                        new Date(n.createdAt.getSeconds()*1000L)).toString()
                        : "";
                String who = "doctor".equalsIgnoreCase(n.authorRole) ? "Doctor" : "Patient";
                h.tvMeta.setText(who + " • " + when);
                h.tvText.setText(n.text != null ? n.text : "");
            }
            @Override public int getItemCount() { return notes.size(); }
        };
        rvNotes.setAdapter(adapter);

        // detect role to control composer (only doctor can write by default)
        FirebaseFirestore.getInstance().collection("appointments").document(apptId).get()
                .addOnSuccessListener(d -> {
                    String doctorId = d.getString("doctorId");
                    String patientId = d.getString("patientId");
                    myRole = (myUid != null && myUid.equals(doctorId)) ? "doctor" : "patient";
                    if (!"doctor".equals(myRole)) {
                        composer.setVisibility(View.GONE);
                    }
                });

        btnSend.setOnClickListener(v -> sendNote());
    }

    private void sendNote() {
        String text = String.valueOf(etNote.getText()).trim();
        if (TextUtils.isEmpty(text)) return;
        KeyboardUtils.hideKeyboard(this);

        Map<String,Object> m = new HashMap<>();
        m.put("authorId", myUid);
        m.put("authorRole", "doctor"); // we only allow doctor to send now
        m.put("text", text);
        m.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("appointments")
                .document(apptId).collection("notes")
                .add(m)
                .addOnSuccessListener(r -> {
                    etNote.setText("");
                    rvNotes.scrollToPosition(Math.max(notes.size()-1, 0));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override protected void onStart() {
        super.onStart();
        // live notes stream, newest last
        reg = FirebaseFirestore.getInstance().collection("appointments")
                .document(apptId).collection("notes")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Failed to load notes: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    notes.clear();
                    if (snap != null) {
                        for (DocumentSnapshot ds : snap.getDocuments()) {
                            NoteItem n = ds.toObject(NoteItem.class);
                            if (n != null) {
                                if (n.id == null) n.id = ds.getId();
                                notes.add(n);
                            }
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (tvEmptyNotes != null) {
                        tvEmptyNotes.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    rvNotes.scrollToPosition(Math.max(notes.size()-1, 0));
                });
    }

    @Override protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvMeta, tvText;
        VH(@NonNull View v){ super(v);
            tvMeta = v.findViewById(R.id.tvMeta);
            tvText = v.findViewById(R.id.tvText);
        }
    }
}
