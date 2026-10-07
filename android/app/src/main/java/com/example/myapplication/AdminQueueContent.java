package com.example.myapplication;

import org.json.JSONObject;

/** Queue copy comes from the reported snapshot, never model metadata or identifiers. */
final class AdminQueueContent {
    private static final java.util.regex.Pattern FORMATTING_TAG = java.util.regex.Pattern.compile(
            "</?(?:i|b|em|strong|u|span|font|p|br|div)\\b[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE);
    private AdminQueueContent() {}

    static String title(JSONObject item) { return bounded(item, "contentTitle", 120); }
    static String preview(JSONObject item) { return bounded(item, "contentPreview", 180); }
    static String translatedTitle(JSONObject item) { return translated(item,"title","sourceTitle",120); }
    static String translatedPreview(JSONObject item) { return translated(item,"body","sourcePreview",180); }
    static boolean matchesTranslation(JSONObject source, JSONObject translated) {
        return source.optString("id").equals(translated.optString("id"))
                && title(source).equals(translated.optString("sourceTitle"))
                && preview(source).equals(translated.optString("sourcePreview"));
    }

    private static String bounded(JSONObject item, String key, int limit) {
        if (item == null || item.isNull(key)) return "";
        return boundedText(item.optString(key, ""), limit);
    }

    private static String translated(JSONObject item, String key, String sourceKey, int limit) {
        if (item == null || item.isNull(key)) return "";
        String value = item.optString(key, "");
        // Some model answers introduce HTML formatting into plain forum text.
        // Preserve literal tags when the reported source itself contains them.
        if (!FORMATTING_TAG.matcher(item.optString(sourceKey, "")).find())
            value = FORMATTING_TAG.matcher(value).replaceAll("");
        return boundedText(value, limit);
    }

    private static String boundedText(String text, int limit) {
        String value = text.replaceAll("\\s+", " ").trim();
        return value.codePointCount(0, value.length()) <= limit ? value
                : value.substring(0, value.offsetByCodePoints(0, limit)) + "…";
    }
}
