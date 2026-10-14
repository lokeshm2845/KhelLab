package com.khellab.app.analysis;

import com.khellab.app.model.TestResult;

/**
 * Turns a raw number into a rating the coach can understand at a glance,
 * using simple age- and gender-based benchmark tables.
 *
 * IMPORTANT: these are indicative coaching reference values (rounded from common
 * field-test norm tables), NOT official SAI / Khelo India norms and NOT a medical assessment.
 * A real deployment would replace these numbers with the official tables.
 */
public class BenchmarkHelper {

    public static final String EXCELLENT = "Excellent";
    public static final String GOOD = "Good";
    public static final String AVERAGE = "Average";
    public static final String NEEDS_WORK = "Needs work";
    public static final String NOT_RATED = "Not rated";

    public static String rate(String testType, double value, int age, String gender) {
        boolean female = "Female".equalsIgnoreCase(gender);

        if (TestResult.TYPE_JUMP.equals(testType)) {
            // Vertical jump in cm: HIGHER is better. Adult male cut-offs:
            double[] limits = {65, 50, 40};
            if (female) limits = new double[]{55, 42, 32};
            double scale = ageFactor(age);             // younger athletes get lower cut-offs
            return higherIsBetter(value, limits[0] * scale, limits[1] * scale, limits[2] * scale);

        } else if (TestResult.TYPE_SPRINT.equals(testType)) {
            // 20 m sprint in seconds: LOWER is better. Adult male cut-offs:
            double[] limits = {3.1, 3.4, 3.7};
            double extra = 0;
            if (female) extra += 0.3;
            if (age > 0 && age < 14) extra += 0.5;
            else if (age >= 14 && age < 18) extra += 0.2;
            return lowerIsBetter(value, limits[0] + extra, limits[1] + extra, limits[2] + extra);

        } else if (TestResult.TYPE_REACTION.equals(testType)) {
            // Reaction time in ms (touch-screen, includes ~50 ms screen delay): LOWER is better
            return lowerIsBetter(value, 220, 270, 330);
        }
        return NOT_RATED;
    }

    private static double ageFactor(int age) {
        if (age > 0 && age < 14) return 0.70;
        if (age >= 14 && age < 18) return 0.85;
        return 1.0;
    }

    private static String higherIsBetter(double v, double excellent, double good, double average) {
        if (v >= excellent) return EXCELLENT;
        if (v >= good) return GOOD;
        if (v >= average) return AVERAGE;
        return NEEDS_WORK;
    }

    private static String lowerIsBetter(double v, double excellent, double good, double average) {
        if (v <= excellent) return EXCELLENT;
        if (v <= good) return GOOD;
        if (v <= average) return AVERAGE;
        return NEEDS_WORK;
    }
}
