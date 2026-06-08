package com.jules.adblock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class AdBlockVpnService extends VpnService {
    private static final String TAG = "AdBlockVpnService";
    private static final String CHANNEL_ID = "AdBlockVpnChannel";
    private ParcelFileDescriptor vpnInterface = null;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        setupNetworkCallback();
    }

    private void setupNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.i(TAG, "Network available, re-evaluating VPN");
                startVpn();
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.i(TAG, "Network lost");
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                Log.i(TAG, "Network capabilities changed, re-evaluating VPN");
                startVpn();
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
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
                .setOngoing(true)
                .build();

        startForeground(1, notification);
        startVpn();
        return START_STICKY;
    }

    private boolean isWifiConnected() {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private boolean isMobileDataConnected() {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private void startVpn() {
        if (vpnInterface != null) {
            stopVpnInternal();
        }

        Builder builder = new Builder();
        builder.setSession("AdBlocker")
               .addAddress("10.0.0.2", 32)
               // Primary: AdGuard DNS for AdBlocking
               .addDnsServer("94.140.14.14")
               .addDnsServer("94.140.15.15")
               // Fallback: Cloudflare DNS
               .addDnsServer("1.1.1.1")
               .addDnsServer("1.0.0.1")
               // Secondary Fallback: Google DNS
               .addDnsServer("8.8.8.8")
               .addDnsServer("8.8.4.4");

        SharedPreferences prefs = getSharedPreferences("adblock_prefs", MODE_PRIVATE);

        Set<String> isolatedApps = new HashSet<>();
        if (isWifiConnected()) {
            Log.i(TAG, "Current Network: WiFi");
            isolatedApps.addAll(prefs.getStringSet("isolated_apps_wifi", new HashSet<>()));
        } else if (isMobileDataConnected()) {
            Log.i(TAG, "Current Network: Mobile Data");
            isolatedApps.addAll(prefs.getStringSet("isolated_apps_mobile", new HashSet<>()));
        } else {
            Log.i(TAG, "No internet connection detected for isolation");
        }

        if (!isolatedApps.isEmpty()) {
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
            // Don't stopSelf() here if we want to wait for network availability
        }
    }

    private void stopVpn() {
        stopVpnInternal();
        stopForeground(true);
        stopSelf();
    }

    private void stopVpnInternal() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close VPN interface", e);
            }
            vpnInterface = null;
        }
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
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        stopVpn();
        super.onDestroy();
    }
}
