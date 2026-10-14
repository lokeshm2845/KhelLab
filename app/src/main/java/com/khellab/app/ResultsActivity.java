package com.khellab.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.khellab.app.data.CsvExporter;
import com.khellab.app.data.DBHelper;
import com.khellab.app.data.FirebaseSync;
import com.khellab.app.model.TestResult;
import com.khellab.app.ui.ResultAdapter;
import com.khellab.app.ui.UiUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Results screen (Milestone 3, 4, 5): lists every saved test from SQLite,
 * exports them to CSV, and syncs them to Firebase.
 */
public class ResultsActivity extends AppCompatActivity {

    ListView listResults;
    TextView txtSummary, txtEmpty;
    Button btnExport, btnSync;

    DBHelper dbHelper;
    List<TestResult> results = new ArrayList<>();
    ResultAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_results);
        UiUtils.applySystemBarPadding(findViewById(R.id.resultsRoot));

        dbHelper = new DBHelper(this);
        listResults = findViewById(R.id.listResults);
        txtSummary = findViewById(R.id.txtSummary);
        txtEmpty = findViewById(R.id.txtEmpty);
        btnExport = findViewById(R.id.btnExport);
        btnSync = findViewById(R.id.btnSync);

        adapter = new ResultAdapter(this, results);
        listResults.setAdapter(adapter);

        // Tap = show details, long-press = delete
        listResults.setOnItemClickListener((parent, view, position, id) ->
                showDetails(results.get(position)));
        listResults.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(results.get(position));
            return true;
        });

        btnExport.setOnClickListener(v -> chooseExportType());
        btnSync.setOnClickListener(v -> syncToCloud());

        loadResults();
    }

    /** Reads all rows from SQLite again and refreshes the list. */
    private void loadResults() {
        results.clear();
        results.addAll(dbHelper.getAllResults());
        adapter.notifyDataSetChanged();

        boolean empty = results.isEmpty();
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listResults.setVisibility(empty ? View.GONE : View.VISIBLE);
        txtSummary.setText(results.size() + " results, "
                + dbHelper.getUnsyncedCount() + " waiting to sync");
    }

    private void showDetails(TestResult r) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        String location = r.hasLocation()
                ? String.format(Locale.US, "%.5f, %.5f", r.getLatitude(), r.getLongitude())
                : "not tagged";
        String message = "Athlete: " + r.getAthleteName()
                + "\nAge / gender: " + r.getAge() + " / " + r.getGender()
                + "\nTest: " + r.getTestType()
                + "\nResult: " + r.getFormattedValue()
                + "\nRating: " + r.getRating()
                + "\nDate: " + fmt.format(new Date(r.getTimestamp()))
                + "\nVenue: " + r.getVenue()
                + "\nGPS: " + location
                + "\nCloud: " + (r.isSynced() ? "synced" : "not synced yet")
                + "\n\nNotes: " + r.getNotes()
                + "\n\n" + getString(R.string.disclaimer);

        new AlertDialog.Builder(this)
                .setTitle(r.getTestType())
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmDelete(TestResult r) {
        new AlertDialog.Builder(this)
                .setTitle("Delete result?")
                .setMessage(r.getAthleteName() + " - " + r.getTestType() + " " + r.getFormattedValue()
                        + "\n\nThis removes it from this phone. Copies already synced to the cloud are kept.")
                .setPositiveButton("Delete", (d, w) -> {
                    dbHelper.deleteResult(r.getId());
                    loadResults();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ----- CSV export (Milestone 4) -----

    private void chooseExportType() {
        if (results.isEmpty()) {
            Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = {
                "Full export (with athlete names)",
                "Anonymised export (names replaced, safer to share)"};
        new AlertDialog.Builder(this)
                .setTitle("Export results to CSV")
                .setItems(options, (dialog, which) -> exportCsv(which == 1))
                .show();
    }

    private void exportCsv(boolean anonymise) {
        try {
            File file = CsvExporter.export(this, results, anonymise);
            Toast.makeText(this, "Saved: " + file.getName(), Toast.LENGTH_LONG).show();

            // FileProvider gives other apps a temporary, safe content:// link to our file
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "KhelLab test results");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share CSV file"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ----- Firebase sync (Milestone 5) -----

    private void syncToCloud() {
        btnSync.setEnabled(false);
        btnSync.setText("Syncing...");

        FirebaseSync.syncUnsynced(this, dbHelper, new FirebaseSync.Callback() {
            @Override
            public void onSuccess(int uploadedCount) {
                resetSyncButton();
                String msg = uploadedCount == 0
                        ? "Everything is already synced"
                        : uploadedCount + " results uploaded to Firebase";
                Toast.makeText(ResultsActivity.this, msg, Toast.LENGTH_LONG).show();
                loadResults();
            }

            @Override
            public void onError(String message) {
                resetSyncButton();
                new AlertDialog.Builder(ResultsActivity.this)
                        .setTitle("Sync not completed")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void resetSyncButton() {
        btnSync.setEnabled(true);
        btnSync.setText("Sync to cloud");
    }
}
