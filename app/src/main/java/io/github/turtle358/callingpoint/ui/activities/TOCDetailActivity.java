package io.github.turtle358.callingpoint.ui.activities;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.turtle358.callingpoint.utils.DividerSpan;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class TOCDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_toc_detail);

        String tocName = getIntent().getStringExtra("TOC_NAME");
        String tocCode = getIntent().getStringExtra("TOC_CODE");
        String tocStatus = getIntent().getStringExtra("TOC_STATUS");
        String tocDescription = getIntent().getStringExtra("TOC_DESCRIPTION");
        String tocPlanned = getIntent().getStringExtra("TOC_PLANNED");

        View headerColorBar = findViewById(R.id.headerColorBar);
        TextView tvName = findViewById(R.id.detailTocName);
        TextView tvCode = findViewById(R.id.detailTocCode);
        ImageView tocIcon = findViewById(R.id.detailTocIcon);
        TextView tvStatus = findViewById(R.id.detailTocStatus);

        View cardActiveIncidents = findViewById(R.id.cardActiveIncidents);
        View cardPlannedWorks = findViewById(R.id.cardPlannedWorks);

        TextView tvDetailText = findViewById(R.id.detailIncidentsText);
        TextView tvPlannedText = findViewById(R.id.detailPlannedText);
        WebView activeVideoWebView = findViewById(R.id.activeVideoWebView);
        WebView plannedVideoWebView = findViewById(R.id.plannedVideoWebView);

        tvName.setText(tocName != null ? tocName : "Unknown Operator");
        tvCode.setText(tocCode != null ? tocCode : "--");
        tvStatus.setText(tocStatus != null ? tocStatus : "Status Unavailable");

        if (tocName != null) {
            int brandColor = TOCBrandHelper.getColourForToc(tocName);
            headerColorBar.setBackgroundColor(brandColor);
            tvCode.setBackgroundTintList(ColorStateList.valueOf(brandColor));
            tocIcon.setImageResource(TOCBrandHelper.getIconForToc(tocName));
        }

        if (tocDescription != null && !tocDescription.trim().isEmpty()) {
            setHtmlWithDividers(tvDetailText, tocDescription);
            String vimeoId = extractVimeoId(tocDescription);
            if (vimeoId != null) {
                setupVideoPlayer(activeVideoWebView, vimeoId);
            }
        } else {
            cardActiveIncidents.setVisibility(View.GONE);
        }

        if (tocPlanned != null && !tocPlanned.trim().isEmpty()) {
            setHtmlWithDividers(tvPlannedText, tocPlanned);

            String vimeoIdPlanned = extractVimeoId(tocPlanned);
            if (vimeoIdPlanned != null) {
                setupVideoPlayer(plannedVideoWebView, vimeoIdPlanned);
            }
        } else {
            cardPlannedWorks.setVisibility(View.GONE);
        }
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
        webView.setVisibility(View.VISIBLE);
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

    private void setHtmlWithDividers(TextView textView, String htmlText) {
        if (htmlText == null) return;
        String processedHtml = htmlText.replace("<hr>", "___DIVIDER___");

        android.text.Spanned spanned = Html.fromHtml(processedHtml, Html.FROM_HTML_MODE_COMPACT);
        android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder(spanned);
        String marker = "___DIVIDER___";
        int start = builder.toString().indexOf(marker);

        while (start >= 0) {
            int end = start + marker.length();
            builder.replace(start, end, " \n\n");
            builder.setSpan(new DividerSpan(android.graphics.Color.parseColor("#D1D1D6"), 5),
                    start, start + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = builder.toString().indexOf(marker, start + 1);
        }

        textView.setText(builder);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}

