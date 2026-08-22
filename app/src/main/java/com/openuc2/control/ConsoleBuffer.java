package com.openuc2.control;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

/**
 * Bounded, colourised text buffer for the serial console.
 *
 * The previous version appended straight into the TextView, which meant a
 * chatty board filled the view until the app died. Here the buffer is capped
 * and trimmed from the front at a line boundary, so memory stays flat no matter
 * how long a session runs.
 */
public class ConsoleBuffer {

    /** Roughly 800 lines of JSON. Past this the oldest output is dropped. */
    private static final int MAX_CHARS = 60_000;
    private static final int TRIM_TO = 45_000;

    private final SpannableStringBuilder buffer = new SpannableStringBuilder();

    /** True when the last appended chunk did not end a line. */
    private boolean midLine = false;

    public synchronized void append(String text, int color) {
        if (text == null || text.isEmpty()) return;
        int start = buffer.length();
        buffer.append(text);
        buffer.setSpan(new ForegroundColorSpan(color), start, buffer.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        midLine = !text.endsWith("\n");
        trim();
    }

    /** Append on its own line, starting a new one first if needed. */
    public synchronized void appendLine(String text, int color) {
        if (midLine) append("\n", color);
        append(text.endsWith("\n") ? text : text + "\n", color);
    }

    private void trim() {
        if (buffer.length() <= MAX_CHARS) return;
        int cut = buffer.length() - TRIM_TO;
        // Cut at the next line break so we never leave half a JSON object.
        int nl = -1;
        for (int i = cut; i < Math.min(buffer.length(), cut + 500); i++) {
            if (buffer.charAt(i) == '\n') { nl = i + 1; break; }
        }
        buffer.delete(0, nl > 0 ? nl : cut);
    }

    public synchronized CharSequence text() {
        return buffer;
    }

    public synchronized String plainText() {
        return buffer.toString();
    }

    public synchronized void clear() {
        buffer.clear();
        buffer.clearSpans();
        midLine = false;
    }

    public synchronized boolean isEmpty() {
        return buffer.length() == 0;
    }
}
