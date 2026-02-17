package com.example.dochelp.repository;

import com.example.dochelp.R;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AuthRepo {
    private static final AuthRepo I = new AuthRepo();
    public static AuthRepo get() { return I; }
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Cb { void ok(); void err(Exception e); }

    // Create user + save profile to Firestore
    public void registerPatient(String name, String email, String pass,
                                String dob, String blood, String allergies, String meds, Cb cb) {
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    String uid = r.getUser().getUid();
                    Map<String, Object> p = new HashMap<>();
                    p.put("role","patient");
                    p.put("name",name); p.put("email",email);
                    p.put("dob",dob); p.put("blood",blood);
                    p.put("allergies",allergies); p.put("meds",meds);
                    p.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("users").document(uid).set(p)
                            .addOnSuccessListener(x -> cb.ok())
                            .addOnFailureListener(cb::err);
                })
                .addOnFailureListener(cb::err);
    }

    public void registerDoctor(String name, String email, String pass,
                               String regNo, String authority, String specialty,
                               @Nullable String credentialPath, Cb cb) {
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    String uid = r.getUser().getUid();
                    Map<String,Object> d = new HashMap<>();
                    d.put("role","doctor");
                    d.put("name",name); d.put("email",email);
                    d.put("regNo",regNo); d.put("authority",authority);
                    d.put("specialty",specialty);
                    d.put("verified",false);
                    if (credentialPath != null) d.put("credentialPath", credentialPath);
                    d.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("users").document(uid).set(d)
                            .addOnSuccessListener(x -> cb.ok())
                            .addOnFailureListener(cb::err);
                })
                .addOnFailureListener(cb::err);
    }

    // Login then read role and return it
    public void signIn(String email, String pass, java.util.function.Consumer<String> onRole, Cb cb) {
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> db.collection("users").document(r.getUser().getUid())
                        .get().addOnSuccessListener(doc -> {
                            if (doc.exists()) onRole.accept(doc.getString("role"));
                            cb.ok();
                        }).addOnFailureListener(cb::err))
                .addOnFailureListener(cb::err);
    }

    public void signOut(){ auth.signOut(); }
}


