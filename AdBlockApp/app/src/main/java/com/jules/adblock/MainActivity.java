package com.jules.adblock;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    private static final int VPN_REQUEST_CODE = 100;
    private static final String VIDEO_URL = "https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260328_115001_bcdaa3b4-03de-47e7-ad63-ae3e392c32d4.mp4";

    private boolean isVpnActive = false;
    private TextureView backgroundVideo;
    private MediaPlayer mediaPlayer;
    private boolean fadingOut = false;
    private ValueAnimator currentFadeAnimator;
    private Handler fadeHandler = new Handler(Looper.getMainLooper());

    private ImageButton btnShield;
    private TextView tvStatusText;
    private TextView btnManifesto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        backgroundVideo = findViewById(R.id.background_video);
        backgroundVideo.setSurfaceTextureListener(this);

        btnShield = findViewById(R.id.btn_shield);
        tvStatusText = findViewById(R.id.tv_status_text);
        btnManifesto = findViewById(R.id.btn_manifesto);

        btnShield.setOnClickListener(v -> {
            if (isVpnActive) {
                stopVpn();
            } else {
                prepareVpn();
            }
        });

        btnManifesto.setOnClickListener(v -> {
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
            tvStatusText.setText(R.string.status_active);
            tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.active_green));
            btnShield.setImageResource(R.drawable.ic_arrow_right);
            btnShield.setRotation(180);
        } else {
            tvStatusText.setText(R.string.status_idle);
            tvStatusText.setTextColor(0x66FFFFFF);
            btnShield.setImageResource(R.drawable.ic_arrow_right);
            btnShield.setRotation(0);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        setupMediaPlayer(new Surface(surface));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        adjustVideoAspect(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        fadeHandler.removeCallbacksAndMessages(null);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            int duration = mediaPlayer.getDuration();
            int current = mediaPlayer.getCurrentPosition();

            // Fade out when 0.55 seconds remain
            if (!fadingOut && duration > 0 && (duration - current) <= 550) {
                startFade(0f, 500);
                fadingOut = true;
            }
        }
    }

    private void setupMediaPlayer(Surface surface) {
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setSurface(surface);
            mediaPlayer.setDataSource(VIDEO_URL);
            mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            mediaPlayer.setVolume(0, 0); // Muted
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                adjustVideoAspect(backgroundVideo.getWidth(), backgroundVideo.getHeight());
                startPlayback();
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                backgroundVideo.setAlpha(0f);
                fadingOut = false;
                fadeHandler.postDelayed(() -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.seekTo(0);
                        startPlayback();
                    }
                }, 100);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            startFade(1f, 500);
        }
    }

    private void startFade(float targetAlpha, int duration) {
        if (currentFadeAnimator != null) {
            currentFadeAnimator.cancel();
        }

        float startAlpha = backgroundVideo.getAlpha();
        currentFadeAnimator = ValueAnimator.ofFloat(startAlpha, targetAlpha);
        currentFadeAnimator.setDuration(duration);
        currentFadeAnimator.addUpdateListener(animation -> {
            backgroundVideo.setAlpha((float) animation.getAnimatedValue());
        });
        currentFadeAnimator.start();
    }

    private void adjustVideoAspect(int viewWidth, int viewHeight) {
        if (mediaPlayer == null || viewWidth == 0 || viewHeight == 0) return;

        int videoWidth = mediaPlayer.getVideoWidth();
        int videoHeight = mediaPlayer.getVideoHeight();
        if (videoWidth == 0 || videoHeight == 0) return;

        double viewAspect = (double) viewHeight / viewWidth;
        double videoAspect = (double) videoHeight / videoWidth;

        Matrix matrix = new Matrix();

        // Object-cover logic
        float scale = Math.max((float) viewWidth / videoWidth, (float) viewHeight / videoHeight);
        float scaledWidth = scale * videoWidth;
        float scaledHeight = scale * videoHeight;

        float dx = (viewWidth - scaledWidth) / 2;
        float dy = (viewHeight - scaledHeight) / 2;

        // Apply the 17% translate-y shift
        float shiftY = viewHeight * 0.17f;

        matrix.setScale(scaledWidth / viewWidth, scaledHeight / viewHeight);
        matrix.postTranslate(dx, dy + shiftY);

        backgroundVideo.setTransform(matrix);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpn();
        }
    }
}
