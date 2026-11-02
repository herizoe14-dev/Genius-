package com.genius.echos.android;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.genius.echos.EchosGame;
import com.genius.echos.Platform;

public class AndroidLauncher extends AndroidApplication implements Platform {
    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useWakelock = true;      // l'écran reste allumé pendant le jeu
        config.useAccelerometer = false;
        config.useCompass = false;
        initialize(new EchosGame(this), config);
    }

    /** Sans ce verrou, beaucoup de téléphones ignorent les paquets de diffusion UDP (découverte des parties). */
    @Override
    public synchronized void lanStart() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return;
            if (multicastLock == null) {
                multicastLock = wm.createMulticastLock("echos-lan");
                multicastLock.setReferenceCounted(false);
            }
            if (!multicastLock.isHeld()) multicastLock.acquire();
            if (wifiLock == null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "echos-wifi");
                wifiLock.setReferenceCounted(false);
            }
            if (!wifiLock.isHeld()) wifiLock.acquire();
        } catch (Exception ignored) {
        }
    }

    @Override
    public synchronized void lanStop() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        lanStop();
        super.onDestroy();
    }
}
