package com.jules.adblock;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;

public class AdBlockVpnService extends VpnService {
    private static final String TAG = "AdBlockVpnService";
    private ParcelFileDescriptor vpnInterface = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        if (vpnInterface != null) {
            return;
        }

        Builder builder = new Builder();
        builder.setSession("AdBlocker")
               .addAddress("10.0.0.2", 32)
               .addRoute("0.0.0.0", 0)
               // Using AdGuard DNS for ad blocking
               .addDnsServer("94.140.14.14")
               .addDnsServer("94.140.15.15");

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
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
