package com.minecraftcitiesnetwork.pollster.api.model;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SurveyIds {

    public static final int MAX_LENGTH = 48;

    private static final Pattern SURVEY_ID = Pattern.compile("^[a-z0-9-]{3,48}$");
    private static final Pattern KEY = Pattern.compile("^[a-z0-9-]{1,48}$");
    private static final Pattern NON_SLUG_RUN = Pattern.compile("[^a-z0-9]+");

    private SurveyIds() {
    }

    /** Whether {@code s} is usable as a survey id (3 to 48 characters). */
    public static boolean isValidSlug(String s) {
        return SURVEY_ID.matcher(s).matches();
    }

    /** Whether {@code s} is usable as a question or option key (1 to 48 characters). */
    public static boolean isValidKey(String s) {
        return KEY.matcher(s).matches();
    }

    /** Turns a label into a key; a label with nothing usable becomes "option". */
    public static String slugify(String label) {
        String slug = NON_SLUG_RUN.matcher(label.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "option" : slug;
    }
}
