package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {
    private RecyclerView rv;
    private final List<DocumentSnapshot> items = new ArrayList<>();
    private RecyclerView.Adapter<VH> adapter;
    private ListenerRegistration reg;
    private TextView tvEmptyNotifs;

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvBody, tvTime;
        View dot;
        VH(@NonNull View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvBody  = v.findViewById(R.id.tvBody);
            tvTime  = v.findViewById(R.id.tvTime);
            dot     = v.findViewById(R.id.dotUnread);
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_notifications);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            // This finishes the activity and goes back
            finish();
        });

        rv = findViewById(R.id.rvNotifs);
        tvEmptyNotifs = findViewById(R.id.tvEmptyNotifs);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerView.Adapter<VH>() {
            @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
                View v = LayoutInflater.from(p.getContext())
                        .inflate(R.layout.item_notification, p, false);
                return new VH(v);
            }
            @Override public void onBindViewHolder(@NonNull VH h, int i) {
                DocumentSnapshot d = items.get(i);
                String title = d.getString("title");
                String body  = d.getString("body");
                Timestamp ts = d.getTimestamp("createdAt");
                boolean read = Boolean.TRUE.equals(d.getBoolean("read"));

                h.tvTitle.setText(title != null ? title : "DocHelp");
                h.tvBody.setText(body != null ? body : "");
                h.tvTime.setText(ts != null
                        ? android.text.format.DateFormat.format("dd MMM, h:mm a", ts.toDate())
                        : "");

                h.dot.setVisibility(read ? View.INVISIBLE : View.VISIBLE);

                h.itemView.setOnClickListener(v -> {
                    // mark read
                    d.getReference().update("read", true);
                    // deep link: open appt if you want (apptId in doc)
                    String apptId = d.getString("apptId");
                    if (apptId != null && !apptId.isEmpty()) {
                        Intent it = new Intent(v.getContext(), AppointmentNotesActivity.class);
                        it.putExtra("apptId", apptId);
                        v.getContext().startActivity(it);
                    }
                });
            }
            @Override public int getItemCount() { return items.size(); }
        };
        rv.setAdapter(adapter);
    }

    @Override protected void onStart() {
        super.onStart();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            if (tvEmptyNotifs != null) tvEmptyNotifs.setVisibility(View.VISIBLE);
            return;
        }
        reg = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Failed to load notifications: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snap == null) return;
                    items.clear();
                    items.addAll(snap.getDocuments());
                    adapter.notifyDataSetChanged();
                    if (tvEmptyNotifs != null) {
                        tvEmptyNotifs.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    @Override protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }
}
