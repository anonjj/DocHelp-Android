package com.example.dochelp.summarizer;

import com.example.dochelp.R;
import com.example.dochelp.BuildConfig;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hugging Face Summarization Helper
 * Uses the DistilBART model (sshleifer/distilbart-cnn-12-6)
 * to summarize text via the HF Inference API.
 */
public class HFSummarizer {

    private static final String TAG = "HFSummarizer";
    private static final String MODEL_ID = "facebook/bart-large-cnn";
    private static final String HF_API_KEY = BuildConfig.HF_API_KEY; // from local.properties

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // --- Interface for callback ---
    public interface SummaryCallback {
        void onSuccess(String summary);
        void onError(String errorMessage);
    }

    /**
     * Summarizes a given input text using Hugging Face API asynchronously.
     * @param inputText The text to summarize.
     * @param callback  The callback to receive the result or error.
     */
    public void summarizeText(String inputText, SummaryCallback callback) {
        executor.execute(() -> {
            try {
                String result = performRequest(inputText);
                handler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "Summarization failed", e);
                handler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // --- Actual HTTP POST Request ---
    private String performRequest(String inputText) throws IOException {
        URL url = new URL("https://router.huggingface.co/hf-inference/models/" + MODEL_ID);        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + HF_API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        try {
            body.put("inputs", inputText);
        } catch (Exception e) {
            throw new IOException("Failed to create JSON body", e);
        }

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes());
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        if (code != 200) {
            throw new IOException("HF API error " + code + ": " + sb.toString());
        }

        try {
            JSONArray arr = new JSONArray(sb.toString());
            JSONObject obj = arr.getJSONObject(0);
            return obj.getString("summary_text");
        } catch (Exception e) {
            throw new IOException("Failed to parse response: " + sb, e);
        }
    }
}
