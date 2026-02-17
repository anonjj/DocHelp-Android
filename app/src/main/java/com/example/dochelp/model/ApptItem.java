package com.example.dochelp.model;

import com.example.dochelp.R;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class ApptItem {
    @DocumentId public String id;   // Firestore doc id (we’ll also set it manually as a fallback)
    public String status;
    public String patientId;
    public String doctorId;
    public Timestamp slot;
    public String reason;

    public ApptItem() {} // required for Firestore
}
