package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button btn = findViewById(R.id.btnGetStarted);

        // Setting a click listener for the button "Get started"
        btn.setOnClickListener(v -> {
            Intent i = new Intent(WelcomeActivity.this, SignInActivity.class);
            startActivity(i);
            finish();
        });

        // --- Move the permission check inside onCreate ---
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Request the permission
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
}
