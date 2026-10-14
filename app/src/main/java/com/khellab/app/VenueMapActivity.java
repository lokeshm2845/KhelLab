package com.khellab.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.khellab.app.data.DBHelper;
import com.khellab.app.data.SessionPrefs;
import com.khellab.app.model.TestResult;
import com.khellab.app.ui.UiUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Venue map (Milestone 6): shows every place where tests were done, so a district officer
 * can see where testing has and has not reached.
 * - With a Maps API key: markers on an in-app Google Map.
 * - Without a key: a list; tapping a venue opens it in the Google Maps app.
 */
public class VenueMapActivity extends AppCompatActivity {

    /** One venue with its position and how many tests were done there. */
    static class Venue {
        String name;
        double lat, lng;
        int testCount;

        Venue(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    final List<Venue> venues = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_venue_map);
        UiUtils.applySystemBarPadding(findViewById(R.id.venueRoot));

        collectVenues();

        // ----- the list of venues (always shown) -----
        TextView txtSummary = findViewById(R.id.txtVenueSummary);
        txtSummary.setText(venues.size() + " tagged venue(s). Tap one to open in Google Maps.");

        List<String> rows = new ArrayList<>();
        for (Venue v : venues) {
            rows.add(String.format(Locale.US, "%s\n%d test(s)  |  %.5f, %.5f",
                    v.name, v.testCount, v.lat, v.lng));
        }
        if (rows.isEmpty()) {
            rows.add("No venues tagged yet.\nOn the Home screen, enter a venue name and tap 'Tag GPS'.");
        }
        ListView list = findViewById(R.id.listVenues);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < venues.size()) openInMapsApp(venues.get(position));
        });

        // ----- the in-app map (only if a key was provided in local.properties) -----
        if (BuildConfig.MAPS_API_KEY.isEmpty()) {
            findViewById(R.id.txtNoMap).setVisibility(View.VISIBLE);
        } else {
            SupportMapFragment mapFragment = SupportMapFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mapContainer, mapFragment)
                    .commit();
            mapFragment.getMapAsync(this::showMarkers);   // called when the map is ready
        }
    }

    /** Builds the venue list from saved results, plus the current session's venue. */
    private void collectVenues() {
        // LinkedHashMap keeps one entry per venue name, in the order we first see them
        Map<String, Venue> byName = new LinkedHashMap<>();

        String currentName = SessionPrefs.getVenueName(this);
        double currentLat = SessionPrefs.getVenueLat(this);
        double currentLng = SessionPrefs.getVenueLng(this);
        if (currentLat != 0 || currentLng != 0) {
            String name = currentName.isEmpty() ? "Current venue" : currentName;
            byName.put(name, new Venue(name, currentLat, currentLng));
        }

        for (TestResult r : new DBHelper(this).getAllResults()) {
            if (!r.hasLocation()) continue;
            String name = (r.getVenue() == null || r.getVenue().isEmpty()) ? "Unnamed venue" : r.getVenue();
            Venue v = byName.get(name);
            if (v == null) {
                v = new Venue(name, r.getLatitude(), r.getLongitude());
                byName.put(name, v);
            }
            v.testCount++;
        }
        venues.addAll(byName.values());
    }

    private void showMarkers(GoogleMap map) {
        if (venues.isEmpty()) {
            // Default view: India
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(20.59, 78.96), 4f));
            return;
        }
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (Venue v : venues) {
            LatLng pos = new LatLng(v.lat, v.lng);
            map.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(v.name)
                    .snippet(v.testCount + " test(s)"));
            bounds.include(pos);
        }
        if (venues.size() == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(venues.get(0).lat, venues.get(0).lng), 15f));
        } else {
            map.setOnMapLoadedCallback(() ->
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120)));
        }
    }

    /** A "geo:" link opens the location in whichever maps app is installed. */
    private void openInMapsApp(Venue v) {
        String uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                v.lat, v.lng, v.lat, v.lng, Uri.encode(v.name));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No maps app installed", Toast.LENGTH_SHORT).show();
        }
    }
}
