package io.github.turtle358.callingpoint.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AutoCompleteTextView;

import java.util.List;

public class InlineAutocompleteManager {
    public static void applyInlineAutocomplete(AutoCompleteTextView editText) {
        editText.addTextChangedListener(new TextWatcher() {
            boolean isTyping = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isTyping = before < count;
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isTyping || s.toString().isEmpty()) return;

                String input = s.toString();
                List<String> stations = StationHelper.getStationNames();

                for (String station : stations) {
                    if (station.equalsIgnoreCase(input)) {
                        return;
                    }
                }

                String bestMatch = null;

                for (String station : stations) {
                    if (station.toLowerCase().startsWith(input.toLowerCase()) && !station.equalsIgnoreCase(input)) {
                        if (bestMatch == null || station.length() < bestMatch.length()) {
                            bestMatch = station;
                        }
                    }
                }

                if (bestMatch != null) {
                    editText.removeTextChangedListener(this);
                    editText.setText(bestMatch);
                    editText.setSelection(input.length(), bestMatch.length());
                    editText.addTextChangedListener(this);
                }
            }
        });
    }
}
