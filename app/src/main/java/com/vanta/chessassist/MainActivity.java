package com.vanta.chessassist;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_MEDIA_PROJ = 1001;

    private Button btnOverlay, btnStart, btnOpenChess, btnStop;
    private TextView tvOverlayStatus;
    private MediaProjectionManager mpm;

    // MediaProjection result launcher
    private final ActivityResultLauncher<Intent> projLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                launchService(result.getResultCode(), result.getData());
            } else {
                tvOverlayStatus.setText("⬤  screen capture ditolak");
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        btnOverlay    = findViewById(R.id.btnOverlay);
        btnStart      = findViewById(R.id.btnStart);
        btnOpenChess  = findViewById(R.id.btnOpenChess);
        btnStop       = findViewById(R.id.btnStop);
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus);

        btnOverlay.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                tvOverlayStatus.setText("⬤  grant overlay dulu!");
                return;
            }
            requestNotificationPermission();
            projLauncher.launch(mpm.createScreenCaptureIntent());
        });

        btnOpenChess.setOnClickListener(v -> openChessApp());

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingService.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this)) {
            tvOverlayStatus.setText("⬤  overlay granted");
            tvOverlayStatus.setTextColor(0xFF3FB950);
            btnOverlay.setEnabled(false);
            btnOverlay.setAlpha(0.5f);
        } else {
            tvOverlayStatus.setText("⬤  belum di-grant");
            tvOverlayStatus.setTextColor(0xFFf85149);
            btnOverlay.setEnabled(true);
            btnOverlay.setAlpha(1f);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 99);
        }
    }

    private void launchService(int resultCode, Intent data) {
        Intent service = new Intent(this, FloatingService.class);
        service.putExtra("resultCode", resultCode);
        service.putExtra("data", data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        openChessApp(); // go straight to chess.com after starting
    }

    private void openChessApp() {
        // Try native chess.com app first
        Intent chess = getPackageManager().getLaunchIntentForPackage("com.chess");
        if (chess != null) {
            chess.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chess);
        } else {
            // Fall back to Play Store
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.chess")));
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.chess")));
            }
        }
    }
}
