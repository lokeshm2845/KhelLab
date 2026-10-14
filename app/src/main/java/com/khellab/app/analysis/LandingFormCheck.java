package com.khellab.app.analysis;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * A simple landing-form COACHING HINT (never a medical or injury claim).
 *
 * Idea: when an athlete lands from a jump facing the camera, their knees should stay roughly
 * above their feet. If the gap between the knees becomes much narrower than the gap between the
 * ankles, the knees are moving inwards ("knees caving in"), which coaches usually correct.
 * This only makes sense when the camera faces the athlete from the front.
 */
public class LandingFormCheck {

    /** Knee gap below 75% of the ankle gap triggers the hint. */
    private static final float RATIO_LIMIT = 0.75f;

    /**
     * Returns knee-gap / ankle-gap for this frame, or -1 if the legs are not clearly visible.
     */
    public static float kneeToAnkleRatio(Pose pose) {
        PoseLandmark lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
        PoseLandmark rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
        PoseLandmark la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
        PoseLandmark ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);
        if (!visible(lk) || !visible(rk) || !visible(la) || !visible(ra)) {
            return -1;
        }
        float kneeGap = Math.abs(lk.getPosition().x - rk.getPosition().x);
        float ankleGap = Math.abs(la.getPosition().x - ra.getPosition().x);
        if (ankleGap < 5) {
            return -1;      // feet together or side-on view: cannot judge
        }
        return kneeGap / ankleGap;
    }

    /** Turns the smallest ratio seen during landing into a hint for the coach. */
    public static String hintFor(float smallestRatio) {
        if (smallestRatio < 0) {
            return "Landing hint: legs not clearly visible from the front, no form hint.";
        }
        if (smallestRatio < RATIO_LIMIT) {
            return "Coaching hint: knees moved inwards on landing. Cue \"knees over toes\".";
        }
        return "Coaching hint: knees stayed in line with the feet on landing.";
    }

    private static boolean visible(PoseLandmark lm) {
        return lm != null && lm.getInFrameLikelihood() > 0.5f;
    }
}
