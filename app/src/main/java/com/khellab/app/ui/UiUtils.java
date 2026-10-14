package com.khellab.app.ui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.khellab.app.R;
import com.khellab.app.analysis.BenchmarkHelper;

/** Small helpers shared by several screens. */
public class UiUtils {

    /**
     * Android 15+ draws apps "edge to edge" (behind the status bar and navigation bar).
     * This adds padding to the root view so our content is not hidden behind those bars.
     * It is the same code MainActivity used in Milestone 1, moved here so every screen can reuse it.
     */
    public static void applySystemBarPadding(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /** Colour resource for a benchmark rating, used to colour the rating text. */
    public static int ratingColor(String rating) {
        if (BenchmarkHelper.EXCELLENT.equals(rating)) return R.color.rating_excellent;
        if (BenchmarkHelper.GOOD.equals(rating)) return R.color.rating_good;
        if (BenchmarkHelper.AVERAGE.equals(rating)) return R.color.rating_average;
        if (BenchmarkHelper.NEEDS_WORK.equals(rating)) return R.color.rating_needs_work;
        return R.color.text_secondary;
    }
}
