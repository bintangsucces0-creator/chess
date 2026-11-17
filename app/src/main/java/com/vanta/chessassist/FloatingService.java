package com.vanta.chessassist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingService extends Service {

    private static final String CHANNEL_ID   = "chess_overlay";
    private static final String CHANNEL_NAME = "Chess Assist Overlay";

    // WindowManager
    private WindowManager wm;
    private View controlView;
    private OverlayArrowView arrowView;
    private WindowManager.LayoutParams controlParams;
    private WindowManager.LayoutParams arrowParams;

    // Control panel views
    private TextView tvEval, tvBestMove, tvLine, tvStatus;
    private Button btnAnalyze, btnAuto, btnClose;

    // MediaProjection
    private MediaProjectionManager mpm;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenW, screenH, screenDpi;

    // Analysis
    private final BoardDetector   detector = new BoardDetector();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private boolean autoMode = false;
    private BoardDetector.BoardResult lastBoard = null;

    private final Runnable autoRunnable = new Runnable() {
        @Override public void run() {
            if (autoMode) {
                doCapture();
                mainHandler.postDelayed(this, 3200);
            }
        }
    };

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        wm  = (WindowManager) getSystemService(WINDOW_SERVICE);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        getDisplayMetrics();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification());

        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data    = intent.getParcelableExtra("data");
        if (resultCode != 0 && data != null) {
            mediaProjection = mpm.getMediaProjection(resultCode, data);
            setupImageReader();
        }

        inflateOverlay();
        inflateArrow();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(autoRunnable);
        if (controlView != null) try { wm.removeView(controlView); } catch (Exception ignored) {}
        if (arrowView   != null) try { wm.removeView(arrowView);   } catch (Exception ignored) {}
        if (virtualDisplay   != null) virtualDisplay.release();
        if (mediaProjection  != null) mediaProjection.stop();
        if (imageReader      != null) imageReader.close();
        executor.shutdown();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // ── overlay construction ─────────────────────────────────────────────────

    private void inflateOverlay() {
        controlView = LayoutInflater.from(this).inflate(R.layout.overlay_control, null);

        tvEval     = controlView.findViewById(R.id.tvEval);
        tvBestMove = controlView.findViewById(R.id.tvBestMove);
        tvLine     = controlView.findViewById(R.id.tvLine);
        tvStatus   = controlView.findViewById(R.id.tvStatus);
        btnAnalyze = controlView.findViewById(R.id.btnAnalyze);
        btnAuto    = controlView.findViewById(R.id.btnAuto);
        btnClose   = controlView.findViewById(R.id.btnClose);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        controlParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        controlParams.gravity = Gravity.TOP | Gravity.START;
        controlParams.x = 20;
        controlParams.y = 120;

        wm.addView(controlView, controlParams);
        attachControlListeners();
    }

    private void inflateArrow() {
        arrowView = new OverlayArrowView(this);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        arrowParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        wm.addView(arrowView, arrowParams);
    }

    // ── drag control panel ───────────────────────────────────────────────────

    private void attachControlListeners() {
        final int[] initPos = {0, 0};
        final float[] touchStart = {0, 0};
        final boolean[] dragging = {false};

        View dragHandle = controlView.findViewById(R.id.dragHandle);
        dragHandle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initPos[0]    = controlParams.x;
                    initPos[1]    = controlParams.y;
                    touchStart[0] = event.getRawX();
                    touchStart[1] = event.getRawY();
                    dragging[0]   = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(event.getRawX() - touchStart[0]);
                    int dy = (int)(event.getRawY() - touchStart[1]);
                    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) dragging[0] = true;
                    if (dragging[0]) {
                        controlParams.x = initPos[0] + dx;
                        controlParams.y = initPos[1] + dy;
                        wm.updateViewLayout(controlView, controlParams);
                    }
                    return true;
            }
            return false;
        });

        btnAnalyze.setOnClickListener(v -> doCapture());

        btnAuto.setOnClickListener(v -> {
            autoMode = !autoMode;
            int tint = autoMode ? 0xFF238636 : 0xFF30363D;
            int text = autoMode ? 0xFFFFFFFF : 0xFF8B949E;
            btnAuto.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(tint));
            btnAuto.setTextColor(text);
            if (autoMode) mainHandler.post(autoRunnable);
            else          mainHandler.removeCallbacks(autoRunnable);
            setStatus(autoMode ? "auto on" : "auto off");
        });

        btnClose.setOnClickListener(v -> stopSelf());
    }

    // ── screen capture ───────────────────────────────────────────────────────

    private void setupImageReader() {
        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ChessCapture", screenW, screenH, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, null
        );
    }

    private void doCapture() {
        if (imageReader == null) {
            setStatus("no capture"); return;
        }
        setStatus("scanning…");
        executor.execute(() -> {
            Bitmap bmp = grabBitmap();
            if (bmp == null) { mainHandler.post(() -> setStatus("capture fail")); return; }

            BoardDetector.BoardResult board = detector.detect(bmp);
            bmp.recycle();

            if (!board.valid) {
                mainHandler.post(() -> {
                    setStatus("board not found");
                    arrowView.clearArrow();
                });
                return;
            }

            lastBoard = board;
            String fen = board.fen;
            mainHandler.post(() -> setStatus("fen ok · querying…"));

            LichessClient.evalSync(fen, new LichessClient.Callback() {
                @Override
                public void onResult(String uci, String eval, String line) {
                    mainHandler.post(() -> {
                        tvEval.setText("Eval: " + eval);
                        tvBestMove.setText("Best: " + LichessClient.formatUci(uci));
                        tvLine.setText(line);
                        setStatus("ok");
                        drawArrow(uci, board);
                    });
                }
                @Override
                public void onError(String reason) {
                    mainHandler.post(() -> {
                        setStatus(reason);
                        arrowView.clearArrow();
                    });
                }
            });
        });
    }

    private Bitmap grabBitmap() {
        try (Image image = imageReader.acquireLatestImage()) {
            if (image == null) return null;
            Image.Plane[] planes   = image.getPlanes();
            ByteBuffer    buffer   = planes[0].getBuffer();
            int pixelStride        = planes[0].getPixelStride();
            int rowStride          = planes[0].getRowStride();
            int rowPadding         = rowStride - pixelStride * screenW;

            Bitmap raw = Bitmap.createBitmap(
                screenW + rowPadding / pixelStride,
                screenH, Bitmap.Config.ARGB_8888);
            raw.copyPixelsFromBuffer(buffer);

            Bitmap crop = Bitmap.createBitmap(raw, 0, 0, screenW, screenH);
            raw.recycle();
            return crop;
        } catch (Exception e) {
            return null;
        }
    }

    // ── arrow drawing ─────────────────────────────────────────────────────────

    private void drawArrow(String uci, BoardDetector.BoardResult board) {
        if (uci == null || uci.length() < 4) { arrowView.clearArrow(); return; }
        float[] coords = BoardDetector.uciToPixels(
            uci, board.boardRect, board.squareSize, board.boardFlipped);
        if (coords == null) { arrowView.clearArrow(); return; }
        arrowView.setArrow(coords[0], coords[1], coords[2], coords[3]);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void getDisplayMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW   = dm.widthPixels;
        screenH   = dm.heightPixels;
        screenDpi = dm.densityDpi;
    }

    private void setStatus(String msg) {
        mainHandler.post(() -> { if (tvStatus != null) tvStatus.setText(msg); });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Chess Assist overlay service");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chess Assist aktif")
            .setContentText("Overlay berjalan — tap untuk buka")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }
}
