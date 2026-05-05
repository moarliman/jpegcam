package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * Simple focus position bar — shows where focus is within the motor range.
 * DOF calculations removed along with lens profile system.
 */
public class AdvancedFocusMeterView extends View {
    private Paint trackPaint, needlePaint, bgPaint, textPaint;

    private float currentRatio      = 0.5f;
    private float currentAperture   = 2.8f;
    private float currentFocalLength = 50.0f;
    private float currentCoC        = 0.020f;

    public AdvancedFocusMeterView(Context context) {
        super(context);

        trackPaint = new Paint();
        trackPaint.setColor(Color.argb(150, 100, 100, 100));
        trackPaint.setStrokeWidth(4);

        needlePaint = new Paint();
        needlePaint.setColor(Color.WHITE);
        needlePaint.setStrokeWidth(6);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        bgPaint = new Paint();
        bgPaint.setColor(Color.DKGRAY);
        bgPaint.setStrokeWidth(4);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(18);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(4, 0, 0, Color.BLACK);
    }

    public void update(float ratio, float aperture, float focalLength, float coc) {
        this.currentRatio       = ratio;
        this.currentAperture    = aperture;
        this.currentFocalLength = focalLength;
        this.currentCoC         = coc;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        int pad = 40;
        int y = h / 2 + 20;

        canvas.drawLine(pad, y, w - pad, y, bgPaint);
        canvas.drawLine(pad, y, w - pad, y, trackPaint);

        float needleX = pad + ((w - pad * 2) * currentRatio);
        canvas.drawCircle(needleX, y, 12, needlePaint);

        String sensorFormat = (currentCoC >= 0.030f) ? "FF" : ((currentCoC <= 0.011f) ? "1\"" : "APS-C");
        String label = String.format("[ %.0fmm  f/%.1f  %s ]", currentFocalLength, currentAperture, sensorFormat);
        canvas.drawText(label, w / 2, y - 30, textPaint);
    }
}
