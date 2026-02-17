package com.example.dochelp.repository;

import com.example.dochelp.R;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.Timestamp;
import java.util.*;

public class FirestoreRepo {
    private static FirestoreRepo I = new FirestoreRepo();
    public static FirestoreRepo get() {
        return I;
    }    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Create appointment request (patient)
    public Task<DocumentReference> requestAppointment(String patientId, String doctorId,
                                                      Timestamp slot, @Nullable String reason) {
        Map<String, Object> a = new HashMap<>();
        a.put("status", "pending");
        a.put("patientId", patientId);
        a.put("doctorId", doctorId);
        a.put("slot",   slot);
        // \ fixed-length slot: +30 min end; caller can override
        a.put("end",    new Timestamp(new Date(slot.toDate().getTime() + 30*60*1000)));
        if (reason != null) a.put("reason", reason);
        a.put("createdAt", FieldValue.serverTimestamp());
        a.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection("appointments").add(a);
    }

    // Doctor response (accept/decline)
    public Task<Void> respondAppointment(String apptId, boolean accept) {
        return db.runTransaction(trx -> {
            DocumentReference apptRef = db.collection("appointments").document(apptId);
            DocumentSnapshot snap = trx.get(apptRef);

            if (!snap.exists()) {
                throw new RuntimeException("Appointment not found: " + apptId);
            }

            String patientId = snap.getString("patientId");
            String doctorId = snap.getString("doctorId");
            Timestamp slot = snap.getTimestamp("slot");

            if (patientId == null || doctorId == null || slot == null) {
                throw new RuntimeException("Invalid appointment data");
            }

            // Manage share gate for patient docs
            DocumentReference shareRef =
                    db.collection("patientShares").document(patientId)
                            .collection("doctors").document(doctorId);

            if (accept) {
                Map<String, Object> share = new HashMap<>();
                share.put("active", true);
                share.put("updatedAt", FieldValue.serverTimestamp());
                trx.set(shareRef, share, SetOptions.merge());
            } else {
                trx.delete(shareRef);
            }

            // Update appointment status
            Map<String, Object> patch = new HashMap<>();
            // This is correct: "confirmed" is watched by the confirmed-list
            // "cancelled" is watched by neither, so it disappears (which is good)
            patch.put("status", accept ? "confirmed" : "cancelled");
            patch.put("updatedAt", FieldValue.serverTimestamp());
            trx.set(apptRef, patch, SetOptions.merge());

            // Pass data to the next step
            Map<String, Object> out = new HashMap<>();
            out.put("patientId", patientId);
            out.put("accept", accept); // <-- Pass the 'accept' boolean
            out.put("apptId", apptId);
            out.put("slot", slot);
            return out;

        }).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                // Transaction failed, propagate the exception.
                return Tasks.forException(task.getException());
            }

            // The transaction succeeded, now send the notification.
            Map<String, Object> out = task.getResult();
            String patientId = (String) out.get("patientId");
            Timestamp slot = (Timestamp) out.get("slot");
            boolean wasAccepted = (boolean) out.get("accept"); // <-- Get the 'accept' boolean

            String when = (slot != null)
                    ? android.text.format.DateFormat.format("EEE, dd MMM • h:mm a", slot.toDate()).toString()
                    : "your request"; // <-- Cleaner fallback

            String title = wasAccepted ? "Appointment confirmed" : "Appointment declined";
            String body = wasAccepted ? ("Your appointment is confirmed for " + when)
                    : ("Your appointment request was declined.");

            // Return the notification task, which will be chained.
            return notifyUser(patientId, title, body, "appt_status", (String) out.get("apptId"));

        }).continueWith(task -> {
            // --- THIS IS THE FIX ---
            if (!task.isSuccessful()) {
                // Log the error
                Log.e("FirestoreRepo", "Transaction or Notification chain failed", task.getException());

                // RE-THROW the exception to propagate the failure
                throw task.getException();
            }

            // If we got here, everything succeeded.
            return null;
        });
    }



    // Patient: live upcoming (pending+confirmed)
    /* -------------------- Optional: patient-side upcoming listener -------------------- */
    public ListenerRegistration listenPatientUpcoming(String patientId,
                                                      EventListener<QuerySnapshot> listener) {
        // If your patient UI should tolerate more statuses, widen the filter here
        return db.collection("appointments")
                .whereEqualTo("patientId", patientId)
                .whereIn("status", Arrays.asList("pending", "confirmed"))
                .orderBy("slot", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    // Doctor: live pending
    /* -------------------- DOCTOR DASH LISTENERS -------------------- */
    public ListenerRegistration listenDoctorPending(String doctorId,
                                                    EventListener<QuerySnapshot> listener) {
        return db.collection("appointments")
                .whereEqualTo("doctorId", doctorId)
                .whereEqualTo("status", "pending")
                .orderBy("slot", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    // Doctor: live confirmed
    public ListenerRegistration listenDoctorConfirmed(String doctorId,
                                                      EventListener<QuerySnapshot> listener) {
        Timestamp now = Timestamp.now();
        return db.collection("appointments")
                .whereEqualTo("doctorId", doctorId)
                .whereEqualTo("status", "confirmed")
                .whereGreaterThanOrEqualTo("slot", now)
                .orderBy("slot", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }
    public Task<Void> rescheduleAppointment(String apptId, Timestamp newSlot, Timestamp newEnd,
                                            @Nullable String statusIfAny) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("slot", newSlot);
        patch.put("end",  newEnd);
        if (statusIfAny != null) patch.put("status", statusIfAny); // e.g., "pending" or keep "confirmed"
        patch.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection("appointments").document(apptId)
                .update(patch);    // merge, do NOT overwrite
    }
    public Task<DocumentReference> notifyUser(String userId, String title, String body, String type, @Nullable String apptId){
        Map<String,Object> n = new HashMap<>();
        n.put("title", title);
        n.put("body", body);
        n.put("type", type);
        if (apptId != null) n.put("apptId", apptId);
        n.put("read", false);
        n.put("createdAt", FieldValue.serverTimestamp());
        return FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("notifications").add(n);
    }
    public Task<Void> cancelAppointment(String apptId) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("status", "cancelled");
        patch.put("updatedAt", FieldValue.serverTimestamp());

        // We use .update() here which merges the data.
        // If you use .set() with SetOptions.merge(), that's also fine.
        return db.collection("appointments").document(apptId)
                .update(patch);
    }
}
