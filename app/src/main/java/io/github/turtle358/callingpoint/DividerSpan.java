package io.github.turtle358.callingpoint;

import androidx.annotation.NonNull;

public class DividerSpan implements android.text.style.LineBackgroundSpan {
    private final int colour;
    private final int thickness;

    public DividerSpan(int color, int thickness) {
        this.colour = color;
        this.thickness = thickness;
    }

    @Override
    public void drawBackground(android.graphics.Canvas c, android.graphics.Paint p,
                               int left, int right, int top, int baseline, int bottom,
                               @NonNull CharSequence text, int start, int end, int lnum) {
        int oldColour = p.getColor();
        p.setColor(colour);
        float centerY = (top + bottom) / 2f;
        c.drawRect(left, centerY - (thickness / 2f), right, centerY + (thickness / 2f), p);
        p.setColor(oldColour);
    }
}
