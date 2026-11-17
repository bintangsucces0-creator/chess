package com.vanta.chessassist;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

/**
 * Detects chess board location and piece positions from a screenshot.
 *
 * Board location: reliable — looks for alternating chess.com square colors.
 * Piece presence: reliable — compares each square to expected empty-square color.
 * Piece color (white/black): reliable — brightness analysis.
 * Piece TYPE: approximate — returns P/p for all; accurate piece-type needs ML.
 *
 * Supported themes: Green (default), Brown, Walnut, Blue.
 */
public class BoardDetector {

    // {light_r, light_g, light_b, dark_r, dark_g, dark_b}
    private static final int[][] THEMES = {
        {238, 238, 210, 118, 150,  86},  // Green (default)
        {240, 217, 181, 181, 136,  99},  // Brown
        {210, 182, 141, 123,  86,  52},  // Walnut
        {222, 227, 230, 140, 162, 173},  // Blue
        {235, 217, 185, 172, 122,  73},  // Tan
    };
    private static final int TOLERANCE = 55;

    public static class BoardResult {
        public RectF   boardRect;
        public float   squareSize;
        public String  fen;
        public boolean valid;
        public boolean boardFlipped; // black at bottom
    }

    public BoardResult detect(Bitmap screen) {
        BoardResult r = new BoardResult();
        RectF bounds = findBoardBounds(screen);
        if (bounds == null) { r.valid = false; return r; }

        r.boardRect  = bounds;
        r.squareSize = bounds.width() / 8f;
        r.valid      = true;

        // Determine orientation: if rank-1 pieces look white at bottom → not flipped
        r.boardFlipped = detectFlipped(screen, bounds, r.squareSize);
        r.fen = extractFen(screen, bounds, r.squareSize, r.boardFlipped);
        return r;
    }

    // ── board location ───────────────────────────────────────────────────────

    private RectF findBoardBounds(Bitmap bmp) {
        for (int[] theme : THEMES) {
            RectF r = findBoardForTheme(bmp, theme);
            if (r != null) return r;
        }
        return null;
    }

    private RectF findBoardForTheme(Bitmap bmp, int[] t) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int minSq = w / 18;
        int maxSq = w / 5;

        for (int sqSize = minSq; sqSize <= maxSq; sqSize += 3) {
            for (int y = h / 10; y < h * 9 / 10 - sqSize * 8; y += sqSize / 3) {
                int bx = findRowStart(bmp, y, sqSize, t, false);
                if (bx < 0) bx = findRowStart(bmp, y, sqSize, t, true);
                if (bx < 0) continue;

                int by = findTopRank(bmp, bx, y, sqSize, t);
                if (by < 0) continue;

                int validRanks = countValidRanks(bmp, bx, by, sqSize, t);
                if (validRanks >= 7) {
                    return new RectF(bx, by, bx + sqSize * 8f, by + sqSize * 8f);
                }
            }
        }
        return null;
    }

    private int findRowStart(Bitmap bmp, int y, int sqSize, int[] t, boolean invertFirst) {
        int w = bmp.getWidth();
        for (int sx = 0; sx <= w - sqSize * 8; sx += 4) {
            int match = 0;
            for (int sq = 0; sq < 8; sq++) {
                int px = sx + sq * sqSize + sqSize / 2;
                if (px >= w) break;
                int pixel = bmp.getPixel(px, y);
                int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
                boolean expectLight = (sq % 2 == 0) != invertFirst;
                boolean hit = expectLight
                    ? colorMatch(r, g, b, t[0], t[1], t[2])
                    : colorMatch(r, g, b, t[3], t[4], t[5]);
                if (hit) match++;
            }
            if (match >= 7) return sx;
        }
        return -1;
    }

    private int findTopRank(Bitmap bmp, int bx, int foundY, int sqSize, int[] t) {
        int topY = foundY;
        for (int y = foundY - sqSize; y >= 0; y -= sqSize) {
            if (rowMatch(bmp, bx, y, sqSize, t) >= 6) topY = y;
            else break;
        }
        return topY;
    }

    private int countValidRanks(Bitmap bmp, int bx, int by, int sqSize, int[] t) {
        int count = 0;
        for (int rank = 0; rank < 8; rank++) {
            int sampleY = by + rank * sqSize + sqSize / 2;
            if (sampleY >= bmp.getHeight()) break;
            if (rowMatch(bmp, bx, sampleY, sqSize, t) >= 6) count++;
        }
        return count;
    }

    private int rowMatch(Bitmap bmp, int bx, int y, int sqSize, int[] t) {
        if (y < 0 || y >= bmp.getHeight()) return 0;
        int match = 0;
        for (int sq = 0; sq < 8; sq++) {
            int px = bx + sq * sqSize + sqSize / 2;
            if (px >= bmp.getWidth()) break;
            int pixel = bmp.getPixel(px, y);
            int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
            boolean expectLight = sq % 2 == 0;
            if (expectLight ? colorMatch(r, g, b, t[0], t[1], t[2])
                            : colorMatch(r, g, b, t[3], t[4], t[5])) match++;
        }
        return match;
    }

    private boolean colorMatch(int r, int g, int b, int er, int eg, int eb) {
        return Math.abs(r - er) <= TOLERANCE
            && Math.abs(g - eg) <= TOLERANCE
            && Math.abs(b - eb) <= TOLERANCE;
    }

    // ── board orientation ────────────────────────────────────────────────────

    private boolean detectFlipped(Bitmap bmp, RectF board, float sqSize) {
        // Sample bottom-left square (a1 if normal, a8 if flipped)
        // If we see white-piece brightness there → not flipped (white starts at bottom)
        float cx = board.left + sqSize / 2f;
        float cy = board.bottom - sqSize / 2f;
        int brightness = avgBrightness(bmp, cx, cy, sqSize * 0.3f);
        // High brightness at bottom-left rank → white pieces → normal orientation
        return brightness < 100; // dark pieces at bottom → flipped
    }

    // ── FEN extraction ───────────────────────────────────────────────────────

    private String extractFen(Bitmap bmp, RectF board, float sqSize, boolean flipped) {
        StringBuilder fen = new StringBuilder();

        for (int rank = 7; rank >= 0; rank--) {
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                int displayFile = flipped ? 7 - file : file;
                int displayRank = flipped ? 7 - rank : rank;

                float cx = board.left + displayFile * sqSize + sqSize / 2f;
                float cy = board.top  + (7 - displayRank) * sqSize + sqSize / 2f;

                char piece = classifySquare(bmp, cx, cy, sqSize, (rank + file) % 2 == 0);

                if (piece == '.') {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) { fen.append(emptyCount); emptyCount = 0; }
                    fen.append(piece);
                }
            }
            if (emptyCount > 0) fen.append(emptyCount);
            if (rank > 0) fen.append('/');
        }

        // Turn detection: defaults to white — FloatingService passes detected turn
        fen.append(" w KQkq - 0 1");
        return fen.toString();
    }

    /**
     * Classifies a square.
     * Returns uppercase for white pieces, lowercase for black, '.' for empty.
     * Piece type accuracy: P/p only (needs ML for exact type).
     */
    private char classifySquare(Bitmap bmp, float cx, float cy, float sqSize,
                                 boolean isLightSq) {
        float radius = sqSize * 0.28f;
        int samples = 0, lightPx = 0, darkPx = 0, midPx = 0;

        for (float dy = -radius; dy <= radius; dy += sqSize * 0.08f) {
            for (float dx = -radius; dx <= radius; dx += sqSize * 0.08f) {
                int px = (int)(cx + dx);
                int py = (int)(cy + dy);
                if (px < 0 || px >= bmp.getWidth() || py < 0 || py >= bmp.getHeight()) continue;

                int pixel = bmp.getPixel(px, py);
                int brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                samples++;
                if (brightness > 210) lightPx++;
                else if (brightness < 60)  darkPx++;
                else midPx++;
            }
        }

        if (samples == 0) return '.';
        float lightR = (float) lightPx / samples;
        float darkR  = (float) darkPx  / samples;

        // Thresholds tuned for chess.com default theme
        if (isLightSq) {
            // Light squares have natural brightness; need stronger signal
            if (lightR > 0.40f) return 'P';  // white piece on light square
            if (darkR  > 0.18f) return 'p';  // black piece on light square
        } else {
            if (lightR > 0.28f) return 'P';  // white piece on dark square
            if (darkR  > 0.25f) return 'p';  // black piece on dark square
        }
        return '.';
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int avgBrightness(Bitmap bmp, float cx, float cy, float radius) {
        long total = 0; int count = 0;
        for (float dy = -radius; dy <= radius; dy += radius / 3) {
            for (float dx = -radius; dx <= radius; dx += radius / 3) {
                int px = (int)(cx + dx), py = (int)(cy + dy);
                if (px < 0 || px >= bmp.getWidth() || py < 0 || py >= bmp.getHeight()) continue;
                int p = bmp.getPixel(px, py);
                total += (Color.red(p) + Color.green(p) + Color.blue(p)) / 3;
                count++;
            }
        }
        return count == 0 ? 128 : (int)(total / count);
    }

    /**
     * Converts UCI move string (e.g. "e2e4") to pixel coordinates on screen.
     * Returns float[4] = {fromX, fromY, toX, toY}
     */
    public static float[] uciToPixels(String uci, RectF board, float sqSize, boolean flipped) {
        if (uci == null || uci.length() < 4) return null;
        int ff = uci.charAt(0) - 'a'; // from file 0-7
        int fr = uci.charAt(1) - '1'; // from rank 0-7
        int tf = uci.charAt(2) - 'a'; // to file
        int tr = uci.charAt(3) - '1'; // to rank

        if (ff < 0 || ff > 7 || fr < 0 || fr > 7 || tf < 0 || tf > 7 || tr < 0 || tr > 7)
            return null;

        if (flipped) {
            ff = 7 - ff; fr = 7 - fr;
            tf = 7 - tf; tr = 7 - tr;
        }

        float fromX = board.left + ff * sqSize + sqSize / 2f;
        float fromY = board.top  + (7 - fr) * sqSize + sqSize / 2f;
        float toX   = board.left + tf * sqSize + sqSize / 2f;
        float toY   = board.top  + (7 - tr) * sqSize + sqSize / 2f;

        return new float[]{fromX, fromY, toX, toY};
    }
}
