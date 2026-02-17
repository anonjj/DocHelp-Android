package com.example.dochelp.activity;

import com.example.dochelp.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class SectorSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sector_selection);

        findViewById(R.id.btnResidential).setOnClickListener(v -> selectSector("Residential"));
        findViewById(R.id.btnCorporate).setOnClickListener(v -> selectSector("Corporate"));
        findViewById(R.id.btnHospitality).setOnClickListener(v -> selectSector("Hospitality"));
    }

    private void selectSector(String sector) {
        SharedPreferences prefs = getSharedPreferences("DocHelpPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("selected_sector", sector).apply();

        // Launch WelcomeActivity as the start of the flow
        Intent intent = new Intent(this, WelcomeActivity.class);
        startActivity(intent);
        finish();
    }
}