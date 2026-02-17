package com.example.dochelp.model;

import com.example.dochelp.R;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class NoteItem {
    @DocumentId public String id;
    public String authorId;
    public String authorRole; // "doctor" | "patient"
    public String text;
    public Timestamp createdAt;

    public NoteItem() {}
}

