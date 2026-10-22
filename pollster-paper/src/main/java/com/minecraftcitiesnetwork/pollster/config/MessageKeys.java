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

    public static final String UI_BUTTON_CONFIRM = "ui.button.confirm";
    public static final String UI_BUTTON_CANCEL = "ui.button.cancel";
    public static final String UI_BUTTON_OK = "ui.button.ok";
    public static final String UI_TITLE_ERROR = "ui.title.error";
    public static final String UI_LOADING = "ui.loading";

    /** Shown for any stale dialog button: survey closed, reloaded, deleted, or session ended. */
    public static final String SURVEY_UNAVAILABLE = "errors.survey-unavailable";

    public static final String PUBLISH_SUCCESS = "publish.success";
    public static final String PUBLISH_FAILED = "publish.failed";
    public static final String EXPORT_SUCCESS = "export.success";
    public static final String EXPORT_FAILED = "export.failed";
    public static final String REPORT_PLAYER_ONLY = "report.player-only";
    public static final String REPORT_UNKNOWN_SURVEY = "report.unknown-survey";

    private MessageKeys() {
    }
}
