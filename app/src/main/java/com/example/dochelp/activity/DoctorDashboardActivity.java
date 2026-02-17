package com.example.dochelp.activity;

import com.example.dochelp.R;
import com.example.dochelp.session.UserSession;
import com.example.dochelp.repository.FirestoreRepo;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet; // Kept in case you use it elsewhere, but not needed for the deleted block
import java.util.List;
import java.util.Map;
import java.util.Calendar; // Kept in case you use it elsewhere, but not needed for the deleted block

public class DoctorDashboardActivity extends AppCompatActivity {
    private static final String TAG = "DoctorDash";

    // --- View Declarations ---
    private RecyclerView rvConfirmed;
    private RecyclerView rvPending;

    // Top-level counter TextViews (for the big boxes)
    private TextView tvCountPending, tvCountToday, tvCountTotal;
    private TextView tvTotalPatients; // NEW

    // Counter TextViews/Chips near list headers
    private Chip chipPendingCount;
    private Chip chipConfirmedCount;
    private TextView tvEmptyPending;
    private TextView tvEmptyConfirmed;

    private TextView tvDocName, tvSpec, tvLicense, tvExperience, tvHospital, tvConsultationFee, tvEmail, tvPhone;

    //Made this a class member so we can update it in the profile listener
    private ImageView ivAvatar;


    // --- Data and Adapters ---
    private final List<DocumentSnapshot> confirmed = new ArrayList<>();
    private final List<DocumentSnapshot> pending = new ArrayList<>();
    private RecyclerView.Adapter<ConfirmedVH> confirmedAdapter;
    private RecyclerView.Adapter<PendingVH> pendingAdapter;

    // --- Firebase and Caching ---
    private ListenerRegistration regPending, regConfirmed, regToday, notifReg; // We'll nullify notifReg
    private ListenerRegistration regProfile;
    private String myUid;
    private final Map<String, String> patientNameCache = new HashMap<>();

    // Firestore instance for convenience in non-Repo calls
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Time boundaries for the "Today" query
    private Timestamp startOfTodayTs, endOfTodayTs;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_dashboard);

        // --- Get UID once ---
        myUid = FirebaseAuth.getInstance().getUid();

        ivAvatar = findViewById(R.id.imgProfile);

        // --- View Initialization (Profile Data) ---
        tvDocName = findViewById(R.id.tvDocName);
        tvSpec = findViewById(R.id.tvSpec);
        tvLicense = findViewById(R.id.tvLicense);
        tvExperience = findViewById(R.id.tvExperience);
        tvHospital = findViewById(R.id.tvHospital);
        tvConsultationFee = findViewById(R.id.tvConsultationFee);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);


        // --- View Initialization (Counters) ---
        // Find all the top-level counter views (0 Pending, 0 Today, 0 Total)
        tvCountPending = findViewById(R.id.tvCountPending);
        tvCountToday   = findViewById(R.id.tvCountToday);
        tvCountTotal   = findViewById(R.id.tvCountTotal);
        tvTotalPatients = findViewById(R.id.tvTotalPatients); // NEW

        // Find list header chips/text views
        chipPendingCount = findViewById(R.id.chipPendingCount);
        chipConfirmedCount = findViewById(R.id.chipConfirmedCount);
        tvEmptyPending = findViewById(R.id.tvEmptyPending);
        tvEmptyConfirmed = findViewById(R.id.tvEmptyConfirmed);


        // --- Button Initialization ---
        ImageButton btnEditProfile = findViewById(R.id.btnEditProfile);
        View btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) btnMenu.setOnClickListener(this::showMenu);

        // This is a good defensive check.
        if (tvCountPending == null || tvCountToday == null || tvCountTotal == null) {
            Log.e("DoctorDash", "Counter views not found. Check layout IDs or variants.");
        }

        // --- Pre-calculate Today's time range once ---
        calculateTodayTimeRange();
        
        // --- NEW: Run Analytics for Total Patients ---
        if (tvTotalPatients != null && myUid != null) {
            db.collection("appointments")
                .whereEqualTo("doctorId", myUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                     tvTotalPatients.setText("Total Patients: " + querySnapshot.size());
                });
        }


        // --- 2. SETUP CONFIRMED APPOINTMENTS LIST ---
        rvConfirmed = findViewById(R.id.rvConfirmed);
        rvConfirmed.setLayoutManager(new LinearLayoutManager(this));
        setupConfirmedAdapter();
        rvConfirmed.setAdapter(confirmedAdapter);

        // --- 3. SETUP PENDING APPOINTMENTS LIST ---
        rvPending = findViewById(R.id.rvPending);
        rvPending.setLayoutManager(new LinearLayoutManager(this));
        setupPendingAdapter();
        rvPending.setAdapter(pendingAdapter);

        // --- Button Click Handlers ---
        btnEditProfile.setOnClickListener(v -> {
            // Assuming your edit activity class is named EditDoctorProfileActivity
            startActivity(new Intent(this, EditDoctorProfileActivity.class));
        });

        // happen one time.
        Notif.ensureChannel(this);
    }

    // Helper to setup confirmed adapter
    private void setupConfirmedAdapter() {
        confirmedAdapter = new RecyclerView.Adapter<ConfirmedVH>() {
            @NonNull
            @Override
            public ConfirmedVH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
                View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_confirmed_appt, p, false);
                return new ConfirmedVH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull ConfirmedVH h, int i) {
                if (i < 0 || i >= confirmed.size()) return;
                DocumentSnapshot d = confirmed.get(i);


                // Good, re-enable the button on bind, just in case
                if (h.btnCancel != null) h.btnCancel.setEnabled(true);

                final String apptId = d.getId();
                final String patientId = d.getString("patientId");
                Timestamp ts = d.getTimestamp("slot");

                String when = "-";
                if (ts != null) {
                    when = android.text.format.DateFormat
                            .format("EEE, dd MMM • h:mm a", ts.toDate().getTime())
                            .toString();
                }
                h.tvWhen.setText(when);

                final String finalWhen = when; // Need this for the click listener



                if (h.btnCancel != null) {
                    h.btnCancel.setOnClickListener(v -> {
                        // Disable button to prevent spam
                        h.btnCancel.setEnabled(false); // <-- Set to false on click

                        FirestoreRepo.get().cancelAppointment(apptId)
                                .addOnSuccessListener(aVoid -> {
                                    // let the listener remove the item. Perfect.
                                    String title = "Appointment Cancelled";
                                    String body = "Your appointment for " + finalWhen + " has been cancelled by your doctor.";
                                    FirestoreRepo.get().notifyUser(
                                            patientId,
                                            title,
                                            body,
                                            "appt_cancelled",
                                            apptId
                                    );
                                    // The live listener will automatically remove this item
                                    Toast.makeText(v.getContext(), "Appointment Cancelled", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    // Re-enable on failure so they can try again
                                    h.btnCancel.setEnabled(true);
                                    Toast.makeText(v.getContext(), "Failed to cancel: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    });
                }
                if (h.btnReschedule != null) {
                    h.btnReschedule.setOnClickListener(v -> {
                        // Just call the helper method!
                        // It passes the correct, final apptId and patientId
                        showRescheduleDialog(apptId, patientId);
                    });
                }
                // Use unified patient name fetching logic
                fetchAndSetPatientName(h.tvPatient, patientId, i, h, null);

                // Notes chip click handler
                h.chipNotes.setOnClickListener(v -> {
                    // This AppointmentSession singleton looks useful for passing context
                    com.example.dochelp.session.AppointmentSession.get()
                            .set(apptId, patientId, FirebaseAuth.getInstance().getUid());

                    Intent it = new Intent(v.getContext(), AppointmentNotesActivity.class);
                    it.putExtra("apptId", apptId);
                    it.putExtra("patientId", patientId);
                    v.getContext().startActivity(it);
                });
            }

            @Override
            public int getItemCount() {
                return confirmed.size();
            }
        };
    }

    // Helper to setup pending adapter
    private void setupPendingAdapter() {
        pendingAdapter = new RecyclerView.Adapter<PendingVH>() {
            @NonNull
            @Override
            public PendingVH onCreateViewHolder(@NonNull ViewGroup p, int v) {
                View v1 = LayoutInflater.from(p.getContext()).inflate(R.layout.item_pending_request, p, false);
                return new PendingVH(v1);
            }

            @Override
            public void onBindViewHolder(@NonNull PendingVH h, int i) {
                if (i < 0 || i >= pending.size()) return;
                DocumentSnapshot d = pending.get(i);

                // OnBindViewHolder is called when scrolling,
                // the buttons to an enabled state here.
                Log.d(TAG, "Binding item at position " + i + ". Setting buttons ENABLED.");
                h.btnAccept.setEnabled(true);
                h.btnDecline.setEnabled(true);

                final String patientId = d.getString("patientId");
                final String apptId = d.getId();
                Timestamp ts = d.getTimestamp("slot");
                String reason = d.getString("reason");

                final String when; // For notification body
                final String displayWhen; // For UI display
                if (ts != null) {
                    when = android.text.format.DateFormat
                            .format("EEE, dd MMM • h:mm a", ts.toDate().getTime())
                            .toString();
                    displayWhen = when;
                } else {
                    when = "your recent request"; // Better for notification
                    displayWhen = "-"; // Better for UI
                }

                // Pass 'displayWhen' as a suffix for the TV
                fetchAndSetPatientName(h.tvPatient, patientId, i, h, " • " + displayWhen);

                String reasonStr = (reason != null && !reason.isEmpty()) ? reason : "-";
                h.tvReason.setText("Reason: " + reasonStr);

                // Actions: Accept/Decline
                h.btnAccept.setOnClickListener(v2 -> {
                    // Disable buttons immediately on click.
                    Log.d(TAG, "ACCEPT button CLICKED for apptId: " + apptId);
                    h.btnAccept.setEnabled(false);
                    h.btnDecline.setEnabled(false);

                    FirestoreRepo.get().respondAppointment(apptId, true)
                            .addOnSuccessListener(aVoid -> {
                                // The listener will remove this item, so no need to re-enable
                                Log.d(TAG, "Accept SUCCESS for apptId: " + apptId);
                                String title = "Appointment Confirmed";
                                String body = "Your appointment for " + when + " has been confirmed.";
                                FirestoreRepo.get().notifyUser(
                                        patientId, title, body, "appointment_confirmed", apptId
                                );
                            })
                            .addOnFailureListener(e -> {
                                // --- FIX: Re-enable buttons on failure so user can try again ---
                                Log.e(TAG, "Accept FAILED for apptId: " + apptId, e);
                                h.btnAccept.setEnabled(true);
                                h.btnDecline.setEnabled(true);
                                Toast.makeText(v2.getContext(), "Accept Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });

                h.btnDecline.setOnClickListener(v2 -> {
                    // --- HEY! --- This is the fix. Disable buttons immediately on click.
                    Log.d(TAG, "DECLINE button CLICKED for apptId: " + apptId);
                    h.btnAccept.setEnabled(false);
                    h.btnDecline.setEnabled(false);

                    FirestoreRepo.get().respondAppointment(apptId, false)
                            .addOnSuccessListener(aVoid -> {
                                // The listener will remove this item, so no need to re-enable
                                Log.d(TAG, "Decline SUCCESS for apptId: " + apptId);
                                String title = "Appointment Update";
                                String body = "Your appointment request for " + when + " was declined.";
                                FirestoreRepo.get().notifyUser(
                                        patientId, title, body, "appointment_declined", apptId
                                );
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Decline FAILED for apptId: " + apptId, e); // 'e' prints the error
                                h.btnAccept.setEnabled(true);
                                h.btnDecline.setEnabled(true);
                                Toast.makeText(v2.getContext(), "Decline Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });
                // --- END NOTIFICATION ---

                // Nav to notes
                h.itemView.setOnClickListener(v -> {
                    com.example.dochelp.session.AppointmentSession.get()
                            .set(apptId, patientId, FirebaseAuth.getInstance().getUid());

                    Intent intent = new Intent(v.getContext(), AppointmentNotesActivity.class);
                    intent.putExtra("apptId", apptId);
                    v.getContext().startActivity(intent);
                });
            }

            @Override
            public int getItemCount() {
                return pending.size();
            }
        };
    }

    // --- FIX 4: Changed signature to accept ViewHolder 'h' ---
    //this one handles cache, loading, success, and failure.
    private void fetchAndSetPatientName(TextView tv, String patientId, int position, RecyclerView.ViewHolder h, String suffix) {
        final String safeSuffix = (suffix != null) ? suffix : "";
        tv.setText("Loading..." + safeSuffix); // Set a temporary state

        if (patientId == null || patientId.isEmpty()) {
            tv.setText("Patient" + safeSuffix);
            return;
        }

        // 1. Check Cache
        String cached = patientNameCache.get(patientId);
        if (cached != null) {
            tv.setText(cached + safeSuffix);
            return;
        }

        // 2. Fetch from Firestore
        db.collection("users").document(patientId).get()
                .addOnSuccessListener(doc -> {
                    String name = (doc != null) ? doc.getString("name") : null;
                    String nm = (name != null && !name.isEmpty()) ? name : "Patient";

                    // 3. Update Cache
                    patientNameCache.put(patientId, nm);

                    // 4. Update TextView only if the view hasn't been recycled
                    // item in the list as the user scrolls.
                    if (h.getBindingAdapterPosition() == position) {
                        tv.setText(nm + safeSuffix);
                    }
                })
                .addOnFailureListener(e -> {
                    // Fallback on failure
                    if (h.getBindingAdapterPosition() == position) {
                        tv.setText("Error/Patient" + safeSuffix);
                    }
                });
    }

    private void calculateTodayTimeRange() {
        ZonedDateTime startZ = ZonedDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime endZ   = startZ.plusDays(1);
        startOfTodayTs = new Timestamp(java.util.Date.from(startZ.toInstant()));
        endOfTodayTs   = new Timestamp(java.util.Date.from(endZ.toInstant()));
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (myUid == null) {
            Log.e("DoctorDash", "User is logged out, cannot attach listeners.");
            // Optionally redirect to login
            return;
        }

        // --- 1. PENDING LISTENER: Real-time update for Pending list and counts ---
        // This relies on FirestoreRepo.listenDoctorPending filtering by status="pending"
        regPending = FirestoreRepo.get().listenDoctorPending(myUid, (snap, e) -> {
            if (e != null) { Log.e("DoctorDash", "Pending listener failed: " + e.getMessage()); return; }
            pending.clear();
            if (snap != null) pending.addAll(snap.getDocuments());
            pendingAdapter.notifyDataSetChanged();

            // Update top-level "Pending" box count
            setCount(tvCountPending, pending.size());
            String pendingCount = String.valueOf(pending.size());

            // Update list header counts
            chipPendingCount.setText(pendingCount);
            updateEmptyStates();
        });

        // --- 2. CONFIRMED LISTENER: Real-time update for Confirmed list and list count ---
        // This relies on FirestoreRepo.listenDoctorConfirmed filtering by status="confirmed"
        if (regConfirmed != null) regConfirmed.remove();

        regConfirmed = db.collection("appointments")
                .whereEqualTo("doctorId", myUid) // 'myUid' is your variable
                .whereIn("status", Arrays.asList("confirmed", "rescheduled"))

                // --- OPTIMIZATION 1: Only show upcoming appointments ---
                .whereGreaterThan("slot", new Date())

                // --- OPTIMIZATION 2: Show the closest appointments first ---
                .orderBy("slot", Query.Direction.ASCENDING)

                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Log.e("DoctorDash", "Confirmed listener failed: " + e.getMessage());
                        return;
                    }

                    confirmed.clear(); // Clear the old list

                    if (snap != null) {
                        confirmed.addAll(snap.getDocuments()); // Add all new (filtered) docs
                    }

                    confirmedAdapter.notifyDataSetChanged();

                    // Update list header chip count
                    chipConfirmedCount.setText(String.valueOf(confirmed.size()));
                    updateEmptyStates();
                });

        // --- 3. TODAY & TOTAL COUNTS: Dedicated logic for the remaining top boxes ---
        listenTodayAndTotal(myUid);
        listenDoctorProfile(myUid);
    }


    /**
     * Sets up listeners for the "Today" (live) and "Total" (one-shot aggregate) counts.
     */
    private void listenTodayAndTotal(String doctorId) {
        // --- TODAY (Live Listener) ---
        // Watches appointments with status "pending" OR "confirmed" within today's time range.
        if (regToday != null) regToday.remove();
        regToday = db.collection("appointments")
                .whereEqualTo("doctorId", doctorId)
                .whereIn("status", Arrays.asList("pending", "confirmed"))
                .whereGreaterThanOrEqualTo("slot", startOfTodayTs)
                .whereLessThan("slot", endOfTodayTs)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { Log.e("DoctorDash", "Today listener failed: " + e.getMessage()); return; }
                    int n = (snap != null) ? snap.size() : 0;
                    setCount(tvCountToday, n); // Update top-level "Today" box count
                });

        // --- TOTAL (One-Shot Aggregate Count) ---
        // way to get a total count. Way better than downloading all the documents.
        db.collection("appointments")
                .whereEqualTo("doctorId", doctorId)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(agg -> setCount(tvCountTotal, (int) agg.getCount()))
                .addOnFailureListener(err -> {
                    // And you even have a fallback! This is robust code.
                    Log.e("DoctorDash", "Aggregate count failed, falling back.");
                    db.collection("appointments").whereEqualTo("doctorId", doctorId).get()
                            .addOnSuccessListener(s -> setCount(tvCountTotal, s.size()));
                });
    }

    /**
     * Helper method to safely set text on a TextView.
     */
    private void setCount(TextView tv, int n) {
        if (tv != null) tv.setText(String.valueOf(n));
    }

    private void updateEmptyStates() {
        if (tvEmptyPending != null) {
            tvEmptyPending.setVisibility(pending.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (tvEmptyConfirmed != null) {
            tvEmptyConfirmed.setVisibility(confirmed.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    // --- FIX 3: Added helper for safe text setting to prevent null crashes ---
    /**
     * Helper method to safely set text, falling back to an empty string if text is null.
     */
    private void safeSetText(TextView tv, String text) {
        if (tv != null) {
            tv.setText(text != null ? text : "");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // --- Clean up listeners to prevent memory leaks ---
        // --- CLEANUP: Set all listeners to null after removing ---
        if (regPending != null) { regPending.remove(); regPending = null; }
        if (regConfirmed != null) { regConfirmed.remove(); regConfirmed = null; }
        if (regToday != null) { regToday.remove(); regToday = null; }
        if (regProfile != null) { regProfile.remove(); regProfile = null; }

        // --- HEY! --- Also nullifying the (now removed) listener, just to be safe.
        if (notifReg != null) { notifReg.remove(); notifReg = null; }
    }

    // --- ViewHolder Classes ---

    static class PendingVH extends RecyclerView.ViewHolder {
        TextView tvPatient, tvReason, tvWhen;
        View btnAccept, btnDecline;

        PendingVH(View v) {
            super(v);
            tvPatient = v.findViewById(R.id.tvPatient);
            tvReason = v.findViewById(R.id.tvReason);
            btnAccept = v.findViewById(R.id.btnAccept);
            btnDecline = v.findViewById(R.id.btnDecline);
            // Assuming the time view is included in the patient text view or a separate one is needed
            // If tvWhen is separate in item_pending_request, uncomment and add ID:
            tvWhen = v.findViewById(R.id.tvWhen);
        }
    }

    static class ConfirmedVH extends RecyclerView.ViewHolder {
        TextView tvPatient, tvWhen;
        Chip chipNotes;
        View btnCancel, btnReschedule;

        ConfirmedVH(View v) {
            super(v);
            tvPatient = v.findViewById(R.id.tvPatient);
            tvWhen = v.findViewById(R.id.tvWhen);
            chipNotes = v.findViewById(R.id.chipNotes);
            btnCancel = v.findViewById(R.id.btnCancel);
            btnReschedule = v.findViewById(R.id.btnReschedule);
        }
    }

    // --- Helper Menu Functions ---

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.doctor_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut();
                com.example.dochelp.session.UserSession.get().clear();
                startActivity(new Intent(this, SignInActivity.class));
                finishAffinity(); // Good, this clears the back stack.
                return true;
            } else if (id == R.id.action_availability) {
                startActivity(new Intent(this, DoctorAvailabilityActivity.class));
                return true;
            } else if (id == R.id.action_patients) {
                startActivity(new Intent(this, DoctorPatientsActivity.class));
                return true;
            }

            return false;
        });
        popup.show();
    }

    /**
     * This sends a note and (correctly) notifies the patient.
     * This was already working as requested.
     */
    private void sendNote(String apptId, String text){
        String author = FirebaseAuth.getInstance().getUid();
        Map<String,Object> n = new HashMap<>();
        n.put("authorId", author);
        n.put("text", text);
        n.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("appointments").document(apptId).collection("notes").add(n)
                .addOnSuccessListener(ref -> {
                    // Fetch appt to know who to notify
                    db.collection("appointments").document(apptId).get()
                            .addOnSuccessListener(appt -> {
                                String patientId = appt.getString("patientId");
                                FirestoreRepo.get().notifyUser(
                                        patientId,
                                        "New note from your doctor",
                                        "Tap to view the latest note.",
                                        "doctor_note",
                                        apptId
                                );
                            });
                });
    }

    // New method to listen to doctor profile changes
    private void listenDoctorProfile(String doctorId) {
        // 1. Remove previous listener if it exists
        if (regProfile != null) {
            regProfile.remove();
        }

        // 2. Start the real-time listener on the doctor's document
        regProfile = FirebaseFirestore.getInstance().collection("users").document(doctorId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null) {
                        Log.e("DoctorDash", "Profile listener failed: " + e.getMessage());
                        return;
                    }

                    // Ensure the document exists before trying to read fields
                    if (doc != null && doc.exists()) {

                        // Using safeSetText for null safety


                        // Update all text elements
                        safeSetText(tvDocName, doc.getString("name"));
                        safeSetText(tvSpec, doc.getString("specialization"));
                        safeSetText(tvLicense, doc.getString("licenseNumber"));
                        safeSetText(tvHospital, doc.getString("hospitalName"));
                        safeSetText(tvConsultationFee, doc.getString("consultationFee"));
                        safeSetText(tvEmail, doc.getString("email"));
                        safeSetText(tvPhone, doc.getString("phone"));

                        // Now the photo updates in real-time, too!
                        String encoded = doc.getString("profilePhoto");
                        if (encoded != null && !encoded.isEmpty()) {
                            try {
                                byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                                ivAvatar.setImageBitmap(bitmap);
                            } catch (Exception ex) {
                                Log.e(TAG, "Failed to decode profile photo", ex);
                                // ivAvatar.setImageResource(R.drawable.default_avatar); // Good idea to have a default we wiil see to that later
                            }
                        } else {
                            // ivAvatar.setImageResource(R.drawable.default_avatar); // And set default if no photo
                        }
                    }
                });
    }

    // self-contained class for notifications.
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
                    .setSmallIcon(R.drawable.ic_notifications)   // ensure you have this drawable
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            if (pi != null) b.setContentIntent(pi);
            NotificationManagerCompat.from(c).notify((int)System.currentTimeMillis(), b.build());
        }
    }

    private void showRescheduleDialog(String apptId, String patientId) {
        Calendar cal = Calendar.getInstance();
        Context context = this; // 'this' is the Activity context

        // Step 1: Show Date Picker
        DatePickerDialog datePicker = new DatePickerDialog(context, (view, year, month, day) -> {

            // Step 2: Show Time Picker after date is chosen
            TimePickerDialog timePicker = new TimePickerDialog(context, (tp, hour, minute) -> {

                // --- Logic is now clean and uses the correct variables ---
                cal.set(year, month, day, hour, minute);
                Timestamp newSlot = new Timestamp(cal.getTime());

                // Use the class 'db' instance
                // Step 3: Update appointment slot & status
                db.collection("appointments").document(apptId) // <-- FIXED (was currentApptId)
                        .update("slot", newSlot,
                                "status", "rescheduled",
                                "updatedAt", FieldValue.serverTimestamp())
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(context, "Appointment rescheduled!", Toast.LENGTH_SHORT).show();

                            // Step 4: Create a "notification" doc for patient
                            Map<String, Object> notif = new HashMap<>();
                            notif.put("type", "reschedule");
                            notif.put("message", "Your appointment has been rescheduled.");
                            notif.put("newSlot", newSlot);
                            notif.put("doctorId", myUid); // (was doctorId, uses class member)
                            notif.put("read", false);
                            notif.put("createdAt", FieldValue.serverTimestamp());

                            db.collection("users").document(patientId) // (was currentPatientId)
                                    .collection("notifications")
                                    .add(notif)
                                    .addOnSuccessListener(ref ->
                                            Log.d("Reschedule", "Patient notified"))
                                    .addOnFailureListener(err ->
                                            Log.e("Reschedule", "Notif failed", err));
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());

            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false); // 'false' for 12-hour clock
            timePicker.show();

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        // Optional: Prevent picking dates in the past
        datePicker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePicker.show();
    }
}
