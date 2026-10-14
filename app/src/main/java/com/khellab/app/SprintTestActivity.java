package com.khellab.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.khellab.app.analysis.BenchmarkHelper;
import com.khellab.app.data.DBHelper;
import com.khellab.app.data.SessionPrefs;
import com.khellab.app.model.TestResult;
import com.khellab.app.ui.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sprint test with a coach-operated stopwatch and split times.
 * (Detecting line crossings automatically from video is listed as future work.)
 */
public class SprintTestActivity extends AppCompatActivity {

    TextView txtAthlete, txtTimer, txtSplits, txtResult;
    EditText edtDistance;
    Button btnStart, btnSplit, btnStop, btnReset, btnSave;

    DBHelper dbHelper;

    // A Handler lets us run code again and again on the UI thread, to refresh the timer text
    final Handler handler = new Handler(Looper.getMainLooper());
    long startTime = 0;            // SystemClock.elapsedRealtime() when Start was tapped
    boolean running = false;
    final List<Double> splits = new ArrayList<>();
    double finalTimeSec = 0;
    double distanceM = 20;

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (running) {
                txtTimer.setText(String.format(Locale.US, "%.2f s", elapsedSeconds()));
                handler.postDelayed(this, 30);   // run again in 30 ms
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sprint);
        UiUtils.applySystemBarPadding(findViewById(R.id.sprintRoot));

        dbHelper = new DBHelper(this);

        txtAthlete = findViewById(R.id.txtSprintAthlete);
        txtTimer = findViewById(R.id.txtTimer);
        txtSplits = findViewById(R.id.txtSplits);
        txtResult = findViewById(R.id.txtSprintResult);
        edtDistance = findViewById(R.id.edtDistance);
        btnStart = findViewById(R.id.btnSprintStart);
        btnSplit = findViewById(R.id.btnSprintSplit);
        btnStop = findViewById(R.id.btnSprintStop);
        btnReset = findViewById(R.id.btnSprintReset);
        btnSave = findViewById(R.id.btnSprintSave);

        txtAthlete.setText("Athlete: " + SessionPrefs.getAthleteName(this));

        btnStart.setOnClickListener(v -> startTimer());
        btnSplit.setOnClickListener(v -> addSplit());
        btnStop.setOnClickListener(v -> stopTimer());
        btnReset.setOnClickListener(v -> resetTimer());
        btnSave.setOnClickListener(v -> saveResult());
    }

    private double elapsedSeconds() {
        return (SystemClock.elapsedRealtime() - startTime) / 1000.0;
    }

    private void startTimer() {
        String d = edtDistance.getText().toString().trim();
        if (d.isEmpty() || Double.parseDouble(d) <= 0) {
            edtDistance.setError("Enter the sprint distance");
            return;
        }
        distanceM = Double.parseDouble(d);

        splits.clear();
        txtSplits.setText("");
        txtResult.setText("");
        startTime = SystemClock.elapsedRealtime();
        running = true;
        handler.post(tick);

        btnStart.setEnabled(false);
        btnSplit.setEnabled(true);
        btnStop.setEnabled(true);
        btnSave.setEnabled(false);
        edtDistance.setEnabled(false);
    }

    private void addSplit() {
        double t = elapsedSeconds();
        splits.add(t);
        txtSplits.append(String.format(Locale.US, "Split %d: %.2f s\n", splits.size(), t));
    }

    private void stopTimer() {
        finalTimeSec = elapsedSeconds();
        running = false;
        handler.removeCallbacks(tick);
        txtTimer.setText(String.format(Locale.US, "%.2f s", finalTimeSec));

        double speed = distanceM / finalTimeSec;
        String rating = getRating();
        txtResult.setText(String.format(Locale.US, "%.0f m in %.2f s\nAverage speed %.2f m/s (%.1f km/h)\nRating: %s",
                distanceM, finalTimeSec, speed, speed * 3.6, rating));

        btnStart.setEnabled(true);
        btnSplit.setEnabled(false);
        btnStop.setEnabled(false);
        btnSave.setEnabled(true);
        edtDistance.setEnabled(true);
    }

    private void resetTimer() {
        running = false;
        handler.removeCallbacks(tick);
        splits.clear();
        finalTimeSec = 0;
        txtTimer.setText("0.00 s");
        txtSplits.setText("");
        txtResult.setText("");
        btnStart.setEnabled(true);
        btnSplit.setEnabled(false);
        btnStop.setEnabled(false);
        btnSave.setEnabled(false);
        edtDistance.setEnabled(true);
    }

    /** Benchmarks exist only for the standard 20 m sprint. */
    private String getRating() {
        if (Math.abs(distanceM - 20) > 0.01) {
            return BenchmarkHelper.NOT_RATED + " (benchmarks are for 20 m)";
        }
        return BenchmarkHelper.rate(TestResult.TYPE_SPRINT, finalTimeSec,
                SessionPrefs.getAthleteAge(this), SessionPrefs.getAthleteGender(this));
    }

    private void saveResult() {
        StringBuilder notes = new StringBuilder();
        notes.append(String.format(Locale.US, "%.0f m, avg speed %.2f m/s, manual timing",
                distanceM, distanceM / finalTimeSec));
        if (!splits.isEmpty()) {
            notes.append(", splits:");
            for (double s : splits) {
                notes.append(String.format(Locale.US, " %.2f", s));
            }
        }

        String rating = getRating();
        if (rating.startsWith(BenchmarkHelper.NOT_RATED)) {
            rating = BenchmarkHelper.NOT_RATED;
        }

        TestResult result = new TestResult(
                SessionPrefs.getAthleteName(this), SessionPrefs.getAthleteAge(this),
                SessionPrefs.getAthleteGender(this), TestResult.TYPE_SPRINT,
                finalTimeSec, "s", rating, notes.toString(), System.currentTimeMillis(),
                SessionPrefs.getVenueName(this),
                SessionPrefs.getVenueLat(this), SessionPrefs.getVenueLng(this));

        dbHelper.insertResult(result);
        Toast.makeText(this, "Sprint result saved", Toast.LENGTH_SHORT).show();
        btnSave.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);   // stop the timer so it does not leak after the screen closes
    }
}
