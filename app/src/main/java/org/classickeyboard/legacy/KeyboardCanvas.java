package org.classickeyboard.legacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Drawn keyboard surface. It uses only Android Canvas/View APIs and contains no remote features. */
final class KeyboardCanvas extends View {
    interface Listener {
        void onText(String text);
        void onAction(String action);
        void onCandidate(String candidate);
        void onSwipe(String keyPath);
    }

    private static final class Key {
        final String label;
        final String value;
        final String action;
        final String subLabel;
        final float weight;
        final boolean special;
        final RectF rect = new RectF();

        Key(String label, String value, String action, String subLabel, float weight, boolean special) {
            this.label = label;
            this.value = value;
            this.action = action;
            this.subLabel = subLabel;
            this.weight = weight;
            this.special = special;
        }
        boolean isLetter() { return value != null && value.length() == 1 && value.charAt(0) >= 'a' && value.charAt(0) <= 'z'; }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Key> keys = new ArrayList<>();
    private final String[] emojis = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "🙂", "🙃", "😉", "😊", "😍", "🥰", "😘", "😋",
            "😜", "🤪", "🤔", "🤨", "😎", "🤩", "🥳", "😏",
            "😢", "😭", "😤", "😡", "🤯", "😴", "🥺", "🤗",
            "👍", "👎", "👏", "🙏", "💪", "❤️", "💙", "🔥",
            "✨", "🎉", "💯", "✅", "⭐", "🌈", "☀️", "🌙"
    };

    private Listener listener;
    private List<String> candidates = new ArrayList<>();
    private boolean dark;
    private boolean shifted;
    private boolean capsLock;
    private boolean symbols;
    private boolean emojiMode;
    private boolean numericMode;
    private boolean phoneMode;
    private boolean decimalMode;
    private boolean signedMode;
    private boolean showSwitcher;
    private boolean popups = true;
    private String enterLabel = "↵";
    private float density;
    private float headerHeight;
    private Key pressedKey;
    private int headerHit = -1;
    private int emojiHit = -1;
    private float downX, downY, lastX, lastY;
    private long downTime;
    private boolean longHandled;
    private boolean repeatingDelete;
    private boolean gesture;
    private final StringBuilder gesturePath = new StringBuilder();
    private char lastGestureChar;

    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (pressedKey == null || emojiMode) return;
            if ("DELETE".equals(pressedKey.action)) {
                // Keep repeating until ACTION_UP/ACTION_CANCEL removes this callback.
                longHandled = true;
                repeatingDelete = true;
                if (listener != null) listener.onAction("DELETE");
                handler.postDelayed(this, 55);
                return;
            }
            if (longHandled) return;
            if (pressedKey.isLetter()) {
                longHandled = true;
                if (listener != null) listener.onText(accentFor(pressedKey.value.charAt(0), shifted || capsLock));
                invalidate();
            }
        }
    };

    KeyboardCanvas(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Classic keyboard");
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    void setTheme(boolean dark) { this.dark = dark; invalidate(); }
    void setShifted(boolean shifted, boolean capsLock) { this.shifted = shifted; this.capsLock = capsLock; invalidate(); }
    void setSymbols(boolean symbols) { this.symbols = symbols; this.emojiMode = false; invalidate(); }
    void setEmojiMode(boolean emojiMode) {
        this.emojiMode = emojiMode;
        if (emojiMode) candidates.clear();
        invalidate();
    }
    void setNumericMode(boolean numericMode, boolean phoneMode, boolean decimalMode, boolean signedMode) {
        this.numericMode = numericMode;
        this.phoneMode = phoneMode;
        this.decimalMode = decimalMode;
        this.signedMode = signedMode;
        this.symbols = false;
        this.emojiMode = false;
        invalidate();
    }
    void setShowSwitcher(boolean show) { showSwitcher = show; invalidate(); }
    void setPopups(boolean enabled) { popups = enabled; }
    void setEnterLabel(String label) { enterLabel = label == null ? "↵" : label; invalidate(); }
    void setCandidates(List<String> candidates) {
        this.candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
        invalidate();
    }

    private float d(float value) { return value * density; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desired = (int) d(270);
        int height = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        int board = dark ? 0xFF202124 : 0xFFD8DCE2;
        canvas.drawColor(board);
        headerHeight = d(42);
        drawHeader(canvas, w);
        keys.clear();
        if (emojiMode) {
            drawEmojiGrid(canvas, w, h);
        } else {
            buildKeyLayout(w, h);
            for (Key key : keys) drawKey(canvas, key);
        }
    }

    private void drawHeader(Canvas canvas, float width) {
        int ink = dark ? 0xFFE8EAED : 0xFF5F6368;
        int divider = dark ? 0xFF42464B : 0xFFBEC4CC;
        paint.setColor(ink);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(d(21));
        canvas.drawText(emojiMode ? "ABC" : "☺", d(24), headerHeight * 0.67f, paint);
        canvas.drawText("⚙", width - d(24), headerHeight * 0.67f, paint);
        paint.setColor(divider);
        paint.setStrokeWidth(d(1));
        canvas.drawLine(d(48), d(8), d(48), headerHeight - d(8), paint);
        canvas.drawLine(width - d(48), d(8), width - d(48), headerHeight - d(8), paint);

        if (!emojiMode && !candidates.isEmpty()) {
            float left = d(54);
            float right = width - d(54);
            float slot = (right - left) / 3f;
            paint.setTextSize(d(16));
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            for (int i = 0; i < Math.min(3, candidates.size()); i++) {
                String value = candidates.get(i);
                if (value == null) continue;
                float cx = left + slot * (i + 0.5f);
                paint.setColor(i == 0 ? (dark ? 0xFFFFFFFF : 0xFF202124) : ink);
                canvas.drawText(trimToWidth(value, slot - d(10), paint), cx, headerHeight * 0.64f, paint);
                if (i < 2) {
                    paint.setColor(divider);
                    canvas.drawLine(left + slot * (i + 1), d(10), left + slot * (i + 1), headerHeight - d(10), paint);
                }
            }
        } else if (emojiMode) {
            paint.setTextSize(d(14));
            paint.setColor(ink);
            canvas.drawText("Emoji", width / 2f, headerHeight * 0.64f, paint);
        } else {
            paint.setColor(ink);
            paint.setTextSize(d(13));
            canvas.drawText("English (US)", width / 2f, headerHeight * 0.64f, paint);
        }
        paint.setColor(divider);
        canvas.drawLine(0, headerHeight, width, headerHeight, paint);
    }

    private String trimToWidth(String text, float maxWidth, Paint textPaint) {
        if (textPaint.measureText(text) <= maxWidth) return text;
        String result = text;
        while (result.length() > 1 && textPaint.measureText(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private void buildKeyLayout(float width, float height) {
        float margin = d(3);
        float gap = d(5);
        float top = headerHeight + d(5);
        float bottom = d(5);
        float rowHeight = (height - top - bottom - gap * 3) / 4f;
        float rowWidth = width - margin * 2;
        if (numericMode) {
            addRow(Arrays.asList(
                    key("1", "1", null, null, 1, false), key("2", "2", null, null, 1, false), key("3", "3", null, null, 1, false), key("⌫", null, "DELETE", null, 1.2f, true)), margin, top, rowWidth, rowHeight, gap);
            addRow(Arrays.asList(
                    key("4", "4", null, null, 1, false), key("5", "5", null, null, 1, false), key("6", "6", null, null, 1, false),
                    phoneMode ? key("+", "+", null, null, 1.2f, true) : key("", null, null, null, 1.2f, true)), margin, top + rowHeight + gap, rowWidth, rowHeight, gap);
            addRow(Arrays.asList(
                    key("7", "7", null, null, 1, false), key("8", "8", null, null, 1, false), key("9", "9", null, null, 1, false),
                    key("", null, null, null, 1.2f, true)), margin, top + (rowHeight + gap) * 2, rowWidth, rowHeight, gap);
            addRow(Arrays.asList(
                    phoneMode ? key("*", "*", null, null, 1, true) : (signedMode ? key("−", "-", null, null, 1, true) : key("", null, null, null, 1, true)),
                    key("0", "0", null, null, 1, false),
                    phoneMode ? key("#", "#", null, null, 1, true) : (decimalMode ? key(".", ".", null, null, 1, true) : key("", null, null, null, 1, true)),
                    key(enterLabel, null, "ENTER", null, 1.2f, true)), margin, top + (rowHeight + gap) * 3, rowWidth, rowHeight, gap);
            return;
        }

        if (!symbols) {
            addLetterRow("qwertyuiop", "1234567890", margin, top, rowWidth, rowHeight, gap);
            addLetterRow("asdfghjkl", "", margin + d(15), top + rowHeight + gap, rowWidth - d(30), rowHeight, gap);
            ArrayList<Key> third = new ArrayList<>();
            third.add(key(capsLock ? "⇧" : "⇧", null, "SHIFT", capsLock ? "LOCK" : null, 1.45f, true));
            String letters = "zxcvbnm";
            for (int i = 0; i < letters.length(); i++) third.add(key(letter(letters.charAt(i)), String.valueOf(letters.charAt(i)), null, null, 1, false));
            third.add(key("⌫", null, "DELETE", null, 1.45f, true));
            addRow(third, margin, top + (rowHeight + gap) * 2, rowWidth, rowHeight, gap);
        } else {
            addSymbolRow("1234567890", margin, top, rowWidth, rowHeight, gap);
            addSymbolRow("@#$%&-*+()", margin + d(2), top + rowHeight + gap, rowWidth - d(4), rowHeight, gap);
            ArrayList<Key> third = new ArrayList<>();
            third.add(key("=\\<", "=", null, null, 1.2f, true));
            for (String symbol : new String[]{"/", ":", ";", "!", "?", "'"}) third.add(key(symbol, symbol, null, null, 1, false));
            third.add(key("⌫", null, "DELETE", null, 1.4f, true));
            addRow(third, margin, top + (rowHeight + gap) * 2, rowWidth, rowHeight, gap);
        }

        ArrayList<Key> bottomRow = new ArrayList<>();
        bottomRow.add(key(symbols ? "ABC" : "?123", null, "SYMBOLS", null, 1.35f, true));
        bottomRow.add(key(",", ",", null, "?", 0.72f, true));
        bottomRow.add(key(showSwitcher ? "🌐" : "☺", null, showSwitcher ? "SWITCH_IME" : "EMOJI", null, 0.85f, true));
        bottomRow.add(key("English (US)", " ", null, null, 4.1f, false));
        bottomRow.add(key(".", ".", null, "!", 0.72f, true));
        bottomRow.add(key(enterLabel, null, "ENTER", null, 1.35f, true));
        addRow(bottomRow, margin, top + (rowHeight + gap) * 3, rowWidth, rowHeight, gap);
    }

    private void addLetterRow(String letters, String sub, float x, float y, float width, float height, float gap) {
        ArrayList<Key> row = new ArrayList<>();
        for (int i = 0; i < letters.length(); i++) {
            String subLabel = sub.length() > i ? sub.substring(i, i + 1) : null;
            char c = letters.charAt(i);
            row.add(key(letter(c), String.valueOf(c), null, subLabel, 1f, false));
        }
        addRow(row, x, y, width, height, gap);
    }

    private void addSymbolRow(String symbols, float x, float y, float width, float height, float gap) {
        ArrayList<Key> row = new ArrayList<>();
        for (int i = 0; i < symbols.length(); i++) {
            String symbol = symbols.substring(i, i + 1);
            row.add(key(symbol, symbol, null, null, 1f, false));
        }
        addRow(row, x, y, width, height, gap);
    }

    private String letter(char c) {
        return shifted || capsLock ? String.valueOf(Character.toUpperCase(c)) : String.valueOf(c);
    }

    private Key key(String label, String value, String action, String sub, float weight, boolean special) {
        return new Key(label, value, action, sub, weight, special);
    }

    private void addRow(List<Key> row, float x, float y, float width, float height, float gap) {
        float totalWeight = 0;
        for (Key key : row) totalWeight += key.weight;
        float available = width - gap * (row.size() - 1);
        float cursor = x;
        for (Key key : row) {
            float keyWidth = available * key.weight / totalWeight;
            key.rect.set(cursor, y, cursor + keyWidth, y + height);
            keys.add(key);
            cursor += keyWidth + gap;
        }
    }

    private void drawKey(Canvas canvas, Key key) {
        boolean pressed = key == pressedKey;
        float radius = d(4);
        int fill;
        int ink;
        int subInk;
        if (key.action != null && "ENTER".equals(key.action)) {
            fill = dark ? 0xFF8AB4F8 : 0xFF9ABCF0;
            ink = 0xFF202124;
        } else if (key.special || key.action != null) {
            fill = dark ? 0xFF5F6368 : 0xFFC3C9D1;
            ink = dark ? 0xFFF1F3F4 : 0xFF30343A;
        } else {
            fill = dark ? 0xFF3C4043 : 0xFFFFFFFF;
            ink = dark ? 0xFFF1F3F4 : 0xFF202124;
        }
        subInk = dark ? 0xFFB9BEC5 : 0xFF777D86;
        if (pressed) fill = dark ? 0xFF777D85 : 0xFFE8EDF4;

        paint.setColor(dark ? 0x33000000 : 0x22000000);
        RectF shadow = new RectF(key.rect);
        shadow.offset(0, d(1.2f));
        canvas.drawRoundRect(shadow, radius, radius, paint);
        paint.setColor(fill);
        canvas.drawRoundRect(key.rect, radius, radius, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        float centerX = key.rect.centerX();
        float centerY = key.rect.centerY();
        float textSize;
        if (key.value != null && " ".equals(key.value)) textSize = d(10);
        else if (key.action != null || key.special) textSize = d(key.label.length() > 3 ? 12 : 17);
        else textSize = d(22);
        paint.setTextSize(textSize);
        paint.setColor(ink);
        String label = key.label;
        if ("English (US)".equals(label)) label = "English (US)";
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = centerY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, centerX, baseline, paint);

        if (key.subLabel != null) {
            paint.setTextSize(d(9));
            paint.setColor(subInk);
            canvas.drawText(key.subLabel, key.rect.right - d(7), key.rect.top + d(11), paint);
        }
    }

    private void drawEmojiGrid(Canvas canvas, float width, float height) {
        float top = headerHeight + d(5);
        float margin = d(4);
        int columns = 8;
        float cellWidth = (width - margin * 2) / columns;
        float cellHeight = (height - top - d(5)) / 6f;
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(d(25));
        paint.setColor(dark ? 0xFFF1F3F4 : 0xFF202124);
        for (int i = 0; i < emojis.length; i++) {
            int row = i / columns;
            int col = i % columns;
            float cx = margin + cellWidth * (col + 0.5f);
            float cy = top + cellHeight * (row + 0.5f);
            canvas.drawText(emojis[i], cx, cy + d(9), paint);
        }
    }

    private int findEmoji(float x, float y) {
        float top = headerHeight + d(5);
        float margin = d(4);
        int columns = 8;
        float cellWidth = (getWidth() - margin * 2) / columns;
        float cellHeight = (getHeight() - top - d(5)) / 6f;
        if (x < margin || x >= getWidth() - margin || y < top) return -1;
        int col = (int) ((x - margin) / cellWidth);
        int row = (int) ((y - top) / cellHeight);
        int index = row * columns + col;
        return index >= 0 && index < emojis.length ? index : -1;
    }

    private Key findKey(float x, float y) {
        for (Key key : keys) if (key.rect.contains(x, y)) return key;
        return null;
    }

    private int findHeaderHit(float x) {
        if (x < d(48)) return -2;
        if (x > getWidth() - d(48)) return -3;
        if (emojiMode) return -1;
        float left = d(54), right = getWidth() - d(54);
        if (right <= left) return -1;
        float slot = (right - left) / 3f;
        int candidate = (int) ((x - left) / slot);
        return candidate >= 0 && candidate < candidates.size() ? candidate : -1;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = x;
                downY = lastY = y;
                downTime = System.currentTimeMillis();
                longHandled = false;
                repeatingDelete = false;
                gesture = false;
                gesturePath.setLength(0);
                lastGestureChar = 0;
                if (y <= headerHeight) {
                    pressedKey = null;
                    headerHit = findHeaderHit(x);
                    invalidate();
                    return true;
                }
                headerHit = -1;
                if (emojiMode) {
                    emojiHit = findEmoji(x, y);
                    invalidate();
                    return true;
                }
                ensureLayout();
                pressedKey = findKey(x, y);
                emojiHit = -1;
                if (pressedKey != null) {
                    if (pressedKey.isLetter()) appendGestureChar(pressedKey.value.charAt(0));
                    if ("DELETE".equals(pressedKey.action) || (popups && pressedKey.isLetter())) {
                        handler.postDelayed(longPressRunnable, 430);
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (headerHit != -1 || emojiMode) return true;
                if (pressedKey != null && !longHandled) {
                    float dx = x - downX;
                    float dy = y - downY;
                    if (Math.abs(dx) + Math.abs(dy) > d(13) && !gesture && pressedKey.isLetter()
                            && Prefs.enabled(getContext(), Prefs.GLIDE, true)) {
                        gesture = true;
                        handler.removeCallbacks(longPressRunnable);
                    }
                    if (gesture) {
                        Key under = findKey(x, y);
                        if (under != null && under.isLetter()) appendGestureChar(under.value.charAt(0));
                    }
                    lastX = x;
                    lastY = y;
                }
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                if (headerHit != -1) {
                    int hit = headerHit;
                    headerHit = -1;
                    if (y <= headerHeight) {
                        if (hit == -2) {
                            if (listener != null) listener.onAction(emojiMode ? "LETTERS" : "EMOJI");
                        } else if (hit == -3) {
                            if (listener != null) listener.onAction("SETTINGS");
                        } else if (hit >= 0 && hit < candidates.size()) {
                            if (listener != null) listener.onCandidate(candidates.get(hit));
                        }
                    }
                    invalidate();
                    return true;
                }
                if (emojiMode) {
                    int index = findEmoji(x, y);
                    if (index >= 0 && listener != null) listener.onText(emojis[index]);
                    invalidate();
                    return true;
                }
                Key released = pressedKey;
                pressedKey = null;
                if (repeatingDelete) {
                    repeatingDelete = false;
                } else if (!longHandled && released != null) {
                    if (gesture && gesturePath.length() >= 3) {
                        if (listener != null) listener.onSwipe(gesturePath.toString());
                    } else {
                        activate(released);
                    }
                }
                gesture = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                pressedKey = null;
                headerHit = -1;
                emojiHit = -1;
                repeatingDelete = false;
                gesture = false;
                longHandled = false;
                invalidate();
                return true;
        }
        return true;
    }

    private void ensureLayout() {
        if (keys.isEmpty()) buildKeyLayout(getWidth(), getHeight());
    }

    private void appendGestureChar(char c) {
        if (gesturePath.length() == 0 || c != lastGestureChar) {
            gesturePath.append(c);
            lastGestureChar = c;
        }
    }

    private void activate(Key key) {
        if (listener == null) return;
        if (key.action != null) {
            if ("SYMBOLS".equals(key.action)) {
                symbols = !symbols;
                listener.onAction("SYMBOLS");
                invalidate();
            } else if ("EMOJI".equals(key.action)) {
                listener.onAction("EMOJI");
            } else if ("SWITCH_IME".equals(key.action)) {
                listener.onAction("SWITCH_IME");
            } else {
                listener.onAction(key.action);
            }
        } else if (key.value != null) {
            // Key values stay lowercase for glide matching; apply the current shift state at commit time.
            listener.onText(key.isLetter() ? letter(key.value.charAt(0)) : key.value);
        }
    }

    private static String accentFor(char c, boolean upper) {
        String result;
        switch (Character.toLowerCase(c)) {
            case 'a': result = "á"; break;
            case 'e': result = "é"; break;
            case 'i': result = "í"; break;
            case 'o': result = "ó"; break;
            case 'u': result = "ú"; break;
            case 'n': result = "ñ"; break;
            case 'c': result = "ç"; break;
            case 'y': result = "ý"; break;
            default: result = String.valueOf(c); break;
        }
        return upper ? result.toUpperCase() : result;
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
