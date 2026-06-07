package com.jules.adblock;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST_CODE = 100;
    private boolean isVpnActive = false;

    private ImageButton btnShield;
    private View shieldGlow;
    private TextView tvStatus;
    private Button btnIsolation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnShield = findViewById(R.id.btn_shield);
        shieldGlow = findViewById(R.id.shield_glow);
        tvStatus = findViewById(R.id.tv_status);
        btnIsolation = findViewById(R.id.btn_isolation);

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
    }

    private void stopVpn() {
        Intent intent = new Intent(this, AdBlockVpnService.class);
        intent.setAction("STOP");
        startService(intent);
        isVpnActive = false;
        updateUi();
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpn();
        }
    }
}
