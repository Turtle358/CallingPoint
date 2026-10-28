package io.github.turtle358.callingpoint.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.utils.SharedJourneyLink;

public class SharedJourneyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            JourneyOption journey = SharedJourneyLink.parse(getIntent().getData());
            Intent show = new Intent(this, JourneyDetailsActivity.class);
            show.putExtra("JOURNEY_OPTION", journey);
            startActivity(show);
        } catch (Exception ex) {
            Toast.makeText(this, "This shared journey link is invalid or unsupported.",
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}