package com.khellab.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.khellab.app.analysis.BenchmarkHelper;
import com.khellab.app.data.DBHelper;
import com.khellab.app.data.SessionPrefs;
import com.khellab.app.model.TestResult;
import com.khellab.app.ui.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Reaction time test: the pad turns green after a random delay, and we measure how long
 * the athlete takes to tap it. 5 trials are averaged.
 */
public class ReactionTestActivity extends AppCompatActivity {

    // The pad can be in one of these states
    private static final int STATE_IDLE = 0;      // grey: waiting for the athlete to start a trial
    private static final int STATE_WAITING = 1;   // red: random delay running, do NOT tap
    private static final int STATE_GO = 2;        // green: tap now!
    private static final int STATE_FINISHED = 3;  // all trials done

    private static final int TOTAL_TRIALS = 5;

    TextView txtAthlete, pad, txtTrials;
    Button btnReset, btnSave;
    DBHelper dbHelper;

    final Handler handler = new Handler(Looper.getMainLooper());
    final Random random = new Random();
    final List<Long> times = new ArrayList<>();
    int state = STATE_IDLE;
    long goTime = 0;
    double averageMs = 0;

    // Runs after the random delay: turns the pad green and starts the clock
    final Runnable showGo = () -> {
        state = STATE_GO;
        goTime = SystemClock.elapsedRealtime();
        pad.setBackgroundColor(ContextCompat.getColor(this, R.color.reaction_go));
        pad.setText("TAP!");
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reaction);
        UiUtils.applySystemBarPadding(findViewById(R.id.reactionRoot));

        dbHelper = new DBHelper(this);
        txtAthlete = findViewById(R.id.txtReactionAthlete);
        pad = findViewById(R.id.reactionPad);
        txtTrials = findViewById(R.id.txtTrials);
        btnReset = findViewById(R.id.btnReactionReset);
        btnSave = findViewById(R.id.btnReactionSave);

        txtAthlete.setText("Athlete: " + SessionPrefs.getAthleteName(this));

        pad.setOnClickListener(v -> onPadTapped());
        btnReset.setOnClickListener(v -> resetTest());
        btnSave.setOnClickListener(v -> saveResult());
    }

    private void onPadTapped() {
        switch (state) {
            case STATE_IDLE:
                // Start a trial: red now, green after a random 1.5 - 4 second wait
                state = STATE_WAITING;
                pad.setBackgroundColor(ContextCompat.getColor(this, R.color.reaction_wait));
                pad.setText("Wait for green...");
                handler.postDelayed(showGo, 1500 + random.nextInt(2500));
                break;

            case STATE_WAITING:
                // Tapped before green: false start, this trial does not count
                handler.removeCallbacks(showGo);
                state = STATE_IDLE;
                pad.setBackgroundColor(ContextCompat.getColor(this, R.color.reaction_idle));
                pad.setText("False start!\nTap to retry trial " + (times.size() + 1));
                break;

            case STATE_GO:
                long reaction = SystemClock.elapsedRealtime() - goTime;
                times.add(reaction);
                txtTrials.append("Trial " + times.size() + ": " + reaction + " ms\n");

                if (times.size() < TOTAL_TRIALS) {
                    state = STATE_IDLE;
                    pad.setBackgroundColor(ContextCompat.getColor(this, R.color.reaction_idle));
                    pad.setText(reaction + " ms\nTap to start trial " + (times.size() + 1));
                } else {
                    finishTest();
                }
                break;

            default:
                break;
        }
    }

    private void finishTest() {
        state = STATE_FINISHED;
        long sum = 0;
        for (long t : times) sum += t;
        averageMs = (double) sum / times.size();

        String rating = BenchmarkHelper.rate(TestResult.TYPE_REACTION, averageMs,
                SessionPrefs.getAthleteAge(this), SessionPrefs.getAthleteGender(this));
        pad.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_primary));
        pad.setText(String.format(Locale.US, "Average\n%.0f ms\n%s", averageMs, rating));
        btnSave.setEnabled(true);
    }

    private void resetTest() {
        handler.removeCallbacks(showGo);
        times.clear();
        state = STATE_IDLE;
        averageMs = 0;
        txtTrials.setText("");
        pad.setBackgroundColor(ContextCompat.getColor(this, R.color.reaction_idle));
        pad.setText("Tap to start trial 1");
        btnSave.setEnabled(false);
    }

    private void saveResult() {
        String rating = BenchmarkHelper.rate(TestResult.TYPE_REACTION, averageMs,
                SessionPrefs.getAthleteAge(this), SessionPrefs.getAthleteGender(this));

        StringBuilder notes = new StringBuilder("Average of " + TOTAL_TRIALS + " touch trials:");
        for (long t : times) notes.append(" ").append(t);

        TestResult result = new TestResult(
                SessionPrefs.getAthleteName(this), SessionPrefs.getAthleteAge(this),
                SessionPrefs.getAthleteGender(this), TestResult.TYPE_REACTION,
                averageMs, "ms", rating, notes.toString(), System.currentTimeMillis(),
                SessionPrefs.getVenueName(this),
                SessionPrefs.getVenueLat(this), SessionPrefs.getVenueLng(this));

        dbHelper.insertResult(result);
        Toast.makeText(this, "Reaction result saved", Toast.LENGTH_SHORT).show();
        btnSave.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(showGo);
    }
}
