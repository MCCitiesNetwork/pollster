package com.minecraftcitiesnetwork.pollster.config;

/**
 * Keys into {@code messages.yml}. Each task appends the keys its screens use, together with
 * their default text, so every constant here must have an entry in the bundled file.
 */
public final class MessageKeys {

    public static final String PREFIX = "prefix";

    public static final String UI_STYLE_TITLE = "ui.style.title";
    public static final String UI_STYLE_DANGER = "ui.style.danger";
    public static final String UI_STYLE_PRIMARY = "ui.style.primary";
    public static final String UI_STYLE_EDIT = "ui.style.edit";
    public static final String UI_STYLE_SECONDARY = "ui.style.secondary";
    public static final String UI_STYLE_LABEL = "ui.style.label";
    public static final String UI_STYLE_ERROR = "ui.style.error";

    /** Shown for any stale dialog button: survey closed, reloaded, deleted, or session ended. */
    public static final String SURVEY_UNAVAILABLE = "errors.survey-unavailable";

    // Admin editor (/pollster).
    public static final String EDITOR_OPEN_WARNING = "editor.open-warning";
    public static final String EDITOR_LOCKED = "editor.locked";
    public static final String EDITOR_READ_ONLY = "editor.read-only";
    public static final String EDITOR_ANONYMOUS_LOCKED = "editor.anonymous-locked";
    public static final String EDITOR_MOVE_BREAKS_CONDITION = "editor.move-breaks-condition";
    public static final String EDITOR_KEY_REFERENCED = "editor.key-referenced";
    public static final String EDITOR_REMOVE_REFERENCED = "editor.remove-referenced";
    public static final String EDITOR_DUPLICATE_KEY = "editor.duplicate-key";
    public static final String EDITOR_UNKNOWN_QUESTION = "editor.unknown-question";
    public static final String EDITOR_SAVE_FAILED = "editor.save-failed";
    public static final String EDITOR_RELOAD_OK = "editor.reload-ok";
    public static final String EDITOR_RELOAD_FAILURE = "editor.reload-failure";
    public static final String EDITOR_RELOAD_ERROR = "editor.reload-error";

    private MessageKeys() {
    }
}
