package com.example.dochelp.session;

import com.example.dochelp.R;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class UserSession {
    private static final UserSession I = new UserSession();
    public static UserSession get() { return I; }

    public String uid, name, photoUrl, email, role;

    private boolean loaded = false;
    private boolean loading = false;
    @Nullable private String loadedForUid = null;

    public interface Ready { void ok(); void err(Exception e); }

    private final List<Ready> pending = new ArrayList<>();

    public synchronized void clear() {
        uid = name = photoUrl = email = role = null;
        loaded = false;
        loading = false;
        loadedForUid = null;
        pending.clear();
    }

    public void load(Ready cb) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            // no navigation here — just callback
            if (cb != null) cb.err(new IllegalStateException("Not signed in"));
            return;
        }

        final String currentUid = auth.getUid();

        synchronized (this) {
            // already loaded for this uid
            if (loaded && currentUid != null && currentUid.equals(loadedForUid)) {
                if (cb != null) cb.ok();
                return;
            }
            // another load is in-flight — queue the callback
            if (loading) {
                if (cb != null) pending.add(cb);
                return;
            }
            // start new load
            loading = true;
            if (cb != null) pending.add(cb);
        }

        // fill quick fields from Auth
        uid = currentUid;
        email = auth.getCurrentUser().getEmail();
        photoUrl = auth.getCurrentUser().getPhotoUrl() != null
                ? auth.getCurrentUser().getPhotoUrl().toString()
                : null;

        FirebaseFirestore.getInstance().collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(this::onDoc)
                .addOnFailureListener(this::onErr);
    }

    private void onDoc(DocumentSnapshot doc) {
        synchronized (this) {
            name = doc.getString("name");
            role = doc.getString("role");
            loaded = true;
            loading = false;
            loadedForUid = uid;

            for (Ready r : pending) r.ok();
            pending.clear();
        }
    }

    private void onErr(Exception e) {
        synchronized (this) {
            loading = false;
            for (Ready r : pending) r.err(e);
            pending.clear();
        }
    }
}
