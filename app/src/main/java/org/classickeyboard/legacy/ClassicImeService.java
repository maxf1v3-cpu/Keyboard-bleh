package org.classickeyboard.legacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline replacement IME. Uses only Android framework APIs and local dictionary assets. */
public final class ClassicImeService extends InputMethodService implements KeyboardCanvas.Listener {
    private static final Pattern CURRENT_WORD = Pattern.compile("([A-Za-z']+)$");

    private KeyboardCanvas keyboard;
    private DictionaryEngine dictionary;
    private EditorInfo editor;
    private boolean passwordField;
    private boolean suggestionsAllowed = true;
    private boolean numericMode;
    private boolean symbolsMode;
    private boolean shifted;
    private boolean capsLock;
    private boolean showSwitcher;
    private long lastShiftTap;

    @Override public void onCreate() {
        super.onCreate();
        dictionary = new DictionaryEngine();
        dictionary.load(this, () -> {
            if (keyboard != null && suggestionsAllowed) updateCandidates();
        });
    }

    @Override public View onCreateInputView() {
        showSwitcher = shouldOfferSwitchingToNextInputMethod();
        keyboard = new KeyboardCanvas(this, this);
        applyPreferences();
        applyEditorMode();
        return keyboard;
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        editor = attribute;
        resetKeyboardState();
        applyEditorMode();
        updateEnterLabel();
    }

    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        editor = attribute;
        showSwitcher = shouldOfferSwitchingToNextInputMethod();
        applyPreferences();
        applyEditorMode();
        updateEnterLabel();
        updateCandidates();
    }

    @Override public void onFinishInput() {
        super.onFinishInput();
        editor = null;
        if (keyboard != null) keyboard.setCandidates(null);
        resetKeyboardState();
    }

    @Override public void onWindowHidden() {
        super.onWindowHidden();
        if (keyboard != null) keyboard.setCandidates(null);
    }

    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                           int newSelStart, int newSelEnd,
                                           int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (keyboard != null && suggestionsAllowed) updateCandidates();
    }

    @Override public boolean onEvaluateFullscreenMode() {
        // Keeping the IME inline avoids the legacy full-screen extract editor on phones.
        return false;
    }

    @Override public void onCustomImeSwitcherButtonRequestedVisible(boolean visible) {
        showSwitcher = visible;
        if (keyboard != null) keyboard.setShowSwitcher(visible);
    }

    private void resetKeyboardState() {
        symbolsMode = false;
        shifted = true;
        capsLock = false;
        lastShiftTap = 0;
        if (keyboard != null) {
            keyboard.setSymbols(false);
            keyboard.setEmojiMode(false);
            keyboard.setShifted(shifted, capsLock);
            keyboard.setCandidates(null);
        }
    }

    private void applyPreferences() {
        if (keyboard == null) return;
        keyboard.setTheme(Prefs.dark(this));
        keyboard.setPopups(Prefs.enabled(this, Prefs.POPUPS, true));
        keyboard.setShowSwitcher(showSwitcher);
    }

    private void applyEditorMode() {
        if (keyboard == null) return;
        int inputType = editor == null ? InputType.TYPE_CLASS_TEXT : editor.inputType;
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        numericMode = inputClass == InputType.TYPE_CLASS_NUMBER
                || inputClass == InputType.TYPE_CLASS_PHONE
                || inputClass == InputType.TYPE_CLASS_DATETIME;
        boolean phoneMode = inputClass == InputType.TYPE_CLASS_PHONE;
        boolean decimalMode = inputClass == InputType.TYPE_CLASS_NUMBER
                && (inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0;
        boolean signedMode = inputClass == InputType.TYPE_CLASS_NUMBER
                && (inputType & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0;

        passwordField = (inputClass == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (inputClass == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        boolean noSuggestionsFlag = inputClass == InputType.TYPE_CLASS_TEXT
                && (inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
        boolean emailOrUri = inputClass == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_URI
                || variation == InputType.TYPE_TEXT_VARIATION_FILTER);
        suggestionsAllowed = !passwordField && !noSuggestionsFlag && !emailOrUri
                && Prefs.enabled(this, Prefs.SUGGESTIONS, true);
        keyboard.setNumericMode(numericMode, phoneMode, decimalMode, signedMode);
        keyboard.setCandidates(null);
        if (inputClass == InputType.TYPE_CLASS_TEXT && (inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
            shifted = true;
            capsLock = true;
        } else {
            shifted = shouldCapitalize();
            capsLock = false;
        }
        keyboard.setShifted(shifted, capsLock);
    }

    private void updateEnterLabel() {
        if (keyboard == null) return;
        if (editor == null) {
            keyboard.setEnterLabel("↵");
            return;
        }
        int action = editor.imeOptions & EditorInfo.IME_MASK_ACTION;
        String label;
        switch (action) {
            case EditorInfo.IME_ACTION_GO: label = "Go"; break;
            case EditorInfo.IME_ACTION_SEARCH: label = "⌕"; break;
            case EditorInfo.IME_ACTION_SEND: label = "Send"; break;
            case EditorInfo.IME_ACTION_NEXT: label = "Next"; break;
            case EditorInfo.IME_ACTION_DONE: label = "Done"; break;
            case EditorInfo.IME_ACTION_PREVIOUS: label = "Prev"; break;
            default: label = "↵"; break;
        }
        keyboard.setEnterLabel(label);
    }

    private boolean shouldCapitalize() {
        if (editor != null && (editor.inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
            CharSequence before = safeBeforeCursor(4);
            if (before == null || before.length() == 0) return true;
            String text = before.toString();
            return text.matches("(?s).*([.!?]\\s*|\\n)$");
        }
        CharSequence before = safeBeforeCursor(1);
        return before == null || before.length() == 0;
    }

    private CharSequence safeBeforeCursor(int length) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return null;
        try { return connection.getTextBeforeCursor(length, 0); }
        catch (RuntimeException ignored) { return null; }
    }

    @Override public void onText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || text == null || text.isEmpty()) return;
        keyFeedback();
        if (" ".equals(text)) {
            autocorrectBeforeSpace();
            connection.commitText(" ", 1);
            afterTextCommit(" ");
            return;
        }
        if (text.length() == 1 && isLetter(text.charAt(0))) {
            connection.commitText(text, 1);
            if (!capsLock) shifted = false;
            if (keyboard != null) keyboard.setShifted(shifted, capsLock);
            updateCandidates();
            return;
        }
        if (isSentencePunctuation(text)) autocorrectBeforeSpace();
        connection.commitText(text, 1);
        afterTextCommit(text);
    }

    private void afterTextCommit(String text) {
        if (keyboard != null) {
            if (".".equals(text) || "!".equals(text) || "?".equals(text) || "\n".equals(text)) {
                shifted = true;
                if (!capsLock) keyboard.setShifted(true, false);
            }
            updateCandidates();
        }
    }

    @Override public void onAction(String action) {
        if (action == null) return;
        switch (action) {
            case "SHIFT":
                toggleShift();
                break;
            case "DELETE":
                keyFeedback();
                deleteBeforeCursor();
                updateCandidates();
                break;
            case "SPACE":
                onText(" ");
                break;
            case "ENTER":
                performEnter();
                break;
            case "SYMBOLS":
                symbolsMode = !symbolsMode;
                if (keyboard != null) keyboard.setSymbols(symbolsMode);
                break;
            case "EMOJI":
                if (keyboard != null) keyboard.setEmojiMode(true);
                break;
            case "LETTERS":
                if (keyboard != null) keyboard.setEmojiMode(false);
                symbolsMode = false;
                if (keyboard != null) keyboard.setSymbols(false);
                break;
            case "SETTINGS":
                requestHideSelf(0);
                Intent settings = new Intent(this, SettingsActivity.class);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(settings); } catch (RuntimeException ignored) { }
                break;
            case "SWITCH_IME":
                try { switchToNextInputMethod(false); } catch (RuntimeException ignored) { }
                break;
            default:
                break;
        }
    }

    @Override public void onCandidate(String candidate) {
        if (candidate == null || candidate.isEmpty()) return;
        keyFeedback();
        replaceCurrentWord(candidate);
        updateCandidates();
    }

    @Override public void onSwipe(String keyPath) {
        if (!Prefs.enabled(this, Prefs.GLIDE, true) || passwordField || !suggestionsAllowed) return;
        if (dictionary == null) return;
        String match = dictionary.swipeMatch(keyPath);
        if (match == null) return;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        keyFeedback();
        if (capsLock) match = match.toUpperCase(java.util.Locale.US);
        else if (shifted) match = match.substring(0, 1).toUpperCase(java.util.Locale.US) + match.substring(1);
        connection.commitText(match + " ", 1);
        shifted = capsLock;
        if (keyboard != null) keyboard.setShifted(shifted, capsLock);
        updateCandidates();
    }

    private void toggleShift() {
        long now = System.currentTimeMillis();
        if (capsLock) {
            capsLock = false;
            shifted = false;
        } else if (shifted && now - lastShiftTap < 450) {
            capsLock = true;
            shifted = true;
        } else {
            shifted = !shifted;
        }
        lastShiftTap = now;
        if (keyboard != null) keyboard.setShifted(shifted, capsLock);
        keyFeedback();
    }

    private void deleteBeforeCursor() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        try {
            CharSequence selected = connection.getSelectedText(0);
            if (selected != null && selected.length() > 0) {
                connection.commitText("", 1);
                return;
            }
            CharSequence before = connection.getTextBeforeCursor(2, 0);
            if (before != null && before.length() >= 2) {
                char last = before.charAt(before.length() - 1);
                char prior = before.charAt(before.length() - 2);
                if (Character.isLowSurrogate(last) && Character.isHighSurrogate(prior)) {
                    connection.deleteSurroundingText(2, 0);
                    return;
                }
            }
            connection.deleteSurroundingText(1, 0);
        } catch (RuntimeException ignored) {
            try { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); } catch (RuntimeException ignoredAgain) { }
        }
    }

    private void performEnter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        keyFeedback();
        int action = editor == null ? EditorInfo.IME_ACTION_NONE : editor.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multiline = editor != null && (editor.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            try {
                if (connection.performEditorAction(action)) return;
            } catch (RuntimeException ignored) { }
        }
        if (multiline || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            try { connection.commitText("\n", 1); }
            catch (RuntimeException ignored) { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER); }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
        updateCandidates();
    }

    private void autocorrectBeforeSpace() {
        if (passwordField || !Prefs.enabled(this, Prefs.AUTOCORRECT, true) || dictionary == null || !dictionary.isReady()) return;
        String word = currentWord();
        if (word == null || word.length() < 3 || word.indexOf('\'') >= 0) return;
        String correction = dictionary.correction(word);
        if (correction != null && !correction.equalsIgnoreCase(word)) replaceCurrentWord(correction);
    }

    private void replaceCurrentWord(String replacement) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        CharSequence before = safeBeforeCursor(128);
        if (before == null || before.length() == 0) return;
        Matcher matcher = CURRENT_WORD.matcher(before);
        if (!matcher.find()) return;
        String oldWord = matcher.group(1);
        try {
            connection.deleteSurroundingText(oldWord.length(), 0);
            connection.commitText(replacement, 1);
        } catch (RuntimeException ignored) { }
    }

    private String currentWord() {
        CharSequence before = safeBeforeCursor(128);
        if (before == null || before.length() == 0) return null;
        Matcher matcher = CURRENT_WORD.matcher(before);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void updateCandidates() {
        if (keyboard == null) return;
        if (!suggestionsAllowed || passwordField || numericMode) {
            keyboard.setCandidates(null);
            return;
        }
        String word = currentWord();
        if (word == null || word.isEmpty() || dictionary == null || !dictionary.isReady()) {
            keyboard.setCandidates(null);
            return;
        }
        List<String> next = dictionary.suggestions(word, 3);
        keyboard.setCandidates(next);
    }

    private void keyFeedback() {
        SharedPreferences prefs = Prefs.get(this);
        if (prefs.getBoolean(Prefs.SOUND, false)) {
            try {
                AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (audio != null) audio.playSoundEffect(SoundEffectConstants.CLICK);
            } catch (RuntimeException ignored) { }
        }
        if (prefs.getBoolean(Prefs.VIBRATION, false)) {
            try {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
                    else vibrator.vibrate(12);
                }
            } catch (RuntimeException ignored) { }
        }
    }

    private static boolean isLetter(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'; }
    private static boolean isSentencePunctuation(String value) {
        return ".".equals(value) || "!".equals(value) || "?".equals(value) || ",".equals(value) || ";".equals(value);
    }
}
