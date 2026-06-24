package io.github.turtle358.callingpoint.ui.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.WindowCompat;
import androidx.core.graphics.ColorUtils;
import android.graphics.Color;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class TOCDetailActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Set<String> trackedTocs;
    private String currentTocCode;
    private ImageButton btnPinTocWidget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_toc_detail);

        String tocName = getIntent().getStringExtra("TOC_NAME");
        currentTocCode = getIntent().getStringExtra("TOC_CODE");
        String tocStatus = getIntent().getStringExtra("TOC_STATUS");
        String tocDescription = getIntent().getStringExtra("TOC_DESCRIPTION");
        String tocPlanned = getIntent().getStringExtra("TOC_PLANNED");

        View headerColorBar = findViewById(R.id.headerColorBar);
        TextView tvName = findViewById(R.id.detailTocName);
        TextView tvCode = findViewById(R.id.detailTocCode);
        ImageView tocIcon = findViewById(R.id.detailTocIcon);
        TextView tvStatus = findViewById(R.id.detailTocStatus);
        btnPinTocWidget = findViewById(R.id.btnPinTocWidget);

        View sectionActiveIncidents = findViewById(R.id.sectionActiveIncidents);
        View sectionPlannedWorks = findViewById(R.id.sectionPlannedWorks);
        LinearLayout layoutActiveIncidents = findViewById(R.id.layoutActiveIncidents);
        LinearLayout layoutPlannedWorks = findViewById(R.id.layoutPlannedWorks);

        tvName.setText(tocName != null ? tocName : "Unknown Operator");
        tvCode.setText(currentTocCode != null ? currentTocCode : "--");
        tvStatus.setText(tocStatus != null ? tocStatus : "Status Unavailable");

        prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String savedTocs = prefs.getString("WIDGET_TRACKED_TOCS", "");
        trackedTocs = new HashSet<>();
        if (!savedTocs.trim().isEmpty()) {
            trackedTocs.addAll(Arrays.asList(savedTocs.split(",")));
        }

        updatePinIcon();

        btnPinTocWidget.setOnClickListener(v -> {
            if (currentTocCode == null || currentTocCode.equals("--")) return;

            boolean isNowPinned;
            if (trackedTocs.contains(currentTocCode)) {
                trackedTocs.remove(currentTocCode);
                isNowPinned = false;
            } else {
                trackedTocs.add(currentTocCode);
                isNowPinned = true;
            }

            saveTrackedTocs();
            updatePinIcon();

            String message = isNowPinned ? "Added to widget" : "Removed from widget";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });

        if (tocName != null) {
            int brandColor = TOCBrandHelper.getColourForToc(tocName);
            headerColorBar.setBackgroundColor(brandColor);
            tvCode.setBackgroundTintList(ColorStateList.valueOf(brandColor));
            tocIcon.setImageResource(TOCBrandHelper.getIconForToc(tocName));

            boolean isLightColor = ColorUtils.calculateLuminance(brandColor) > 0.5;
            tvCode.setTextColor(isLightColor ? Color.BLACK : Color.WHITE);
        }

        if (tocDescription != null && !tocDescription.trim().isEmpty()) {
            populateIncidents(layoutActiveIncidents, tocDescription);
            sectionActiveIncidents.setVisibility(View.VISIBLE);
        } else {
            sectionActiveIncidents.setVisibility(View.GONE);
        }

        if (tocPlanned != null && !tocPlanned.trim().isEmpty()) {
            populateIncidents(layoutPlannedWorks, tocPlanned);
            sectionPlannedWorks.setVisibility(View.VISIBLE);
        } else {
            sectionPlannedWorks.setVisibility(View.GONE);
        }
    }

    private void populateIncidents(LinearLayout container, String htmlText) {
        if (htmlText == null || htmlText.isEmpty()) return;

        String[] incidents = htmlText.split("<hr>");
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < incidents.length; i++) {
            String chunk = incidents[i].trim();

            chunk = chunk.replaceAll("^(?:<br\\s*/?>\\s*)+", "");
            chunk = chunk.replaceAll("(?:<br\\s*/?>\\s*)+$", "");

            if (chunk.isEmpty()) continue;

            String vimeoId = extractVimeoId(chunk);

            chunk = chunk.replaceAll("<a[^>]*vimeo\\.com/\\d+[^>]*>.*?</a>", "");
            chunk = chunk.replaceAll("https?://(?:www\\.)?vimeo\\.com/\\d+", "");

            androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins((int)(12 * density), 0, (int)(12 * density), (int)(12 * density));
            card.setLayoutParams(cardParams);
            card.setRadius(16 * density);
            card.setCardElevation(2 * density);
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_card));

            card.setUseCompatPadding(true);

            LinearLayout innerLayout = new LinearLayout(this);
            innerLayout.setOrientation(LinearLayout.VERTICAL);
            innerLayout.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(16 * density));

            if (vimeoId != null) {
                WebView webView = new WebView(this);
                LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (int) (220 * density)
                );
                wvParams.setMargins(0, 0, 0, (int) (12 * density));
                webView.setLayoutParams(wvParams);
                setupVideoPlayer(webView, vimeoId);
                innerLayout.addView(webView);
            }
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            int secondaryColor = ContextCompat.getColor(this, R.color.text_secondary);
            tv.setTextColor(secondaryColor);
            tv.setLinkTextColor(android.graphics.Color.parseColor("#0A56F1"));
            tv.setTextSize(15f);
            tv.setLineSpacing(0, 1.2f);
            tv.setMovementMethod(LinkMovementMethod.getInstance());
            tv.setText(HtmlCompat.fromHtml(chunk, HtmlCompat.FROM_HTML_MODE_COMPACT));

            innerLayout.addView(tv);
            card.addView(innerLayout);
            container.addView(card);
        }
    }

    private void updatePinIcon() {
        if (currentTocCode != null && trackedTocs.contains(currentTocCode)) {
            btnPinTocWidget.setImageResource(R.drawable.ic_bookmark);
        } else {
            btnPinTocWidget.setImageResource(R.drawable.ic_bookmark_outline);
        }
    }

    private void saveTrackedTocs() {
        String joined = TextUtils.join(",", trackedTocs);
        prefs.edit()
                .putString("WIDGET_TRACKED_TOCS", joined)
                .putLong("LAST_SYNC_TIME", 0)
                .apply();

        io.github.turtle358.callingpoint.utils.DisruptionSyncManager.syncAsync(this, null);
    }

    private String extractVimeoId(String text) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile("vimeo\\.com/(\\d+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void setupVideoPlayer(WebView webView, String videoId) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());

        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                "  <style>" +
                "    html, body { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000000; overflow: hidden; }" +
                "    iframe { width: 100%; height: 100%; border: none; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <iframe src=\"https://player.vimeo.com/video/" + videoId + "?autoplay=0&muted=0\" allow=\"autoplay; fullscreen; picture-in-picture\" allowfullscreen></iframe>" +
                "</body>" +
                "</html>";
        webView.post(() -> webView.loadDataWithBaseURL("https://vimeo.com", html, "text/html", "UTF-8", null));
    }
}