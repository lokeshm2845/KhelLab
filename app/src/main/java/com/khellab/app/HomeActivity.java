package com.khellab.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.khellab.app.data.DBHelper;
import com.khellab.app.data.SessionPrefs;
import com.khellab.app.ui.UiUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Home screen (launcher). The coach enters who is being tested and where,
 * then picks a test. Results, CSV export, sync and the venue map are reached from here too.
 */
public class HomeActivity extends AppCompatActivity {

    EditText edtName, edtAge, edtVenue;
    RadioGroup rgGender;
    TextView txtLocation, txtStats;
    DBHelper dbHelper;
    FusedLocationProviderClient locationClient;

    // Asks for both location permissions at once; the callback runs after the user answers
    ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            (Map<String, Boolean> result) -> {
                Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    fetchLocation();
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        UiUtils.applySystemBarPadding(findViewById(R.id.homeRoot));

        dbHelper = new DBHelper(this);
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        edtName = findViewById(R.id.edtAthleteName);
        edtAge = findViewById(R.id.edtAge);
        edtVenue = findViewById(R.id.edtVenue);
        rgGender = findViewById(R.id.rgGender);
        txtLocation = findViewById(R.id.txtLocation);
        txtStats = findViewById(R.id.txtStats);

        Button btnTagLocation = findViewById(R.id.btnTagLocation);
        Button btnVenueMap = findViewById(R.id.btnVenueMap);
        Button btnJump = findViewById(R.id.btnJumpTest);
        Button btnSprint = findViewById(R.id.btnSprintTest);
        Button btnReaction = findViewById(R.id.btnReactionTest);
        Button btnResults = findViewById(R.id.btnResults);

        loadSavedSession();

        // Each test button first checks the athlete details, then opens that test's screen
        btnJump.setOnClickListener(v -> openTest(MainActivity.class));
        btnSprint.setOnClickListener(v -> openTest(SprintTestActivity.class));
        btnReaction.setOnClickListener(v -> openTest(ReactionTestActivity.class));

        btnResults.setOnClickListener(v -> startActivity(new Intent(this, ResultsActivity.class)));
        btnVenueMap.setOnClickListener(v -> startActivity(new Intent(this, VenueMapActivity.class)));

        btnTagLocation.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                fetchLocation();
            } else {
                locationPermission.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the counts every time we come back from another screen
        int total = dbHelper.getResultCount();
        int unsynced = dbHelper.getUnsyncedCount();
        txtStats.setText(total + " results saved on this phone (" + unsynced + " not yet synced)");
    }

    /** Fill the form with whatever was used last time. */
    private void loadSavedSession() {
        edtName.setText(SessionPrefs.getAthleteName(this));
        int age = SessionPrefs.getAthleteAge(this);
        if (age > 0) edtAge.setText(String.valueOf(age));
        if ("Female".equals(SessionPrefs.getAthleteGender(this))) {
            rgGender.check(R.id.rbFemale);
        } else {
            rgGender.check(R.id.rbMale);
        }
        edtVenue.setText(SessionPrefs.getVenueName(this));
        showLocation(SessionPrefs.getVenueLat(this), SessionPrefs.getVenueLng(this));
    }

    /** Validates the form, saves it in SessionPrefs, then opens the chosen test screen. */
    private void openTest(Class<?> testActivity) {
        String name = edtName.getText().toString().trim();
        String ageText = edtAge.getText().toString().trim();

        if (name.isEmpty()) {
            edtName.setError("Enter the athlete's name");
            edtName.requestFocus();
            return;
        }
        if (ageText.isEmpty()) {
            edtAge.setError("Enter age");
            edtAge.requestFocus();
            return;
        }
        int age = Integer.parseInt(ageText);
        if (age < 5 || age > 80) {
            edtAge.setError("Age should be between 5 and 80");
            edtAge.requestFocus();
            return;
        }
        String gender = rgGender.getCheckedRadioButtonId() == R.id.rbFemale ? "Female" : "Male";

        SessionPrefs.saveAthlete(this, name, age, gender);
        SessionPrefs.saveVenueName(this, edtVenue.getText().toString().trim());

        startActivity(new Intent(this, testActivity));
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Gets the phone's current GPS position and saves it as the venue location. */
    @SuppressLint("MissingPermission") // we only call this after checking the permission
    private void fetchLocation() {
        txtLocation.setText("Getting GPS location...");
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        saveLocation(location.getLatitude(), location.getLongitude());
                    } else {
                        // No fresh fix: fall back to the last known location
                        locationClient.getLastLocation().addOnSuccessListener(last -> {
                            if (last != null) {
                                saveLocation(last.getLatitude(), last.getLongitude());
                            } else {
                                txtLocation.setText("Location not available. Turn on GPS and try again.");
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> txtLocation.setText("Location error: " + e.getMessage()));
    }

    private void saveLocation(double lat, double lng) {
        SessionPrefs.saveVenueLocation(this, lat, lng);
        SessionPrefs.saveVenueName(this, edtVenue.getText().toString().trim());
        showLocation(lat, lng);
        Toast.makeText(this, "Venue location tagged", Toast.LENGTH_SHORT).show();
    }

    private void showLocation(double lat, double lng) {
        if (lat == 0 && lng == 0) {
            txtLocation.setText("GPS location not tagged");
        } else {
            txtLocation.setText(String.format(Locale.US, "GPS: %.5f, %.5f", lat, lng));
        }
    }
}
