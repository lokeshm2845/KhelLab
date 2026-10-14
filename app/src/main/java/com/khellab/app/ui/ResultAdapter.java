package com.khellab.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.khellab.app.R;
import com.khellab.app.model.TestResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter that turns each TestResult into one row (item_result.xml) of the ListView
 * on the Results screen.
 */
public class ResultAdapter extends ArrayAdapter<TestResult> {

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

    public ResultAdapter(Context context, List<TestResult> results) {
        super(context, 0, results);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        // Re-use an old row view if Android gives us one (faster scrolling), otherwise create one
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_result, parent, false);
        }

        TestResult r = getItem(position);
        if (r == null) return convertView;

        TextView txtName = convertView.findViewById(R.id.txtItemName);
        TextView txtType = convertView.findViewById(R.id.txtItemType);
        TextView txtValue = convertView.findViewById(R.id.txtItemValue);
        TextView txtRating = convertView.findViewById(R.id.txtItemRating);
        TextView txtMeta = convertView.findViewById(R.id.txtItemMeta);

        txtName.setText(r.getAthleteName() + "  (" + r.getAge() + ", " + r.getGender() + ")");
        txtType.setText(r.getTestType());
        txtValue.setText(r.getFormattedValue());
        txtRating.setText(r.getRating());
        txtRating.setTextColor(ContextCompat.getColor(getContext(), UiUtils.ratingColor(r.getRating())));

        String venue = (r.getVenue() == null || r.getVenue().isEmpty()) ? "No venue" : r.getVenue();
        String sync = r.isSynced() ? "Synced to cloud" : "On phone only";
        txtMeta.setText(dateFormat.format(new Date(r.getTimestamp())) + "  |  " + venue + "  |  " + sync);

        return convertView;
    }
}
