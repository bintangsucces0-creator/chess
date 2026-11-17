# Chess Assist Pro

Floating overlay + live screen analysis untuk chess.com.

## Cara pakai

1. Install APK → buka Chess Assist Pro
2. Tap **Grant Overlay Permission** → allow di Settings
3. Tap **Mulai Overlay** → izinkan screen capture
4. Aplikasi otomatis buka chess.com
5. Panel kecil muncul di atas layar — bisa di-drag
6. Tap **SCAN** satu kali, atau **AUTO** untuk update tiap 3 detik
7. Panah hijau muncul di papan menunjuk langkah terbaik

## Cara generate APK di GitHub

1. Buat repo baru di github.com (kosong, Public)
2. Upload semua isi folder ini ke repo → Commit
3. Buka tab **Actions** → **Build Debug APK** → **Run workflow**
4. Tunggu ~4 menit → download artifact **ChessAssistPro-debug**
5. Extract → install `app-debug.apk`

## Arsitektur

```
MainActivity       — permission flow, launch chess.com app
FloatingService    — foreground service, WindowManager overlay
OverlayArrowView   — transparent canvas, gambar panah
BoardDetector      — analisis bitmap screenshot, deteksi FEN
LichessClient      — Lichess Cloud Eval API (gratis, no key)
```

## Catatan piece detection

Deteksi lokasi papan & warna bidak (putih/hitam): akurat.
Deteksi *jenis* bidak (pion vs kuda vs menteri dll): approximate —
semua ditampilkan sebagai P/p. Untuk akurasi penuh butuh TFLite.
Eval dari Lichess tetap berguna karena didasarkan pada posisi nyata.

## Min SDK: 23 (Android 6+)
