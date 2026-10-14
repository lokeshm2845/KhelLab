package com.khellab.app.data;

import android.content.Context;

import com.khellab.app.model.TestResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * File handling (Milestone 4): writes test results to a .csv file that opens in Excel / Google Sheets.
 * The file goes into the app's own folder on external storage
 * (Android/data/com.khellab.app/files/exports/), which needs no storage permission.
 */
public class CsvExporter {

    public static File export(Context context, List<TestResult> results, boolean anonymise)
            throws IOException {

        File folder = new File(context.getExternalFilesDir(null), "exports");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(folder, "KhelLab_results_" + stamp + ".csv");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        // When anonymising, the same athlete always gets the same code (Athlete 1, Athlete 2, ...)
        Map<String, String> codes = new HashMap<>();

        // try-with-resources closes the FileWriter automatically, even if an error happens
        try (FileWriter writer = new FileWriter(file)) {
            writer.append("id,athlete,age,gender,test_type,value,unit,rating,date,venue,latitude,longitude,notes\n");

            for (TestResult r : results) {
                String name = r.getAthleteName();
                if (anonymise) {
                    if (!codes.containsKey(name)) {
                        codes.put(name, "Athlete " + (codes.size() + 1));
                    }
                    name = codes.get(name);
                }

                writer.append(String.valueOf(r.getId())).append(',')
                        .append(escape(name)).append(',')
                        .append(String.valueOf(r.getAge())).append(',')
                        .append(escape(r.getGender())).append(',')
                        .append(escape(r.getTestType())).append(',')
                        .append(String.format(Locale.US, "%.3f", r.getValue())).append(',')
                        .append(escape(r.getUnit())).append(',')
                        .append(escape(r.getRating())).append(',')
                        .append(dateFormat.format(new Date(r.getTimestamp()))).append(',')
                        .append(escape(r.getVenue())).append(',')
                        .append(String.valueOf(r.getLatitude())).append(',')
                        .append(String.valueOf(r.getLongitude())).append(',')
                        .append(escape(r.getNotes())).append('\n');
            }
        }
        return file;
    }

    /** CSV rule: if a value contains a comma, quote or new line, wrap it in quotes and double any quotes. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
