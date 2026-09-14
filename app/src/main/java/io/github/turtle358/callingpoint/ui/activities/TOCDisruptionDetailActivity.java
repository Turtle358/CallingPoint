package io.github.turtle358.callingpoint.ui.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
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
import android.graphics.drawable.GradientDrawable;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.IncidentResponse;
import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSeverityManager;
import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSeverityManager.Severity;
import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSyncManager;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class TOCDisruptionDetailActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Set<String> trackedTocs;
    private String currentTocCode;
    private ImageButton btnPinTocWidget;
    boolean isLightColour;

    int brandColour;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toc_disruption_detail);

        View topHeaderBar = findViewById(R.id.topHeaderBar);
        if (topHeaderBar != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topHeaderBar, (v, insets) -> {
                int statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                v.getLayoutParams().height = statusBarHeight;
                v.requestLayout();
                return insets;
            });
        }

        String tocName = getIntent().getStringExtra("TOC_NAME");
        currentTocCode = getIntent().getStringExtra("TOC_CODE");
        String tocStatus = getIntent().getStringExtra("TOC_STATUS");

        brandColour = TOCBrandHelper.getColourForToc(tocName);

        if (topHeaderBar != null) {
            topHeaderBar.setBackgroundColor(brandColour);
        }

        isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(isLightColour);

        String activeJson = getIntent().getStringExtra("TOC_ACTIVE_JSON");
        String plannedJson = getIntent().getStringExtra("TOC_PLANNED_JSON");

        TextView tvName = findViewById(R.id.detailTocName);
        ImageView tocIcon = findViewById(R.id.detailTocIcon);
        TextView tvStatus = findViewById(R.id.detailTocStatus);
        btnPinTocWidget = findViewById(R.id.btnPinTocWidget);

        View sectionActiveIncidents = findViewById(R.id.sectionActiveIncidents);
        View sectionPlannedWorks = findViewById(R.id.sectionPlannedWorks);
        LinearLayout layoutActiveIncidents = findViewById(R.id.layoutActiveIncidents);
        LinearLayout layoutPlannedWorks = findViewById(R.id.layoutPlannedWorks);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        tvName.setText(tocName != null ? tocName : "Unknown Operator");
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
            tocIcon.setImageResource(TOCBrandHelper.getIconForToc(tocName));
            tocIcon.setOnClickListener(v ->
                    TOCBrandHelper.showTOCInfoDialog(this, tocName)
            );
        }

        Gson gson = new Gson();
        Type listType = new TypeToken<List<IncidentResponse>>(){}.getType();

        List<IncidentResponse> activeIncidents = gson.fromJson(activeJson, listType);
        List<IncidentResponse> plannedIncidents = gson.fromJson(plannedJson, listType);

        if (activeIncidents != null) {
            activeIncidents.sort((a, b) -> {
                int priorityA = DisruptionSeverityManager.classify(a.summary, a.description).priority;
                int priorityB = DisruptionSeverityManager.classify(b.summary, b.description).priority;
                return Integer.compare(priorityB, priorityA);
            });
        }

        if (plannedIncidents != null) {
            plannedIncidents.sort((a, b) -> {
                int priorityA = DisruptionSeverityManager.classify(a.summary, a.description).priority;
                int priorityB = DisruptionSeverityManager.classify(b.summary, b.description).priority;
                return Integer.compare(priorityB, priorityA);
            });
        }

        int totalValidItems = countValidIncidents(activeIncidents) + countValidIncidents(plannedIncidents);
        boolean autoExpand = (totalValidItems == 1);

        if (activeIncidents != null && !activeIncidents.isEmpty()) {
            populateIncidents(layoutActiveIncidents, activeIncidents, autoExpand);
            sectionActiveIncidents.setVisibility(View.VISIBLE);
        } else {
            sectionActiveIncidents.setVisibility(View.GONE);
        }

        if (plannedIncidents != null && !plannedIncidents.isEmpty()) {
            populateIncidents(layoutPlannedWorks, plannedIncidents, autoExpand);
            sectionPlannedWorks.setVisibility(View.VISIBLE);
        } else {
            sectionPlannedWorks.setVisibility(View.GONE);
        }
    }

    private int countValidIncidents(List<IncidentResponse> incidents) {
        if (incidents == null) return 0;
        int count = 0;
        for (IncidentResponse incident : incidents) {
            if (incident.description != null && !incident.description.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private void populateIncidents(LinearLayout container, List<IncidentResponse> incidents, boolean autoExpand) {
        if (incidents == null || incidents.isEmpty()) return;

        float density = getResources().getDisplayMetrics().density;

        for (IncidentResponse incident : incidents) {
            if (incident.description == null || incident.description.trim().isEmpty()) continue;

            String chunk = incident.description.trim();
            chunk = chunk.replaceAll("^(?:<br\\s*/?>\\s*)+", "");
            chunk = chunk.replaceAll("(?:<br\\s*/?>\\s*)+$", "");
            String vimeoId = extractVimeoId(chunk);
            chunk = chunk.replaceAll("<a[^>]*vimeo\\.com/\\d+[^>]*>.*?</a>", "");
            chunk = chunk.replaceAll("https?://(?:www\\.)?vimeo\\.com/\\d+", "");

            Severity severity = DisruptionSeverityManager.classify(incident.summary, incident.description);

            androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins((int)(12 * density), 0, (int)(12 * density), (int)(8 * density));
            card.setLayoutParams(cardParams);

            card.setRadius(28 * density);
            card.setCardElevation(2 * density);
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_card));
            card.setUseCompatPadding(true);

            LinearLayout innerLayout = new LinearLayout(this);
            innerLayout.setOrientation(LinearLayout.VERTICAL);
            innerLayout.setPadding((int)(20 * density), (int)(20 * density), (int)(20 * density), (int)(20 * density));

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout bannerContainer = new LinearLayout(this);
            bannerContainer.setOrientation(LinearLayout.HORIZONTAL);
            bannerContainer.setGravity(Gravity.CENTER_VERTICAL);

            GradientDrawable bannerBg = new GradientDrawable();
            bannerBg.setColor(ColorUtils.setAlphaComponent(severity.accentColor, 38));
            bannerBg.setCornerRadius(8 * density);
            bannerContainer.setBackground(bannerBg);
            bannerContainer.setPadding((int)(8 * density), (int)(4 * density), (int)(8 * density), (int)(4 * density));

            TextView badgeSeverity = new TextView(this);
            badgeSeverity.setText(severity.statusString.toUpperCase());
            badgeSeverity.setTextSize(11f);
            badgeSeverity.setTypeface(null, android.graphics.Typeface.BOLD);
            badgeSeverity.setTextColor(severity.accentColor);
            badgeSeverity.setLetterSpacing(0.03f);
            bannerContainer.addView(badgeSeverity);

            LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            topRow.addView(bannerContainer, bannerParams);

            View spacer = new View(this);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 0, 1f);
            topRow.addView(spacer, spacerParams);

            ImageView arrowIcon = new ImageView(this);
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams((int)(24 * density), (int)(24 * density));
            arrowParams.setMargins((int)(12 * density), 0, 0, 0);
            arrowIcon.setLayoutParams(arrowParams);
            arrowIcon.setImageResource(R.drawable.ic_chevron_down);
            arrowIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
            topRow.addView(arrowIcon);

            innerLayout.addView(topRow);

            TextView tvSummary = new TextView(this);
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            summaryParams.setMargins(0, (int)(8 * density), 0, 0);
            tvSummary.setLayoutParams(summaryParams);
            tvSummary.setText(incident.summary != null ? incident.summary : "Service Update");
            tvSummary.setTextSize(16f);
            tvSummary.setTypeface(null, android.graphics.Typeface.BOLD);
            tvSummary.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvSummary.setLineSpacing(0, 1.2f);
            innerLayout.addView(tvSummary);

            if (incident.affectedOperators != null && !incident.affectedOperators.isEmpty()) {
                android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
                hsv.setHorizontalScrollBarEnabled(false);

                LinearLayout.LayoutParams hsvParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                hsvParams.setMargins(0, (int)(12 * density), 0, 0);
                hsv.setLayoutParams(hsvParams);

                LinearLayout pillContainer = new LinearLayout(this);
                pillContainer.setOrientation(LinearLayout.HORIZONTAL);

                for (IncidentResponse.AffectedOperator op : incident.affectedOperators) {
                    TextView pill = new TextView(this);
                    String pillAcronym = TOCBrandHelper.getAcronymForCode(op.tocCode);
                    pill.setText(pillAcronym != null ? pillAcronym : op.tocCode);

                    pill.setTextSize(10f);
                    pill.setTypeface(null, android.graphics.Typeface.BOLD);
                    pill.setPadding((int)(6 * density), (int)(2 * density), (int)(6 * density), (int)(2 * density));

                    String opTocName = TOCBrandHelper.getNameForCode(op.tocCode);
                    int primaryColour = TOCBrandHelper.getColourForToc(opTocName);
                    int secondaryColour = TOCBrandHelper.getSecondaryColourForToc(opTocName);

                    GradientDrawable gd = new GradientDrawable();
                    gd.setColor(primaryColour);
                    gd.setCornerRadius(7 * density);
                    pill.setBackground(gd);
                    pill.setTextColor(secondaryColour);

                    LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    pillParams.setMargins(0, 0, (int)(6 * density), 0);
                    pillContainer.addView(pill, pillParams);
                }

                hsv.addView(pillContainer);
                innerLayout.addView(hsv);
            }

            if (vimeoId != null) {
                WebView webView = new WebView(this);
                LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (int) (200 * density)
                );
                wvParams.setMargins(0, (int) (12 * density), 0, 0);
                webView.setLayoutParams(wvParams);
                webView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12 * density);
                    }
                });
                webView.setClipToOutline(true);
                webView.post(() -> {
                    int actualWidth = webView.getWidth();
                    if (actualWidth > 0) {
                        int perfectHeight = (int) (actualWidth * (9.0f / 16.0f));

                        if (webView.getHeight() != perfectHeight) {
                            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) webView.getLayoutParams();
                            params.height = perfectHeight;
                            webView.setLayoutParams(params);
                        }
                    }
                });

                setupVideoPlayer(webView, vimeoId);
                innerLayout.addView(webView);
            }

            LinearLayout descriptionContainer = new LinearLayout(this);
            descriptionContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            bodyParams.setMargins(0, (int)(12 * density), 0, 0);
            descriptionContainer.setLayoutParams(bodyParams);
            descriptionContainer.setVisibility(autoExpand ? View.VISIBLE : View.GONE);

            if (autoExpand) {
                arrowIcon.setRotation(180f);
            }

            buildSmartDescription(descriptionContainer, chunk, density);
            innerLayout.addView(descriptionContainer);

            card.addView(innerLayout);
            container.addView(card);

            final boolean[] isExpanded = {autoExpand};
            View.OnClickListener toggleListener = v -> {
                android.transition.TransitionSet transition = new android.transition.TransitionSet();
                transition.addTransition(new android.transition.Fade(android.transition.Fade.OUT).setDuration(100));
                transition.addTransition(new android.transition.ChangeBounds().setDuration(250));
                transition.addTransition(new android.transition.Fade(android.transition.Fade.IN).setDuration(150).setStartDelay(100));

                android.view.ViewParent parentNode = container.getParent();
                android.widget.ScrollView scrollView = null;
                while (parentNode != null) {
                    if (parentNode instanceof android.widget.ScrollView) {
                        scrollView = (android.widget.ScrollView) parentNode;
                        break;
                    }
                    parentNode = parentNode.getParent();
                }

                if (scrollView != null) {
                    android.transition.TransitionManager.beginDelayedTransition(scrollView, transition);
                } else {
                    android.transition.TransitionManager.beginDelayedTransition((android.view.ViewGroup) getWindow().getDecorView(), transition);
                }

                isExpanded[0] = !isExpanded[0];
                if (isExpanded[0]) {
                    descriptionContainer.setVisibility(View.VISIBLE);
                    arrowIcon.animate().rotation(180f).setDuration(250).start();
                } else {
                    descriptionContainer.setVisibility(View.GONE);
                    arrowIcon.animate().rotation(0f).setDuration(250).start();

                    if (scrollView != null) {
                        final android.widget.ScrollView finalScrollView = scrollView;
                        finalScrollView.post(() -> {
                            android.graphics.Rect rect = new android.graphics.Rect();
                            card.getHitRect(rect);
                            finalScrollView.offsetDescendantRectToMyCoords(card, rect);

                            int topMargin = (int) (12 * density);
                            int targetY = rect.top - topMargin;

                            if (finalScrollView.getScrollY() > targetY) {
                                finalScrollView.smoothScrollTo(0, targetY);
                            }
                        });
                    }
                }
            };

            card.setOnClickListener(toggleListener);
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

        DisruptionSyncManager.syncAsync(this, null);
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
        webView.setBackgroundColor(Color.TRANSPARENT);

        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                "  <style>" +
                "    html, body { margin: 0; padding: 0; width: 100%; height: 100%; background-color: transparent; overflow: hidden; }" +
                "    iframe { width: 100%; height: 100%; border: none; }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <iframe src=\"https://player.vimeo.com/video/" + videoId + "?autoplay=0&muted=0&title=0&byline=0&portrait=0&transparent=1\" allow=\"autoplay; fullscreen; picture-in-picture\" allowfullscreen></iframe>" +
                "</body>" +
                "</html>";
        webView.post(() -> webView.loadDataWithBaseURL("https://vimeo.com", html, "text/html", "UTF-8", null));
    }

    private void buildSmartDescription(LinearLayout container, String htmlContent, float density) {
        Pattern pattern = Pattern.compile(
                "(?i)(?:" +
                        "<h4>\\s*(?:<b>|<strong>)?\\s*(.*?)\\s*(?:</b>|</strong>)?\\s*:?\\s*</h4>" +
                        "|" +
                        "(?:^|</p>|<p(?:\\s[^>]*)?>|<br\\s*/?>)\\s*(?:<b>|<strong>)\\s*([^<]{1,80}?[:?])\\s*(?:</b>|</strong>)" +
                        ")\\s*(?:<br\\s*/?>)*"
        );
        Matcher matcher = pattern.matcher(htmlContent);

        int lastEnd = 0;
        String currentHeader = "Overview";
        List<ParsedSection> rawSections = new ArrayList<>();

        while (matcher.find()) {
            String sectionContent = htmlContent.substring(lastEnd, matcher.start()).trim();
            if (!sectionContent.isEmpty() && !sectionContent.matches("^(?:<br\\s*/?>\\s*)+$")) {
                rawSections.add(new ParsedSection(currentHeader, sectionContent));
            }

            String h4Header = matcher.group(1);
            String boldHeader = matcher.group(2);
            currentHeader = (h4Header != null ? h4Header : boldHeader).trim();
            lastEnd = matcher.end();
        }

        String finalContent = htmlContent.substring(lastEnd).trim();
        if (!finalContent.isEmpty() && !finalContent.matches("^(?:<br\\s*/?>\\s*)+$")) {
            rawSections.add(new ParsedSection(currentHeader, finalContent));
        }

        LinkedHashMap<String, List<ParsedSection>> grouped = new LinkedHashMap<>();
        Map<String, CategoryStyle> styleByKey = new java.util.HashMap<>();

        for (ParsedSection section : rawSections) {
            String cleanHeader = section.rawHeader != null
                    ? section.rawHeader.replaceAll(":\\s*$", "").trim()
                    : "Overview";
            CategoryStyle style = classifyHeader(cleanHeader);

            grouped.computeIfAbsent(style.title, k -> new ArrayList<>())
                    .add(new ParsedSection(cleanHeader, section.html));
            styleByKey.putIfAbsent(style.title, style);
        }

        for (Map.Entry<String, List<ParsedSection>> entry : grouped.entrySet()) {
            addDescriptionCard(container, styleByKey.get(entry.getKey()), entry.getValue(), density);
        }
    }

    private void addDescriptionCard(LinearLayout container, CategoryStyle style, List<ParsedSection> sections, float density) {
        LinearLayout subCard = new LinearLayout(this);
        subCard.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ColorUtils.setAlphaComponent(style.accentColor, 22));
        bg.setStroke((int) (1 * density), ColorUtils.setAlphaComponent(style.accentColor, 110));
        bg.setCornerRadius(24 * density);
        subCard.setBackground(bg);
        subCard.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));

        LinearLayout.LayoutParams subCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subCardParams.setMargins(0, 0, 0, (int) (20 * density));
        subCard.setLayoutParams(subCardParams);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(style.iconRes);
        icon.setColorFilter(style.accentColor);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams((int) (18 * density), (int) (18 * density));
        iconParams.setMargins(0, 0, (int) (8 * density), 0);
        headerRow.addView(icon, iconParams);

        TextView titleView = new TextView(this);
        titleView.setText(style.title);
        titleView.setTextSize(14f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(style.accentColor);
        headerRow.addView(titleView);

        subCard.addView(headerRow);

        boolean showSubheaders = sections.size() > 1;

        for (int i = 0; i < sections.size(); i++) {
            ParsedSection section = sections.get(i);

            if (showSubheaders) {
                TextView subheaderView = new TextView(this);
                subheaderView.setText(section.rawHeader);
                subheaderView.setTextSize(12.5f);
                subheaderView.setTypeface(null, android.graphics.Typeface.BOLD);
                subheaderView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                subheaderView.setAlpha(0.85f);

                LinearLayout.LayoutParams subheaderParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                subheaderParams.setMargins(0, (int) ((i == 0 ? 8 : 16) * density), 0, 0);
                subCard.addView(subheaderView, subheaderParams);
            }

            TextView bodyView = new TextView(this);
            bodyView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            bodyView.setLinkTextColor(Color.parseColor("#0A56F1"));
            bodyView.setTextSize(13.5f);
            bodyView.setLineSpacing(0, 1.2f);
            bodyView.setMovementMethod(LinkMovementMethod.getInstance());
            bodyView.setText(HtmlCompat.fromHtml(section.html, HtmlCompat.FROM_HTML_MODE_COMPACT));

            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bodyParams.setMargins(0, (int) ((showSubheaders ? 4 : 8) * density), 0, 0);
            subCard.addView(bodyView, bodyParams);
        }

        container.addView(subCard);
    }

    private CategoryStyle classifyHeader(String cleanHeader) {
        String lowerHeader = cleanHeader.toLowerCase();

        String simplifiedTitle = cleanHeader;
        int accentColor = Color.parseColor("#78909C");
        int iconRes = R.drawable.ic_bookmark_outline;

        if (lowerHeader.equals("overview") || lowerHeader.contains("summary")) {
            simplifiedTitle = "Overview";
            accentColor = Color.parseColor("#29B6F6");
            iconRes = R.drawable.ic_summary_stars;
        } else if (lowerHeader.contains("advice")) {
            simplifiedTitle = cleanHeader;
            accentColor = Color.parseColor("#5C6BC0");
            iconRes = R.drawable.ic_lightbulb;
        } else if ( lowerHeader.contains("additional") || lowerHeader.contains("happening")) {
            simplifiedTitle = cleanHeader;
            accentColor = Color.parseColor("#0288D1");
            iconRes = R.drawable.ic_info;
        } else if (lowerHeader.contains("bus") || lowerHeader.contains("coach")) {
            simplifiedTitle = cleanHeader.contains(":") ? cleanHeader : "Replacement Buses";
            accentColor = Color.parseColor("#FFA726");
            iconRes = R.drawable.ic_bus;
        } else if (lowerHeader.contains("ticket")) {
            simplifiedTitle = "Ticket Acceptance";
            accentColor = Color.parseColor("#FF7043");
            iconRes = R.drawable.ic_ticket;
        } else if (lowerHeader.contains("compensation")) {
            simplifiedTitle = "Compensation & Refunds";
            accentColor = Color.parseColor("#FF7043");
            iconRes = R.drawable.ic_money;
        } else if (lowerHeader.contains("alternative") || lowerHeader.contains("planning") || lowerHeader.contains("check before")) {
            simplifiedTitle = "Alternative Routes & Planning";
            accentColor = Color.parseColor("#66BB6A");
            iconRes = R.drawable.ic_smartphone;
        } else if (lowerHeader.contains("tell me more") || lowerHeader.contains("why") || lowerHeader.contains("cause")) {
            simplifiedTitle = "Tell me more about this";
            accentColor = Color.parseColor("#26A69A");
            iconRes = R.drawable.ic_question_mark;
        } else if (lowerHeader.startsWith("after") || lowerHeader.startsWith("before") || lowerHeader.contains("today") || lowerHeader.contains("timetable")) {
            simplifiedTitle = cleanHeader;
            accentColor = Color.parseColor("#AB47BC");
            iconRes = R.drawable.ic_clock;
        } else if (lowerHeader.contains("parking") || lowerHeader.contains("station")) {
            simplifiedTitle = "Parking";
            accentColor = Color.parseColor("#8D6E63");
            iconRes = R.drawable.ic_parking;
        } else if (lowerHeader.contains("services") || TOCBrandHelper.containsTocName(lowerHeader)) {
            simplifiedTitle = cleanHeader;
            String matchedToc = TOCBrandHelper.getContainedName(lowerHeader);
            int rawColour = matchedToc != null
                    ? TOCBrandHelper.getColourForToc(matchedToc)
                    : Color.parseColor("#c84c53");
            accentColor = TOCBrandHelper.brightenIfDark(rawColour, 0.55f);
            iconRes = R.drawable.ic_train;
        } else if (lowerHeader.contains("day") || lowerHeader.contains("weekends")) {
            simplifiedTitle = cleanHeader;
            accentColor = Color.parseColor("#0A56F1");
            iconRes = R.drawable.ic_calendar;
        } else if (lowerHeader.contains("industrial action")) {
            simplifiedTitle = cleanHeader;
            accentColor = Color.parseColor("#E056FD");
            iconRes = R.drawable.ic_fire;
        } else if (lowerHeader.contains("faq")) {
            simplifiedTitle = cleanHeader;
            accentColor = Color.parseColor("#d5b20c");
            iconRes = R.drawable.ic_question_mark;
        }

        return new CategoryStyle(simplifiedTitle, accentColor, iconRes);
    }

    private static class ParsedSection {
        final String rawHeader;
        final String html;
        ParsedSection(String rawHeader, String html) {
            this.rawHeader = rawHeader;
            this.html = html;
        }
    }

    private static class CategoryStyle {
        final String title;
        final int accentColor;
        final int iconRes;
        CategoryStyle(String title, int accentColor, int iconRes) {
            this.title = title;
            this.accentColor = accentColor;
            this.iconRes = iconRes;
        }
    }
}