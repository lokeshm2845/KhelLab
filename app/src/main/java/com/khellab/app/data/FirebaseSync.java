package com.khellab.app.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.khellab.app.model.TestResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloud sync (Milestone 5): uploads results that are not yet synced to Firebase Firestore,
 * so a whole cohort's results can be reviewed together.
 *
 * The app works fully offline: results are always saved in SQLite first, and this class
 * only uploads them later when there is internet. Each result is marked "synced" in SQLite
 * after Firestore confirms the upload, so nothing is uploaded twice.
 */
public class FirebaseSync {

    public interface Callback {
        void onSuccess(int uploadedCount);
        void onError(String message);
    }

    /** Firebase is only set up after google-services.json has been added to the app/ folder. */
    public static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    public static void syncUnsynced(Context context, DBHelper db, Callback callback) {
        if (!isConfigured(context)) {
            callback.onError("Firebase is not connected yet. In Android Studio use "
                    + "Tools > Firebase > Cloud Firestore to connect the app, then rebuild. "
                    + "Your results are safe on this phone.");
            return;
        }
        if (!isOnline(context)) {
            callback.onError("No internet connection. Results stay saved on this phone "
                    + "and can be synced later.");
            return;
        }

        // Firestore security rules usually require a signed-in user.
        // Anonymous sign-in gives each coach's phone a user id without needing a password.
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            upload(context, db, auth.getCurrentUser().getUid(), callback);
        } else {
            auth.signInAnonymously()
                    .addOnSuccessListener(result ->
                            upload(context, db, result.getUser().getUid(), callback))
                    .addOnFailureListener(e ->
                            callback.onError("Sign-in failed: " + e.getMessage()
                                    + "\n(Enable 'Anonymous' in Firebase console > Authentication.)"));
        }
    }

    private static void upload(Context context, DBHelper db, String coachUid, Callback callback) {
        List<TestResult> pending = db.getUnsyncedResults();
        if (pending.isEmpty()) {
            callback.onSuccess(0);
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String deviceId = SessionPrefs.getDeviceId(context);

        // A WriteBatch uploads all documents together: either all succeed or none do
        WriteBatch batch = firestore.batch();
        for (TestResult r : pending) {
            Map<String, Object> data = new HashMap<>();
            data.put("athleteName", r.getAthleteName());
            data.put("age", r.getAge());
            data.put("gender", r.getGender());
            data.put("testType", r.getTestType());
            data.put("value", r.getValue());
            data.put("unit", r.getUnit());
            data.put("rating", r.getRating());
            data.put("notes", r.getNotes());
            data.put("timestamp", r.getTimestamp());
            data.put("venue", r.getVenue());
            data.put("latitude", r.getLatitude());
            data.put("longitude", r.getLongitude());
            data.put("coachUid", coachUid);
            data.put("deviceId", deviceId);

            // Document id = phone id + local row id, so re-uploading overwrites instead of duplicating
            DocumentReference doc = firestore.collection("results")
                    .document(deviceId + "_" + r.getId());
            batch.set(doc, data);
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    for (TestResult r : pending) {
                        db.markSynced(r.getId());
                    }
                    callback.onSuccess(pending.size());
                })
                .addOnFailureListener(e -> callback.onError("Upload failed: " + e.getMessage()));
    }
}
