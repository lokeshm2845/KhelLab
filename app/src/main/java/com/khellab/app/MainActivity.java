package com.khellab.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.khellab.app.analysis.BenchmarkHelper;
import com.khellab.app.analysis.JumpDetector;
import com.khellab.app.analysis.LandingFormCheck;
import com.khellab.app.data.DBHelper;
import com.khellab.app.data.SessionPrefs;
import com.khellab.app.model.TestResult;
import com.khellab.app.ui.PoseOverlayView;
import com.khellab.app.ui.UiUtils;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Vertical jump test.
 * Milestone 1: live camera preview + ML Kit pose skeleton overlay.
 * Milestone 2: Start button -> calibrate -> detect take-off and landing -> jump height from flight time.
 */
public class MainActivity extends AppCompatActivity {

    PreviewView previewView;
    PoseOverlayView overlay;
    ExecutorService cameraExecutor;
    PoseDetector poseDetector;

    TextView txtAthlete, txtStatus, txtResult, txtHint;
    Button btnRecord, btnManual, btnSave, btnFlip;

    DBHelper dbHelper;
    JumpDetector jumpDetector = new JumpDetector();
    int lensFacing = CameraSelector.LENS_FACING_BACK;

    // The last measured jump, waiting to be saved
    double lastFlightSec = 0;
    double lastHeightCm = 0;
    String lastSource = "";
    String lastHint = "";

    // Landing-form hint: we look at the knees for a few frames just after landing
    int landingFramesLeft = 0;
    float smallestKneeRatio = Float.MAX_VALUE;

    ActivityResultLauncher<String> requestPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission is needed for the jump test",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        UiUtils.applySystemBarPadding(findViewById(R.id.main));

        previewView = findViewById(R.id.previewView);
        overlay = findViewById(R.id.poseOverlay);
        txtAthlete = findViewById(R.id.txtAthlete);
        txtStatus = findViewById(R.id.txtStatus);
        txtResult = findViewById(R.id.txtResult);
        txtHint = findViewById(R.id.txtHint);
        btnRecord = findViewById(R.id.btnRecord);
        btnManual = findViewById(R.id.btnManual);
        btnSave = findViewById(R.id.btnSave);
        btnFlip = findViewById(R.id.btnFlip);

        dbHelper = new DBHelper(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        txtAthlete.setText(SessionPrefs.getAthleteName(this) + " (" + SessionPrefs.getAthleteAge(this)
                + ", " + SessionPrefs.getAthleteGender(this) + ")");

        // STREAM_MODE: tuned for video. It tracks the person from frame to frame (faster, smoother)
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        btnRecord.setOnClickListener(v -> onRecordClicked());
        btnManual.setOnClickListener(v -> showManualEntryDialog());
        btnSave.setOnClickListener(v -> saveResult());
        btnFlip.setOnClickListener(v -> {
            lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                    ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            jumpDetector.reset();
            overlay.clear();
            updateStatus();
            startCamera();
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermission.launch(Manifest.permission.CAMERA);
        }
        updateStatus();
    }

    // ---------------------------------------------------------------------------------------
    // Camera (Milestone 1)
    // ---------------------------------------------------------------------------------------

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing).build();
                if (!cameraProvider.hasCamera(selector)) {
                    Toast.makeText(this, "That camera is not available on this device",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Use case 1: Preview = what the coach sees on screen
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Use case 2: ImageAnalysis = frames we send to ML Kit.
                // KEEP_ONLY_LATEST drops old frames if ML Kit is busy, so we never lag behind.
                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        processImageProxy(imageProxy);
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, imageAnalyzer);

            } catch (Exception e) {
                Log.e("KhelLab", "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** Runs on the camera thread for every frame. */
    private void processImageProxy(ImageProxy imageProxy) {
        if (imageProxy.getImage() != null) {
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            long timestampNs = imageProxy.getImageInfo().getTimestamp();

            // The camera sensor is sideways; ML Kit returns points for the upright image,
            // so for 90/270 degree rotation the width and height are swapped.
            boolean sideways = (rotation == 90 || rotation == 270);
            int uprightWidth = sideways ? imageProxy.getHeight() : imageProxy.getWidth();
            int uprightHeight = sideways ? imageProxy.getWidth() : imageProxy.getHeight();
            boolean mirrored = (lensFacing == CameraSelector.LENS_FACING_FRONT);

            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), rotation);

            // ML Kit listeners run on the main (UI) thread, so it is safe to update views in them
            poseDetector.process(image)
                    .addOnSuccessListener(pose -> {
                        overlay.updatePose(pose, uprightWidth, uprightHeight, mirrored);
                        onPoseDetected(pose, timestampNs);
                    })
                    .addOnFailureListener(e ->
                            Log.e("KhelLab", "Pose detection failed", e))
                    // Always close the frame, otherwise CameraX stops sending new frames
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Jump detection (Milestone 2)
    // ---------------------------------------------------------------------------------------

    private void onPoseDetected(Pose pose, long timeNs) {
        int before = jumpDetector.getState();

        if (before == JumpDetector.CALIBRATING || before == JumpDetector.READY
                || before == JumpDetector.IN_AIR) {
            PoseLandmark lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
            PoseLandmark rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
            PoseLandmark ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
            PoseLandmark rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

            if (isVisible(lh) && isVisible(rh) && isVisible(ls) && isVisible(rs)) {
                float hipX = (lh.getPosition().x + rh.getPosition().x) / 2f;
                float hipY = (lh.getPosition().y + rh.getPosition().y) / 2f;
                float shoulderX = (ls.getPosition().x + rs.getPosition().x) / 2f;
                float shoulderY = (ls.getPosition().y + rs.getPosition().y) / 2f;
                float torsoLength = (float) Math.hypot(hipX - shoulderX, hipY - shoulderY);

                jumpDetector.onFrame(hipY, torsoLength, timeNs);
            } else if (before != JumpDetector.IN_AIR) {
                txtStatus.setText("Cannot see the athlete's shoulders and hips. "
                        + "Move the phone back so the whole body is in the frame.");
                return;
            }
        }

        int after = jumpDetector.getState();

        if (before == JumpDetector.CALIBRATING && after == JumpDetector.READY) {
            overlay.setBaseline(jumpDetector.getBaselineHipY(), jumpDetector.getThreshold());
        }
        if (before == JumpDetector.IN_AIR && after == JumpDetector.DONE) {
            onJumpFinished();
        }

        // Watch the knees for 10 frames after landing for the coaching hint
        if (landingFramesLeft > 0) {
            float ratio = LandingFormCheck.kneeToAnkleRatio(pose);
            if (ratio >= 0) {
                smallestKneeRatio = Math.min(smallestKneeRatio, ratio);
            }
            landingFramesLeft--;
            if (landingFramesLeft == 0) {
                lastHint = LandingFormCheck.hintFor(
                        smallestKneeRatio == Float.MAX_VALUE ? -1 : smallestKneeRatio);
                txtHint.setText(lastHint);
                txtHint.setVisibility(View.VISIBLE);
            }
        }

        if (after != before || after == JumpDetector.CALIBRATING || after == JumpDetector.READY) {
            updateStatus();
        }
    }

    private boolean isVisible(PoseLandmark landmark) {
        return landmark != null && landmark.getInFrameLikelihood() > 0.5f;
    }

    private void onRecordClicked() {
        int state = jumpDetector.getState();
        if (state == JumpDetector.CALIBRATING || state == JumpDetector.READY
                || state == JumpDetector.IN_AIR) {
            jumpDetector.reset();                   // the button acts as "Cancel" while running
            overlay.setBaseline(-1, 0);
        } else {
            jumpDetector.start();
            overlay.setBaseline(-1, 0);
            txtResult.setVisibility(View.GONE);
            txtHint.setVisibility(View.GONE);
            btnSave.setEnabled(false);
        }
        updateStatus();
    }

    private void onJumpFinished() {
        showJumpResult(jumpDetector.getFlightTimeSec(), "live pose tracking");
        // Start collecting landing frames for the coaching hint
        landingFramesLeft = 10;
        smallestKneeRatio = Float.MAX_VALUE;
        lastHint = "";
    }

    private void showJumpResult(double flightSec, String source) {
        lastFlightSec = flightSec;
        lastHeightCm = JumpDetector.heightFromFlightTime(flightSec);
        lastSource = source;

        String rating = BenchmarkHelper.rate(TestResult.TYPE_JUMP, lastHeightCm,
                SessionPrefs.getAthleteAge(this), SessionPrefs.getAthleteGender(this));

        txtResult.setText(String.format(Locale.US, "%.1f cm  (%s)\nflight time %.3f s",
                lastHeightCm, rating, lastFlightSec));
        txtResult.setVisibility(View.VISIBLE);
        btnSave.setEnabled(true);
    }

    /** Lets the coach type a flight time measured from a slow-motion video (also used on the emulator). */
    private void showManualEntryDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("e.g. 480");

        new AlertDialog.Builder(this)
                .setTitle("Enter flight time (ms)")
                .setMessage("Time from take-off to landing, e.g. counted frame-by-frame in a "
                        + "slow-motion video. KhelLab converts it to jump height.")
                .setView(input)
                .setPositiveButton("Calculate", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    double ms = Double.parseDouble(text);
                    if (ms < 100 || ms > 1200) {
                        Toast.makeText(this, "Enter a value between 100 and 1200 ms",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    jumpDetector.reset();
                    overlay.setBaseline(-1, 0);
                    lastHint = "";
                    txtHint.setVisibility(View.GONE);
                    showJumpResult(ms / 1000.0, "manual entry");
                    updateStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveResult() {
        int age = SessionPrefs.getAthleteAge(this);
        String gender = SessionPrefs.getAthleteGender(this);
        String rating = BenchmarkHelper.rate(TestResult.TYPE_JUMP, lastHeightCm, age, gender);

        String notes = String.format(Locale.US, "Flight time %.3f s (%s)", lastFlightSec, lastSource);
        if (!lastHint.isEmpty()) {
            notes += ". " + lastHint;
        }

        TestResult result = new TestResult(
                SessionPrefs.getAthleteName(this), age, gender, TestResult.TYPE_JUMP,
                lastHeightCm, "cm", rating, notes, System.currentTimeMillis(),
                SessionPrefs.getVenueName(this),
                SessionPrefs.getVenueLat(this), SessionPrefs.getVenueLng(this));

        long id = dbHelper.insertResult(result);
        if (id > 0) {
            Toast.makeText(this, String.format(Locale.US, "Saved: %.1f cm (%s)", lastHeightCm, rating),
                    Toast.LENGTH_SHORT).show();
            btnSave.setEnabled(false);       // prevent saving the same jump twice
        } else {
            Toast.makeText(this, "Could not save the result", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus() {
        switch (jumpDetector.getState()) {
            case JumpDetector.CALIBRATING:
                txtStatus.setText("Calibrating: athlete stands still, hands on hips ("
                        + jumpDetector.getCalibrationCount() + "/"
                        + jumpDetector.getCalibrationTarget() + ")");
                btnRecord.setText("Cancel");
                break;
            case JumpDetector.READY:
                txtStatus.setText("READY - jump now! (land back on the same spot)");
                btnRecord.setText("Cancel");
                break;
            case JumpDetector.IN_AIR:
                txtStatus.setText("In the air...");
                btnRecord.setText("Cancel");
                break;
            case JumpDetector.DONE:
                txtStatus.setText("Jump detected. Tap Save, or Again to repeat.");
                btnRecord.setText("Again");
                break;
            default:
                txtStatus.setText("Place the phone on a tripod so the whole body is visible, then tap "
                        + "Start. No athlete on camera? Use Manual to type a flight time.");
                btnRecord.setText("Start");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        poseDetector.close();
    }
}
