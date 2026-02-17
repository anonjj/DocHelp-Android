package com.example.dochelp.activity;

import com.example.dochelp.R;
import com.example.dochelp.summarizer.HFSummarizer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class UploadDocsActivity extends AppCompatActivity {

    private static final String TAG = "UploadDocsActivity";
    private Uri selectedUri;
    private ProgressBar progressBar;
    private Button btnPick;
    private Button btnProcess;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_docs);

        btnPick = findViewById(R.id.btnPick);
        btnProcess = findViewById(R.id.btnProcess);
        progressBar = findViewById(R.id.progressBar);

        setProcessing(false);
        btnProcess.setEnabled(false);

        // File picker launcher
        ActivityResultLauncher<String> filePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedUri = uri;
                        Toast.makeText(this, "Selected: " + getFileName(uri), Toast.LENGTH_SHORT).show();
                        btnProcess.setEnabled(true);
                    }
                });

        btnPick.setOnClickListener(v -> filePicker.launch("image/*"));

        btnProcess.setOnClickListener(v -> {
            if (selectedUri == null) {
                Toast.makeText(this, "Pick a document first", Toast.LENGTH_SHORT).show();
                return;
            }
            processUpload(selectedUri);
        });
    }

    private void processUpload(Uri uri) {
        try {
            setProcessing(true);

            InputStream is = getContentResolver().openInputStream(uri);
            byte[] bytes = readAllBytes(is);
            String hash = computeSHA256(bytes);
            String fileName = getFileName(uri);

            // Check if this document already exists
            checkIfExists(hash, uri, fileName);

        } catch (Exception e) {
            setProcessing(false);
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkIfExists(String hash, Uri uri, String fileName) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            setProcessing(false);
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid)
                .collection("summaries")
                .whereEqualTo("fileHash", hash)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        setProcessing(false);
                        Toast.makeText(this, "This document already exists!", Toast.LENGTH_SHORT).show();
                    } else {
                        extractTextWithMLKit(uri, hash, fileName);
                    }
                })
                .addOnFailureListener(e -> {
                    setProcessing(false);
                    Toast.makeText(this, "Check failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // 🔍 OCR using ML Kit
    private void extractTextWithMLKit(Uri imageUri, String fileHash, String fileName) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            com.google.mlkit.vision.text.TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        String extractedText = result.getText();
                        if (extractedText.trim().isEmpty()) {
                            setProcessing(false);
                            Toast.makeText(this, "No text found in image.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        summarizeWithHuggingFace(extractedText, fileHash, fileName);
                    })
                    .addOnFailureListener(e -> {
                        setProcessing(false);
                        Toast.makeText(this, "OCR failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });

        } catch (Exception e) {
            setProcessing(false);
            Toast.makeText(this, "Image read error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 🧠 Summarize extracted text using Hugging Face API
    private void summarizeWithHuggingFace(String extractedText, String fileHash, String fileName) {
        HFSummarizer summarizer = new HFSummarizer();

        summarizer.summarizeText(extractedText, new HFSummarizer.SummaryCallback() {
            @Override
            public void onSuccess(String summary) {
                saveDocSummaryToFirestore(summary, fileHash, fileName);
            }

            @Override
            public void onError(String errorMessage) {
                setProcessing(false);
                Toast.makeText(UploadDocsActivity.this, "Summarization failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    // 💾 Save summary to Firestore
    private void saveDocSummaryToFirestore(String summary, String hash, String fileName) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> docData = new HashMap<>();
        docData.put("fileName", fileName);
        docData.put("fileHash", hash);
        docData.put("summary", summary.trim());
        docData.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid).collection("summaries")
                .add(docData)
                .addOnSuccessListener(ref -> {
                    setProcessing(false);
                    ScrollView scroll = new ScrollView(this);
                    TextView tv = new TextView(this);
                    tv.setText(summary);
                    tv.setPadding(40, 20, 40, 20);
                    scroll.addView(tv);

                    new AlertDialog.Builder(this)
                            .setTitle("Summary Generated ✅")
                            .setMessage(summary)
                            .setPositiveButton("Save", (dlg, which) -> {
                                Toast.makeText(this, "Summary saved successfully!", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Discard", (dlg, which) -> {
                                ref.delete();
                                Toast.makeText(this, "Summary discarded.", Toast.LENGTH_SHORT).show();
                            })
                            .show();
                })
                .addOnFailureListener(e -> {
                    setProcessing(false);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setProcessing(boolean on) {
        if (progressBar != null) progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (btnProcess != null) btnProcess.setEnabled(!on && selectedUri != null);
        if (btnPick != null) btnPick.setEnabled(!on);
    }

    // --- Utility Methods ---
    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        is.close();
        return buffer.toByteArray();
    }

    private String computeSHA256(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return (result == null) ? "unknown_file" : result;
    }
}
