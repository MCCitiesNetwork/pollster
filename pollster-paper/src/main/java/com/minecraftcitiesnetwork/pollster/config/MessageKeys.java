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

    public static final String SURVEY_LIST_EMPTY = "survey.list.empty";
    public static final String SURVEY_LIST_LOAD_FAILED = "survey.list.load-failed";
    public static final String SURVEY_INTRO_QUESTIONS = "survey.intro.questions";
    public static final String SURVEY_INTRO_ANONYMOUS = "survey.intro.anonymous";
    public static final String SURVEY_INTRO_NAMED = "survey.intro.named";
    public static final String SURVEY_INTRO_REWARDS = "survey.intro.rewards";
    public static final String SURVEY_QUESTION_HEADER = "survey.question.header";
    public static final String SURVEY_INVALID_REQUIRED = "survey.invalid.required";
    public static final String SURVEY_INVALID_TOO_FEW = "survey.invalid.too-few";
    public static final String SURVEY_INVALID_TOO_MANY = "survey.invalid.too-many";
    public static final String SURVEY_INVALID_TOO_LONG = "survey.invalid.too-long";
    public static final String SURVEY_INVALID_GENERIC = "survey.invalid.generic";
    public static final String SURVEY_REVIEW_INTRO = "survey.review.intro";
    public static final String SURVEY_REVIEW_SKIPPED = "survey.review.skipped";
    public static final String SURVEY_THANKS_BODY = "survey.thanks.body";
    public static final String SURVEY_THANKS_PREVIEW = "survey.thanks.preview";
    public static final String SURVEY_PREVIEW_MARKER = "survey.preview-marker";
    public static final String SURVEY_REPLACE_TITLE = "survey.replace.title";
    public static final String SURVEY_REPLACE_BODY = "survey.replace.body";
    public static final String SURVEY_ALREADY_RESPONDED = "survey.already-responded";
    public static final String SURVEY_SUBMIT_FAILED = "survey.submit-failed";
    public static final String SURVEY_SUBMIT_CANCELLED = "survey.submit-cancelled";
    public static final String SURVEY_CANCELLED = "survey.cancelled";
    public static final String SURVEY_NO_UNFINISHED = "survey.no-unfinished";
    public static final String SURVEY_SAVING = "survey.saving";
    public static final String REMINDER_MESSAGE = "reminder.message";

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
