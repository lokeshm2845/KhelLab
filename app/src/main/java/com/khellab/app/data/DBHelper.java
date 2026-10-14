package com.khellab.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.khellab.app.model.TestResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite database for test results (Milestone 3).
 * SQLiteOpenHelper creates the database file the first time it is used (onCreate)
 * and gives us getWritableDatabase() / getReadableDatabase() to run queries.
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "khellab.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_RESULTS = "results";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "athlete_name";
    public static final String COL_AGE = "age";
    public static final String COL_GENDER = "gender";
    public static final String COL_TYPE = "test_type";
    public static final String COL_VALUE = "value";
    public static final String COL_UNIT = "unit";
    public static final String COL_RATING = "rating";
    public static final String COL_NOTES = "notes";
    public static final String COL_DATE = "test_date";
    public static final String COL_VENUE = "venue";
    public static final String COL_LAT = "latitude";
    public static final String COL_LNG = "longitude";
    public static final String COL_SYNCED = "synced";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_RESULTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_AGE + " INTEGER, "
                + COL_GENDER + " TEXT, "
                + COL_TYPE + " TEXT NOT NULL, "
                + COL_VALUE + " REAL NOT NULL, "
                + COL_UNIT + " TEXT, "
                + COL_RATING + " TEXT, "
                + COL_NOTES + " TEXT, "
                + COL_DATE + " INTEGER, "
                + COL_VENUE + " TEXT, "
                + COL_LAT + " REAL, "
                + COL_LNG + " REAL, "
                + COL_SYNCED + " INTEGER DEFAULT 0)";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple strategy for a student project: drop the old table and create it again
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RESULTS);
        onCreate(db);
    }

    /** INSERT one result. Returns the new row id (or -1 if it failed). */
    public long insertResult(TestResult r) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, r.getAthleteName());
        values.put(COL_AGE, r.getAge());
        values.put(COL_GENDER, r.getGender());
        values.put(COL_TYPE, r.getTestType());
        values.put(COL_VALUE, r.getValue());
        values.put(COL_UNIT, r.getUnit());
        values.put(COL_RATING, r.getRating());
        values.put(COL_NOTES, r.getNotes());
        values.put(COL_DATE, r.getTimestamp());
        values.put(COL_VENUE, r.getVenue());
        values.put(COL_LAT, r.getLatitude());
        values.put(COL_LNG, r.getLongitude());
        values.put(COL_SYNCED, r.isSynced() ? 1 : 0);
        long id = db.insert(TABLE_RESULTS, null, values);
        r.setId(id);
        return id;
    }

    /** SELECT every result, newest first. */
    public List<TestResult> getAllResults() {
        return query(null, null);
    }

    /** SELECT results that have not been uploaded to Firebase yet. */
    public List<TestResult> getUnsyncedResults() {
        return query(COL_SYNCED + " = ?", new String[]{"0"});
    }

    /** UPDATE: mark one result as uploaded to Firebase. */
    public void markSynced(long id) {
        ContentValues values = new ContentValues();
        values.put(COL_SYNCED, 1);
        getWritableDatabase().update(TABLE_RESULTS, values,
                COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    /** DELETE one result. */
    public void deleteResult(long id) {
        getWritableDatabase().delete(TABLE_RESULTS, COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    public int getResultCount() {
        return count(null);
    }

    public int getUnsyncedCount() {
        return count(COL_SYNCED + " = 0");
    }

    // ----- private helpers -----

    private int count(String where) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_RESULTS + (where == null ? "" : " WHERE " + where);
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        int n = 0;
        if (c.moveToFirst()) {
            n = c.getInt(0);
        }
        c.close();
        return n;
    }

    private List<TestResult> query(String selection, String[] args) {
        List<TestResult> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_RESULTS, null, selection, args,
                null, null, COL_DATE + " DESC");

        // Move through the cursor row by row, turning each row into a TestResult object
        while (c.moveToNext()) {
            TestResult r = new TestResult(
                    c.getString(c.getColumnIndexOrThrow(COL_NAME)),
                    c.getInt(c.getColumnIndexOrThrow(COL_AGE)),
                    c.getString(c.getColumnIndexOrThrow(COL_GENDER)),
                    c.getString(c.getColumnIndexOrThrow(COL_TYPE)),
                    c.getDouble(c.getColumnIndexOrThrow(COL_VALUE)),
                    c.getString(c.getColumnIndexOrThrow(COL_UNIT)),
                    c.getString(c.getColumnIndexOrThrow(COL_RATING)),
                    c.getString(c.getColumnIndexOrThrow(COL_NOTES)),
                    c.getLong(c.getColumnIndexOrThrow(COL_DATE)),
                    c.getString(c.getColumnIndexOrThrow(COL_VENUE)),
                    c.getDouble(c.getColumnIndexOrThrow(COL_LAT)),
                    c.getDouble(c.getColumnIndexOrThrow(COL_LNG)));
            r.setId(c.getLong(c.getColumnIndexOrThrow(COL_ID)));
            r.setSynced(c.getInt(c.getColumnIndexOrThrow(COL_SYNCED)) == 1);
            list.add(r);
        }
        c.close();
        return list;
    }
}
