package com.khellab.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * Custom View drawn on top of the camera preview. It draws the pose skeleton
 * (green dots + white bones), highlights the hip point used for the jump test in yellow,
 * and draws the standing "baseline" line once the jump test is calibrated.
 */
public class PoseOverlayView extends View {

    // Pairs of landmarks that are joined by a line ("bones") to draw a stick figure
    private static final int[][] BONES = {
            {PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER},
            {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW},
            {PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST},
            {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW},
            {PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST},
            {PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP},
            {PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP},
            {PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP},
            {PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE},
            {PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE},
            {PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE},
            {PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE},
    };

    private Pose pose;
    private int imageWidth = 1;     // size of the analysed image, already rotated upright
    private int imageHeight = 1;
    private boolean mirror = false; // front camera preview is mirrored, so we mirror too
    private float baselineY = -1;   // standing hip level in image pixels (-1 = not set)
    private float thresholdY = 0;   // how far above the baseline counts as "in the air"

    private final Paint dotPaint = new Paint();
    private final Paint bonePaint = new Paint();
    private final Paint hipPaint = new Paint();
    private final Paint linePaint = new Paint();

    // This two-argument constructor is the one Android calls when it inflates the view from XML
    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dotPaint.setColor(Color.GREEN);
        dotPaint.setStyle(Paint.Style.FILL);

        bonePaint.setColor(Color.WHITE);
        bonePaint.setStrokeWidth(6f);

        hipPaint.setColor(Color.YELLOW);
        hipPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(4f);
    }

    public void updatePose(Pose newPose, int imgWidth, int imgHeight, boolean mirrored) {
        pose = newPose;
        imageWidth = imgWidth;
        imageHeight = imgHeight;
        mirror = mirrored;
        postInvalidate();   // asks Android to call onDraw() again soon
    }

    public void setBaseline(float baselineHipY, float threshold) {
        baselineY = baselineHipY;
        thresholdY = threshold;
        postInvalidate();
    }

    public void clear() {
        pose = null;
        baselineY = -1;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pose == null) return;

        // PreviewView uses "fill centre": the image is scaled up until it fills the view and the
        // overflowing part is cropped. We use exactly the same scale and offset for the dots.
        float scale = Math.max((float) getWidth() / imageWidth, (float) getHeight() / imageHeight);
        float offsetX = (getWidth() - imageWidth * scale) / 2f;
        float offsetY = (getHeight() - imageHeight * scale) / 2f;

        // Bones first, so the dots are drawn on top of them
        for (int[] bone : BONES) {
            PoseLandmark a = pose.getPoseLandmark(bone[0]);
            PoseLandmark b = pose.getPoseLandmark(bone[1]);
            if (a == null || b == null) continue;
            canvas.drawLine(mapX(a.getPosition().x, scale, offsetX), mapY(a.getPosition().y, scale, offsetY),
                    mapX(b.getPosition().x, scale, offsetX), mapY(b.getPosition().y, scale, offsetY),
                    bonePaint);
        }

        for (PoseLandmark landmark : pose.getAllPoseLandmarks()) {
            float x = mapX(landmark.getPosition().x, scale, offsetX);
            float y = mapY(landmark.getPosition().y, scale, offsetY);
            canvas.drawCircle(x, y, 8f, dotPaint);
        }

        // Yellow dot at the middle of the hips: this is the point the jump test tracks
        PoseLandmark lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        if (lh != null && rh != null) {
            float hx = (lh.getPosition().x + rh.getPosition().x) / 2f;
            float hy = (lh.getPosition().y + rh.getPosition().y) / 2f;
            canvas.drawCircle(mapX(hx, scale, offsetX), mapY(hy, scale, offsetY), 16f, hipPaint);
        }

        // Cyan line = standing hip level; the hip must rise above the dashed-looking upper line to count
        if (baselineY > 0) {
            float y1 = mapY(baselineY, scale, offsetY);
            float y2 = mapY(baselineY - thresholdY, scale, offsetY);
            canvas.drawLine(0, y1, getWidth(), y1, linePaint);
            for (float x = 0; x < getWidth(); x += 40) {
                canvas.drawLine(x, y2, x + 20, y2, linePaint);
            }
        }
    }

    private float mapX(float x, float scale, float offsetX) {
        float viewX = x * scale + offsetX;
        return mirror ? getWidth() - viewX : viewX;
    }

    private float mapY(float y, float scale, float offsetY) {
        return y * scale + offsetY;
    }
}
