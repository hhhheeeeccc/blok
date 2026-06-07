package com.jules.adblock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class AdBlockVpnService extends VpnService {
    private static final String TAG = "AdBlockVpnService";
    private static final String CHANNEL_ID = "AdBlockVpnChannel";
    private ParcelFileDescriptor vpnInterface = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.vpn_notif_title))
                .setContentText(getString(R.string.vpn_notif_text))
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // Temporary icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        if (vpnInterface != null) {
            stopVpn();
        }

        Builder builder = new Builder();
        builder.setSession("AdBlocker")
               .addAddress("10.0.0.2", 32)
               .addDnsServer("94.140.14.14")
               .addDnsServer("94.140.15.15");

        SharedPreferences prefs = getSharedPreferences("adblock_prefs", MODE_PRIVATE);
        Set<String> isolatedApps = prefs.getStringSet("isolated_apps", new HashSet<>());

        if (isolatedApps != null && !isolatedApps.isEmpty()) {
            Log.i(TAG, "Isolating apps: " + isolatedApps.size());
            for (String pkg : isolatedApps) {
                try {
                    builder.addAllowedApplication(pkg);
                } catch (Exception e) {
                    Log.e(TAG, "Could not add app: " + pkg, e);
                }
            }
            // Capture all traffic for these apps to block it
            builder.addRoute("0.0.0.0", 0);
        } else {
            // DNS-only mode: Only capture traffic for our own app to keep VPN active
            // This allows system-wide DNS to work without intercepting other app traffic
            try {
                builder.addAllowedApplication(getPackageName());
            } catch (Exception e) {
                Log.e(TAG, "Could not add self", e);
            }
        }

        try {
            vpnInterface = builder.establish();
            Log.i(TAG, "VPN Interface established");
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish VPN interface", e);
        }
    }

    private void stopVpn() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close VPN interface", e);
            }
            vpnInterface = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AdBlock VPN Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
