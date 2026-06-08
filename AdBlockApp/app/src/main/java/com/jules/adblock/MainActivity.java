package com.jules.adblock;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST_CODE = 100;
    private boolean isVpnActive = false;

    private ImageButton btnShield;
    private View shieldGlow;
    private TextView tvStatus;
    private Button btnIsolation;

    // Stats views
    private TextView tvAdsCount;
    private TextView tvTrackersCount;
    private TextView tvDataSaved;

    private Handler statsHandler = new Handler(Looper.getMainLooper());
    private int adsCount = 0;
    private int trackersCount = 0;
    private double dataSaved = 0.0;
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnShield = findViewById(R.id.btn_shield);
        shieldGlow = findViewById(R.id.shield_glow);
        tvStatus = findViewById(R.id.tv_status);
        btnIsolation = findViewById(R.id.btn_isolation);

        tvAdsCount = findViewById(R.id.tv_ads_count);
        tvTrackersCount = findViewById(R.id.tv_trackers_count);
        tvDataSaved = findViewById(R.id.tv_data_saved);

        btnShield.setOnClickListener(v -> {
            if (isVpnActive) {
                stopVpn();
            } else {
                prepareVpn();
            }
        });

        btnIsolation.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppIsolationActivity.class);
            startActivity(intent);
        });

        updateUi();
    }

    private void prepareVpn() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            startVpn();
        }
    }

    private void startVpn() {
        Intent intent = new Intent(this, AdBlockVpnService.class);
        startService(intent);
        isVpnActive = true;
        updateUi();
        startStatsSimulation();
    }

    private void stopVpn() {
        Intent intent = new Intent(this, AdBlockVpnService.class);
        intent.setAction("STOP");
        startService(intent);
        isVpnActive = false;
        updateUi();
        stopStatsSimulation();
    }

    private void startStatsSimulation() {
        statsHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isVpnActive) {
                    adsCount += random.nextInt(3);
                    trackersCount += random.nextInt(2);
                    dataSaved += random.nextDouble() * 0.5;

                    updateStatsUi();
                    statsHandler.postDelayed(this, 3000);
                }
            }
        }, 3000);
    }

    private void stopStatsSimulation() {
        statsHandler.removeCallbacksAndMessages(null);
    }

    private void updateStatsUi() {
        tvAdsCount.setText(String.valueOf(adsCount));
        tvTrackersCount.setText(String.valueOf(trackersCount));
        tvDataSaved.setText(String.format("%.1f", dataSaved));
    }

    private void updateUi() {
        if (isVpnActive) {
            btnShield.setBackgroundResource(R.drawable.shield_button_active);
            btnShield.setImageResource(R.drawable.ic_shield_on);
            shieldGlow.setBackgroundResource(R.drawable.shield_glow_active);
            tvStatus.setText(R.string.status_active);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.active_green));
        } else {
            btnShield.setBackgroundResource(R.drawable.shield_button_inactive);
            btnShield.setImageResource(R.drawable.ic_shield_off);
            shieldGlow.setBackgroundResource(R.drawable.shield_glow_inactive);
            tvStatus.setText(R.string.status_inactive);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.inactive_red));
        }
        updateStatsUi();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpn();
        }
    }

    @Override
    protected void onDestroy() {
        stopStatsSimulation();
        super.onDestroy();
    }
}
