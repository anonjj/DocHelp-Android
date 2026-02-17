package com.example.dochelp.activity;

import com.example.dochelp.R;
import com.example.dochelp.session.UserSession;
import com.example.dochelp.repository.FirestoreRepo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatientDashboardActivity extends AppCompatActivity {

    private final List<ApptItem> upcomingAppts = new ArrayList<>();
    private final List<DocumentSnapshot> docSnaps = new ArrayList<>();
    private final Map<String, String> doctorNameCache = new HashMap<>();

    private RecyclerView rvUpcoming, rvDocs;
    private RecyclerView.Adapter<UpcomingVH> upcomingAdapter;
    private RecyclerView.Adapter<DocVH> docsAdapter;

    // --- Only one set of listeners needed ---
    private ListenerRegistration upcomingReg, meReg, docsReg, notifReg;


    // --- Profile Card TextViews ---
    private TextView tvName, tvAge, tvGender, tvBloodGroup, tvPatientId, tvPhone, tvEmail;
    private TextView tvEmergencyContact, tvHeight, tvWeight, tvBMI;
    private TextView tvAppointmentCount;
    private TextView tvEmptyUpcoming, tvEmptyDocs;

    private ImageButton btnNotifications, btnEditProfile;
    private MaterialCardView btnViewAllDoctors;
    private ExtendedFloatingActionButton fabQuickAction;

    private FirebaseFirestore db;
    private String myUid;

    // --- Variable to hold real emergency phone ---
    private String emergencyPhone = null;


    public static class ApptItem {
        public String status;
        public String patientId;
        public String doctorId;
        public com.google.firebase.Timestamp slot;

        @com.google.firebase.firestore.DocumentId
        public String id;

        public ApptItem() {} // required by Firestore
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_dashboard);

        // --- 1. Initialize Profile Card Views ---
        tvName = findViewById(R.id.tvName);
        tvAge = findViewById(R.id.tvAge);
        tvGender = findViewById(R.id.tvGender);
        tvBloodGroup = findViewById(R.id.tvBloodGroup);
        tvPatientId = findViewById(R.id.tvPatientId);
        tvPhone = findViewById(R.id.tvPhone);
        tvEmail = findViewById(R.id.tvEmail);
        tvEmergencyContact = findViewById(R.id.tvEmergencyContact);
        tvHeight = findViewById(R.id.tvHeight);
        tvWeight = findViewById(R.id.tvWeight);
        tvBMI = findViewById(R.id.tvBMI);
        tvAppointmentCount = findViewById(R.id.tvAppointmentCount);
        tvEmptyUpcoming = findViewById(R.id.tvEmptyUpcoming);
        tvEmptyDocs = findViewById(R.id.tvEmptyDocs);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnViewAllDoctors = findViewById(R.id.btnViewAllDoctors);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        fabQuickAction = findViewById(R.id.fabQuickAction);
        ImageView imgProfile = findViewById(R.id.imgProfile);

        // --- 2. Menu and Button Setup ---
        setupMenuButton();

        findViewById(R.id.btnUpload).setOnClickListener(v ->
                startActivity(new Intent(this, UploadDocsActivity.class))
        );

        fabQuickAction.setOnClickListener(v ->
                handleEmergency()
        );

        // This button correctly links to your new activity
        btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class))
        );

        btnEditProfile.setOnClickListener(v ->
                handleEditProfile()
        );

        btnViewAllDoctors.setOnClickListener(v ->
                handleViewAllDoctors()
        );

        // --- 3. Documents RecyclerView Setup ---
        setupDocsRecyclerView();
        // ----------------------------------------

        // --- 4. Upcoming Appointments RecyclerView Setup ---
        setupUpcomingRecyclerView();
        // ----------------------------------------
        Notif.ensureChannel(this); // Ensure channel exists on create
        db = FirebaseFirestore.getInstance();
        myUid = FirebaseAuth.getInstance().getUid();
        Notif.ensureChannel(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {

                // This is the modern way to ask for a single permission
                ActivityResultLauncher<String> launcher = registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> {
                            if (isGranted) {
                                // Permission granted, good to go
                            } else {
                                Toast.makeText(this, "Notifications permission denied", Toast.LENGTH_SHORT).show();
                            }
                        }
                );
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(myUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String encoded = doc.getString("profilePhoto");
                        if (encoded != null && !encoded.isEmpty()) {
                            byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                            imgProfile.setImageBitmap(bitmap);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Log.e("ProfilePhoto", "Failed to load profile photo: " + e.getMessage()));

    }

    // --- Helper for Menu Button Setup ---
    private void setupMenuButton() {
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        btnMenu.setOnClickListener(v -> {
            PopupMenu pop = new PopupMenu(this, v);
            pop.getMenu().add(0, 2, 1, "Settings");
            pop.getMenu().add(0, 99, 2, "Logout");

            pop.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 99) {
                    FirebaseAuth.getInstance().signOut();
                    UserSession.get().clear();
                    startActivity(new Intent(this, SignInActivity.class));
                    finishAffinity();
                    return true;
                }
                else if (id == 2) {
                    Toast.makeText(this, "Settings (frontend)", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            pop.show();
        });
    }

    // --- Helper for Documents RecyclerView Setup ---
    private void setupDocsRecyclerView() {
        rvDocs = findViewById(R.id.rvDocs);
        rvDocs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        docsAdapter = new RecyclerView.Adapter<DocVH>() {
            @NonNull
            @Override
            public DocVH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
                View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_patient_doc, p, false);
                return new DocVH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull DocVH h, int i) {
                DocumentSnapshot d = docSnaps.get(i);
                String name = d.getString("fileName");
                String url = d.getString("downloadUrl");
                h.tvName.setText(name != null ? name : "Document");
                h.itemView.setOnClickListener(v -> {
                    if (url == null) return;
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(it);
                });
            }

            @Override
            public int getItemCount() {
                return docSnaps.size();
            }
        };
        rvDocs.setAdapter(docsAdapter);
    }

    // --- Helper for Upcoming Appointments RecyclerView Setup ---
    private void setupUpcomingRecyclerView() {
        rvUpcoming = findViewById(R.id.rvUpcoming);
        rvUpcoming.setLayoutManager(new LinearLayoutManager(this));
        rvUpcoming.setNestedScrollingEnabled(false);

        upcomingAdapter = new RecyclerView.Adapter<UpcomingVH>() {
            @NonNull @Override public UpcomingVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_upcoming_patient, parent, false);
                return new UpcomingVH(v);
            }

            @Override public void onBindViewHolder(@NonNull UpcomingVH h, int position) {
                if (position < 0 || position >= upcomingAppts.size()) return;
                ApptItem a = upcomingAppts.get(position);

                // --- ADDED: Reset button state for recycled views ---
                if (h.btnPatientCancel != null) h.btnPatientCancel.setEnabled(true);

                Timestamp ts = a.slot;
                String when = ts != null
                        ? android.text.format.DateFormat.format("EEE, dd MMM • h:mm a", new java.util.Date(ts.getSeconds() * 1000L)).toString()
                        : "-";
                h.tvWhen.setText(when);

                String status = a.status != null ? a.status : "pending";
                h.tvStatus.setText(cap(status));

                bindDoctorName(a.doctorId, h.tvDoc);

                h.itemView.setOnClickListener(v -> {
                    Intent i = new Intent(v.getContext(), AppointmentNotesActivity.class);
                    i.putExtra("apptId", a.id);
                    v.getContext().startActivity(i);
                });

                // Make variables final for the listener
                final String apptId = a.id;
                final String doctorId = a.doctorId;
                final String finalWhen = when;

                if (h.btnPatientCancel != null) {
                    h.btnPatientCancel.setOnClickListener(v -> {
                        // Disable button to prevent spam
                        h.btnPatientCancel.setEnabled(false);

                        FirestoreRepo.get().cancelAppointment(apptId)
                                .addOnSuccessListener(aVoid -> {
                                    // Notify the DOCTOR
                                    String title = "Appointment Cancelled";
                                    String body = "Your appointment for " + finalWhen + " has been cancelled by the patient.";

                                    FirestoreRepo.get().notifyUser(
                                            doctorId,
                                            title,
                                            body,
                                            "appt_cancelled_by_patient",
                                            apptId
                                    );

                                    Toast.makeText(v.getContext(), "Appointment Cancelled", Toast.LENGTH_SHORT).show();
                                    // The live listener will automatically remove this item
                                })
                                .addOnFailureListener(e -> {
                                    // Re-enable on failure
                                    h.btnPatientCancel.setEnabled(true);
                                    Toast.makeText(v.getContext(), "Failed to cancel: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    });
                }
                // --- END: Cancel button logic ---

            }

            @Override public int getItemCount() { return upcomingAppts.size(); }
        };
        rvUpcoming.setAdapter(upcomingAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Notif.ensureChannel(this); // Re-ensure channel just in case
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // 1. Real-time Document Listener
        docsReg = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("documents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { Log.e("PatientDash", "Docs listener error", e); return; }
                    docSnaps.clear();
                    if (snap != null) docSnaps.addAll(snap.getDocuments());
                    if (docsAdapter != null) docsAdapter.notifyDataSetChanged();
                    updateDocsEmptyState(docSnaps.size());
                });

        // 2. Real-time Profile and Appointment Listeners (Chained)
        meReg = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) {
                        Log.e("PatientDash", "Profile listener error", e);
                        return;
                    }

                    // --- REAL-TIME PROFILE CARD UPDATE ---
                    updateProfileCard(doc, uid);

                    // Setup Appointment Listener (runs only once after profile is loaded)
                    if (upcomingReg == null) {
                        setupAppointmentListener(uid);
                    }
                });

        // 3. Real-time Notification Listener
        if (notifReg != null) notifReg.remove(); // Just in case

        notifReg = db.collection("users").document(myUid)
                .collection("notifications")
                .whereEqualTo("read", false) // Only get new ones
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(5) // Only check the 5 most recent
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        Log.e("PatientDash", "Notification listener FAILED", e);
                        return;
                    }
                    if (snap.isEmpty()) {
                        Log.d("PatientDash", "Listener is active, but no unread notifications found.");
                    }

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String title = d.getString("title");
                        String body  = d.getString("body");
                        String apptId = d.getString("apptId");

                        Log.d("PatientDash", "New notification found: " + title);
                        Log.d("PatientDash", "Body: " + body);

                        // --- Build the tap/click action ---
                        // (You can customize this to open any activity)
                        Intent it = new Intent(this, NotificationsActivity.class); // Or AppointmentNotesActivity?
                        if (apptId != null) {
                            it = new Intent(this, AppointmentNotesActivity.class);
                            it.putExtra("apptId", apptId);
                        }

                        PendingIntent pi = PendingIntent.getActivity(
                                this, (int)System.currentTimeMillis(), it,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                        // --- FIXED: Removed duplicate call to Notif.show() ---
                        Log.d("PatientDash", "Attempting to show notification...");
                        Notif.show(this, title != null ? title : "DocHelp", body != null ? body : "", pi);

                        // --- Mark as 'read' so it doesn't fire again ---
                        d.getReference().update("read", true);
                    }
                });

    }

    private void setupAppointmentListener(String uid) {
        Query q = FirebaseFirestore.getInstance()
                .collection("appointments")
                .whereEqualTo("patientId", uid)
                .whereIn("status", Arrays.asList("pending", "confirmed", "rescheduled"))
                .whereGreaterThan("slot", new Date())
                .orderBy("slot");

        upcomingReg = q.addSnapshotListener((snap, eAppt) -> {
            if (eAppt != null) {
                Log.e("PatientDash", "Appt Listener Error", eAppt);
                return;
            }

            upcomingAppts.clear();
            if (snap != null) {
                for (DocumentSnapshot ds : snap.getDocuments()) {
                    ApptItem a = ds.toObject(ApptItem.class);
                    if (a != null) {
                        if (a.id == null || a.id.isEmpty()) a.id = ds.getId();
                        upcomingAppts.add(a);
                    }
                }
            }

            if (upcomingAdapter != null) {
                upcomingAdapter.notifyDataSetChanged();
            }

            // 🧮 Update counter dynamically
            tvAppointmentCount.setText(String.valueOf(upcomingAppts.size()));
            updateUpcomingEmptyState(upcomingAppts.size());
        });
    }


    // --- Profile Card Update Logic ---
    private void updateProfileCard(DocumentSnapshot doc, String uid) {
        String name = doc.getString("name");
        tvName.setText(name != null ? name : "Patient");
        tvPatientId.setText("Patient ID: " + uid.substring(0, 8) + "...");

        tvEmail.setText(doc.getString("email") != null ? doc.getString("email") : "--");
        tvPhone.setText(doc.getString("phone") != null ? doc.getString("phone") : "--");
        tvGender.setText(doc.getString("gender") != null ? doc.getString("gender") : "--");
        tvBloodGroup.setText(doc.getString("bloodGroup") != null ? doc.getString("bloodGroup") : "--");

        String height = doc.getString("height");
        String weight = doc.getString("weight");
        tvHeight.setText(height != null && !height.isEmpty() ? height + " cm" : "--");
        tvWeight.setText(weight != null && !weight.isEmpty() ? weight + " kg" : "--");

        String dob = doc.getString("dob");
        tvAge.setText(dob != null && dob.length() >= 4 ? "DOB: " + dob.substring(0, 4) : "Age --");

        String eName = doc.getString("emergencyName");
        String ePhone = doc.getString("emergencyPhone");
        this.emergencyPhone = ePhone; // Save for emergency button

        if (eName != null && !eName.isEmpty()) {
            tvEmergencyContact.setText("Emergency: " + eName + " (" + (ePhone != null ? ePhone : "N/A") + ")");
        } else {
            tvEmergencyContact.setText("Emergency: None set");
        }
        tvBMI.setText("--"); // Placeholder
    }

    private void bindDoctorName(@Nullable String doctorId, @NonNull TextView target) {
        target.setTag(doctorId);
        target.setText("Doctor");
        if (doctorId == null || doctorId.isEmpty()) return;

        String cached = doctorNameCache.get(doctorId);
        if (cached != null) {
            target.setText(cached);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(doctorId)
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    if (name == null || name.trim().isEmpty()) {
                        name = "Doctor";
                    }
                    doctorNameCache.put(doctorId, name);

                    Object tag = target.getTag();
                    if (doctorId.equals(tag)) {
                        target.setText(name);
                    }
                })
                .addOnFailureListener(e -> doctorNameCache.put(doctorId, "Doctor"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        // --- Single, correct onStop to clean up all listeners ---
        if (meReg != null) { meReg.remove(); meReg = null; }
        if (upcomingReg != null) { upcomingReg.remove(); upcomingReg = null; }
        if (docsReg != null) { docsReg.remove(); docsReg = null; }
        if (notifReg != null) {
            notifReg.remove();
            notifReg = null;
        }
    }

    // --- Helper Classes ---

    static class DocVH extends RecyclerView.ViewHolder {
        TextView tvName;
        DocVH(View v){ super(v); tvName = v.findViewById(R.id.tvDocName); }
    }

    // --- MODIFIED: Added btnPatientCancel ---
    static class UpcomingVH extends RecyclerView.ViewHolder {
        TextView tvDoc, tvWhen, tvStatus;
        View btnPatientCancel; // <-- ADDED

        UpcomingVH(@NonNull View v) {
            super(v);
            tvDoc = v.findViewById(R.id.tvDoc);
            tvWhen = v.findViewById(R.id.tvWhen);
            tvStatus = v.findViewById(R.id.tvStatus);
            btnPatientCancel = v.findViewById(R.id.btnPatientCancel); // <-- ADDED
        }
    }

    private String cap(String s){ return (s==null||s.isEmpty())? "" : Character.toUpperCase(s.charAt(0))+s.substring(1); }

    // --- Button Handlers ---

    private void handleEmergency() {
        if (this.emergencyPhone == null || this.emergencyPhone.isEmpty()) {
            Toast.makeText(this, "No emergency contact set. Please edit your profile.", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Emergency Alert!")
                .setMessage("Do you want to call your emergency contact (" + this.emergencyPhone + ") now?")
                .setPositiveButton("Call Now", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + this.emergencyPhone));
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "Phone app not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // This handler wasn't used, but the button onClick was correct.
    // private void handleNotifications() {
    //     startActivity(new Intent(this, NotificationsActivity.class));
    // }

    private void handleEditProfile() {
        startActivity(new Intent(this, EditPatientProfileActivity.class));
    }

    private void handleViewAllDoctors() {
        startActivity(new Intent(this, DoctorsListActivity.class));
    }

    // --- Notification Helper Class ---

    public static final class Notif {
        public static final String CHANNEL_ID = "dochelp_updates";

        public static void ensureChannel(Context c){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "DocHelp Updates", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Appointments and notes");
                NotificationManager nm = c.getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.createNotificationChannel(ch);
                }
            }
        }
        @SuppressLint("MissingPermission")
        public static void show(Context c, String title, String body, @Nullable PendingIntent pi){
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notifications)   // Make sure you have this drawable
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            if (pi != null) b.setContentIntent(pi);
            NotificationManagerCompat.from(c).notify((int)System.currentTimeMillis(), b.build());
        }
    }
    private void updateUpcomingEmptyState(int count) {
        if (tvEmptyUpcoming != null) {
            tvEmptyUpcoming.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void updateDocsEmptyState(int count) {
        if (tvEmptyDocs != null) {
            tvEmptyDocs.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        }
    }


}
