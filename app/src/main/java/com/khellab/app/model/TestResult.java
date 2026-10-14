package com.khellab.app.model;

import java.util.Locale;

/**
 * One fitness test result for one athlete. This is a plain Java object (a "model"):
 * it only holds data. DBHelper turns it into a database row and back again.
 */
public class TestResult {

    // The three test types KhelLab supports
    public static final String TYPE_JUMP = "Vertical Jump";
    public static final String TYPE_SPRINT = "Sprint";
    public static final String TYPE_REACTION = "Reaction Time";

    private long id;              // database row id (0 until saved)
    private String athleteName;
    private int age;
    private String gender;        // "Male" or "Female"
    private String testType;      // one of the TYPE_ constants
    private double value;         // the measurement itself
    private String unit;          // "cm", "s" or "ms"
    private String rating;        // benchmark rating, e.g. "Good"
    private String notes;         // extra details, e.g. flight time
    private long timestamp;       // when the test was done (milliseconds since 1970)
    private String venue;         // name of the test venue
    private double latitude;      // venue GPS position (0 if not tagged)
    private double longitude;
    private boolean synced;       // true once uploaded to Firebase

    public TestResult(String athleteName, int age, String gender, String testType,
                      double value, String unit, String rating, String notes,
                      long timestamp, String venue, double latitude, double longitude) {
        this.athleteName = athleteName;
        this.age = age;
        this.gender = gender;
        this.testType = testType;
        this.value = value;
        this.unit = unit;
        this.rating = rating;
        this.notes = notes;
        this.timestamp = timestamp;
        this.venue = venue;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** Value with its unit, formatted sensibly for each test type, e.g. "32.4 cm". */
    public String getFormattedValue() {
        if (TYPE_REACTION.equals(testType)) {
            return String.format(Locale.US, "%.0f %s", value, unit);
        } else if (TYPE_SPRINT.equals(testType)) {
            return String.format(Locale.US, "%.2f %s", value, unit);
        } else {
            return String.format(Locale.US, "%.1f %s", value, unit);
        }
    }

    public boolean hasLocation() {
        return latitude != 0 || longitude != 0;
    }

    // ----- getters and setters -----

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getAthleteName() { return athleteName; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public String getTestType() { return testType; }
    public double getValue() { return value; }
    public String getUnit() { return unit; }
    public String getRating() { return rating; }
    public String getNotes() { return notes; }
    public long getTimestamp() { return timestamp; }
    public String getVenue() { return venue; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    public boolean isSynced() { return synced; }
    public void setSynced(boolean synced) { this.synced = synced; }
}
