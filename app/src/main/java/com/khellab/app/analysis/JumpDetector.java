package com.khellab.app.analysis;

/**
 * Milestone 2: detects one vertical jump from the hip position in each camera frame,
 * and calculates jump height from flight time.
 *
 * How it works (a small "state machine"):
 *  1. CALIBRATING - athlete stands still; we average the hip height over 20 frames.
 *                   This average is the "baseline" (standing hip position).
 *  2. READY       - waiting for the jump. When the hip rises clearly above the baseline,
 *                   the athlete has taken off: we remember the take-off time.
 *  3. IN_AIR      - when the hip comes back down to the baseline, the athlete has landed.
 *                   flight time = landing time - take-off time.
 *  4. DONE        - height = g * t^2 / 8  (projectile motion: the body rises for t/2 seconds
 *                   and falls for t/2 seconds, so h = 1/2 * g * (t/2)^2 = g * t^2 / 8).
 *
 * Note: in image coordinates y grows DOWNWARDS, so "hip moves up" means hipY gets SMALLER.
 */
public class JumpDetector {

    public static final int IDLE = 0;
    public static final int CALIBRATING = 1;
    public static final int READY = 2;
    public static final int IN_AIR = 3;
    public static final int DONE = 4;

    private static final double GRAVITY = 9.81;          // m/s^2
    private static final int CALIBRATION_FRAMES = 20;
    // The hip must rise by 15% of the torso length to count as "in the air".
    // Using torso length makes this work whether the athlete is near or far from the camera.
    private static final float THRESHOLD_FRACTION = 0.15f;
    private static final double MIN_FLIGHT_SEC = 0.10;   // shorter = probably noise
    private static final double MAX_FLIGHT_SEC = 1.20;   // longer = not a real jump

    private int state = IDLE;
    private int calibrationCount = 0;
    private float hipSum = 0;
    private float torsoSum = 0;

    private float baselineHipY = -1;
    private float threshold = 0;
    private long takeOffTimeNs = 0;
    private float peakHipY = 0;

    private double flightTimeSec = 0;
    private double jumpHeightCm = 0;

    /** Start (or restart) a new jump test. */
    public void start() {
        state = CALIBRATING;
        calibrationCount = 0;
        hipSum = 0;
        torsoSum = 0;
        baselineHipY = -1;
        flightTimeSec = 0;
        jumpHeightCm = 0;
    }

    public void reset() {
        state = IDLE;
        baselineHipY = -1;
    }

    /**
     * Called once for every camera frame where the hips were detected.
     * @param hipY        average y of the left and right hip (image pixels)
     * @param torsoLength distance from mid-shoulder to mid-hip (image pixels)
     * @param timeNs      camera timestamp of this frame in nanoseconds
     */
    public void onFrame(float hipY, float torsoLength, long timeNs) {
        switch (state) {
            case CALIBRATING:
                hipSum += hipY;
                torsoSum += torsoLength;
                calibrationCount++;
                if (calibrationCount >= CALIBRATION_FRAMES) {
                    baselineHipY = hipSum / calibrationCount;
                    float torso = torsoSum / calibrationCount;
                    threshold = Math.max(torso * THRESHOLD_FRACTION, 4f);
                    state = READY;
                }
                break;

            case READY:
                if (hipY < baselineHipY - threshold) {      // hip clearly above standing level
                    takeOffTimeNs = timeNs;
                    peakHipY = hipY;
                    state = IN_AIR;
                }
                break;

            case IN_AIR:
                if (hipY < peakHipY) {
                    peakHipY = hipY;                          // remember the highest point
                }
                double secondsInAir = (timeNs - takeOffTimeNs) / 1_000_000_000.0;
                if (hipY >= baselineHipY - threshold) {     // back down to standing level = landed
                    if (secondsInAir < MIN_FLIGHT_SEC) {
                        state = READY;                        // too short: just noise, keep waiting
                    } else {
                        flightTimeSec = secondsInAir;
                        jumpHeightCm = heightFromFlightTime(flightTimeSec);
                        state = DONE;
                    }
                } else if (secondsInAir > MAX_FLIGHT_SEC) {
                    state = READY;                            // athlete probably moved; try again
                }
                break;

            default:
                // IDLE or DONE: nothing to do
                break;
        }
    }

    /** h = g * t^2 / 8, converted from metres to centimetres. */
    public static double heightFromFlightTime(double flightSec) {
        return GRAVITY * flightSec * flightSec / 8.0 * 100.0;
    }

    public int getState() { return state; }
    public int getCalibrationCount() { return calibrationCount; }
    public int getCalibrationTarget() { return CALIBRATION_FRAMES; }
    public float getBaselineHipY() { return baselineHipY; }
    public float getThreshold() { return threshold; }
    public double getFlightTimeSec() { return flightTimeSec; }
    public double getJumpHeightCm() { return jumpHeightCm; }
}
