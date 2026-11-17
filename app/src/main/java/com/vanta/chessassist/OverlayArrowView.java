package com.vanta.chessassist;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class OverlayArrowView extends View {

    private float fromX, fromY, toX, toY;
    private boolean visible = false;

    private final Paint linePaint;
    private final Paint glowPaint;
    private final Paint fillPaint;
    private final Paint dotPaint;

    public OverlayArrowView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // needed for BlurMaskFilter

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#CC00E676"));
        linePaint.setStrokeWidth(14f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(Color.parseColor("#6600E676"));
        glowPaint.setStrokeWidth(30f);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setMaskFilter(new BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL));

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.parseColor("#CC00E676"));
        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.parseColor("#AAFFE082"));
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setArrow(float fx, float fy, float tx, float ty) {
        fromX = fx; fromY = fy;
        toX   = tx; toY   = ty;
        visible = true;
        postInvalidate();
    }

    public void clearArrow() {
        visible = false;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!visible) return;

        // Shorten line to leave room for arrowhead
        float dx = toX - fromX;
        float dy = toY - fromY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 10f) return;

        float ux = dx / len;
        float uy = dy / len;
        float arrowLen  = Math.min(50f, len * 0.25f);
        float arrowHalf = arrowLen * 0.6f;

        // Line end (shortened)
        float lx = toX - ux * arrowLen;
        float ly = toY - uy * arrowLen;

        // Glow pass
        canvas.drawLine(fromX, fromY, lx, ly, glowPaint);

        // Main line
        canvas.drawLine(fromX, fromY, lx, ly, linePaint);

        // Arrowhead triangle
        Path head = new Path();
        head.moveTo(toX, toY);
        head.lineTo(lx + arrowHalf * uy, ly - arrowHalf * ux);
        head.lineTo(lx - arrowHalf * uy, ly + arrowHalf * ux);
        head.close();

        canvas.drawPath(head, glowPaint);
        canvas.drawPath(head, fillPaint);

        // Origin dot
        canvas.drawCircle(fromX, fromY, 14f, dotPaint);
    }
}
