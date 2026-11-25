package org.classickeyboard.legacy;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A small, entirely local English word suggester. No network or remote service is used. */
final class DictionaryEngine {
    private final Set<String> words = new HashSet<>();
    private final Map<Character, ArrayList<String>> byFirstLetter = new HashMap<>();
    private final Map<String, ArrayList<String>> byPrefix = new HashMap<>();
    private final Map<String, Integer> commonRank = new HashMap<>();
    private final ArrayList<String> commonWords = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean ready = false;

    void load(Context context, Runnable onReady) {
        Thread worker = new Thread(() -> {
            try {
                loadCommon(context);
                loadWords(context);
            } catch (IOException ignored) {
                // The keyboard remains usable with typed text even if the optional dictionary fails.
            }
            ready = true;
            if (onReady != null) main.post(onReady);
        }, "classic-dictionary-loader");
        worker.setDaemon(true);
        worker.start();
    }

    boolean isReady() { return ready; }

    private void loadCommon(Context context) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("common_words.txt"), StandardCharsets.UTF_8))) {
            String line;
            int rank = 0;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toLowerCase(Locale.US);
                if (!word.isEmpty() && !commonRank.containsKey(word)) {
                    commonRank.put(word, rank++);
                    commonWords.add(word);
                }
            }
        }
    }

    private void loadWords(Context context) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("words.txt"), StandardCharsets.UTF_8), 32 * 1024)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toLowerCase(Locale.US);
                if (word.length() < 2 || word.length() > 20 || !isAsciiWord(word)) continue;
                if (!words.add(word)) continue;
                byFirstLetter.computeIfAbsent(word.charAt(0), ignored -> new ArrayList<>()).add(word);
                if (word.length() >= 2) {
                    String p2 = word.substring(0, 2);
                    byPrefix.computeIfAbsent(p2, ignored -> new ArrayList<>()).add(word);
                }
                if (word.length() >= 3) {
                    String p3 = word.substring(0, 3);
                    byPrefix.computeIfAbsent(p3, ignored -> new ArrayList<>()).add(word);
                }
            }
        }
        Comparator<String> rankSort = Comparator
                .comparingInt((String word) -> commonRank.getOrDefault(word, 100000))
                .thenComparing(String::length)
                .thenComparing(Comparator.naturalOrder());
        for (ArrayList<String> bucket : byFirstLetter.values()) bucket.sort(rankSort);
        for (ArrayList<String> bucket : byPrefix.values()) bucket.sort(rankSort);
    }

    private static boolean isAsciiWord(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    List<String> suggestions(String rawPrefix, int limit) {
        String prefix = normalize(rawPrefix);
        if (prefix.isEmpty() || !ready) return Collections.emptyList();
        ArrayList<String> result = new ArrayList<>(limit);
        result.add(rawPrefix);
        ArrayList<String> source;
        if (prefix.length() >= 3) source = byPrefix.get(prefix.substring(0, 3));
        else if (prefix.length() == 2) source = byPrefix.get(prefix);
        else source = byFirstLetter.get(prefix.charAt(0));
        if (source != null) {
            for (String item : source) {
                if (item.equals(prefix)) continue;
                if (!item.startsWith(prefix)) continue;
                result.add(matchCase(item, rawPrefix));
                if (result.size() >= limit) break;
            }
        }
        return result;
    }

    String correction(String rawWord) {
        String word = normalize(rawWord);
        if (word.length() < 3 || !ready || words.contains(word)) return null;
        ArrayList<String> candidates = byFirstLetter.get(word.charAt(0));
        if (candidates == null) return null;
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        int bestDistance = 2;
        for (String candidate : candidates) {
            if (Math.abs(candidate.length() - word.length()) > 1) continue;
            Integer rank = commonRank.get(candidate);
            if (rank == null) continue; // Avoid changing a typo into an obscure dictionary entry.
            int distance = editDistanceAtMostOne(word, candidate);
            if (distance <= 1 && (distance < bestDistance || (distance == bestDistance && rank < bestRank))) {
                best = candidate;
                bestDistance = distance;
                bestRank = rank;
            }
        }
        return best == null ? null : matchCase(best, rawWord);
    }

    String swipeMatch(String rawPattern) {
        String pattern = normalize(rawPattern);
        if (pattern.length() < 3 || !ready) return null;
        // Glide matching runs in the input method's touch callback. Restrict it to the
        // small frequency-ranked vocabulary rather than scanning the full 76k word list.
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestRank = Integer.MAX_VALUE;
        for (int rank = 0; rank < commonWords.size(); rank++) {
            String candidate = commonWords.get(rank);
            if (candidate.charAt(0) != pattern.charAt(0)
                    || Math.abs(candidate.length() - pattern.length()) > 2) continue;
            int distance = editDistanceAtMostTwo(pattern, candidate);
            if (distance < bestDistance || (distance == bestDistance && rank < bestRank)) {
                best = candidate;
                bestDistance = distance;
                bestRank = rank;
            }
        }
        int allowed = pattern.length() <= 5 ? 1 : 2;
        return best != null && bestDistance <= allowed && words.contains(best)
                ? matchCase(best, rawPattern) : null;
    }

    boolean contains(String rawWord) { return ready && words.contains(normalize(rawWord)); }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US).replaceAll("[^a-z]", "");
    }

    private static String matchCase(String candidate, String original) {
        if (original == null || original.isEmpty()) return candidate;
        if (original.length() > 1 && original.equals(original.toUpperCase(Locale.US))) {
            return candidate.toUpperCase(Locale.US);
        }
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1);
        }
        return candidate;
    }

    private static int editDistanceAtMostOne(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return 2;
        int i = 0, j = 0, edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
            if (i + 1 < a.length() && j + 1 < b.length()
                    && a.charAt(i) == b.charAt(j + 1) && a.charAt(i + 1) == b.charAt(j)) {
                if (++edits > 1) return 2;
                i += 2;
                j += 2;
                continue;
            }
            if (++edits > 1) return 2;
            if (a.length() > b.length()) i++;
            else if (b.length() > a.length()) j++;
            else { i++; j++; }
        }
        if (i < a.length() || j < b.length()) edits++;
        return edits;
    }

    private static int editDistanceAtMostTwo(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 2) return 3;
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > 2) return 3;
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.length()];
    }
}
