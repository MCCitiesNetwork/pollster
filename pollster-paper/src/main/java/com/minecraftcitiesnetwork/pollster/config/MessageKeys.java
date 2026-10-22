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
    /** A dialog button the player lacks the permission for. */
    public static final String ERRORS_NO_PERMISSION = "errors.no-permission";

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

    // Words on the player survey screens.
    public static final String SURVEY_BUTTON_START = "survey.button.start";
    public static final String SURVEY_BUTTON_NOT_NOW = "survey.button.not-now";
    public static final String SURVEY_BUTTON_CLOSE = "survey.button.close";
    public static final String SURVEY_BUTTON_YES = "survey.button.answer-yes";
    public static final String SURVEY_BUTTON_NO = "survey.button.answer-no";
    public static final String SURVEY_BUTTON_NEXT = "survey.button.next";
    public static final String SURVEY_BUTTON_BACK = "survey.button.back";
    public static final String SURVEY_BUTTON_SKIP = "survey.button.skip";
    public static final String SURVEY_BUTTON_SUBMIT = "survey.button.submit";
    public static final String SURVEY_BUTTON_CANCEL = "survey.button.cancel";
    public static final String SURVEY_LIST_TITLE = "survey.list-title";
    public static final String SURVEY_LIST_COMPLETED = "survey.list-state.completed";
    public static final String SURVEY_LIST_IN_PROGRESS = "survey.list-state.in-progress";
    public static final String SURVEY_LIST_NEW = "survey.list-state.new";
    public static final String SURVEY_TEXT_ANSWER_LABEL = "survey.text-answer-label";
    public static final String SURVEY_PLAYER_ONLY = "survey.player-only";

    // Admin editor (/pollster).
    public static final String EDITOR_OPEN_WARNING = "editor.open-warning";
    public static final String EDITOR_LOCKED = "editor.locked";
    public static final String EDITOR_READ_ONLY = "editor.read-only";
    public static final String EDITOR_STATUS_CHANGED = "editor.status-changed";
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

    // Admin editor screens.
    public static final String EDITOR_COMMON_WORKING = "editor.common.working";
    public static final String EDITOR_COMMON_SURVEY = "editor.common.survey";
    public static final String EDITOR_COMMON_BACK = "editor.common.back";
    public static final String EDITOR_COMMON_CLOSE = "editor.common.close";
    public static final String EDITOR_COMMON_NEXT = "editor.common.next";
    public static final String EDITOR_COMMON_SAVE = "editor.common.save";
    public static final String EDITOR_COMMON_CREATE = "editor.common.create";
    public static final String EDITOR_COMMON_NONE = "editor.common.none";
    public static final String EDITOR_COMMON_ANSWER_YES = "editor.common.answer-yes";
    public static final String EDITOR_COMMON_ANSWER_NO = "editor.common.answer-no";
    public static final String EDITOR_COMMON_BULLET = "editor.common.bullet";
    public static final String EDITOR_COMMON_COULD_NOT_SAVE = "editor.common.could-not-save";
    public static final String EDITOR_COMMON_HAS_PROBLEMS = "editor.common.has-problems";
    public static final String EDITOR_COMMON_NEEDS_QUESTION = "editor.common.needs-question";
    public static final String EDITOR_COMMON_QUESTION_PROBLEM = "editor.common.question-problem";
    public static final String EDITOR_COMMON_ID_INVALID = "editor.common.id-invalid";
    public static final String EDITOR_COMMON_ID_EXISTS = "editor.common.id-exists";
    public static final String EDITOR_COMMON_NEW_ID = "editor.common.new-id";
    public static final String EDITOR_LABEL_ID = "editor.label.id";
    public static final String EDITOR_LABEL_NAME = "editor.label.name";
    public static final String EDITOR_LABEL_DESCRIPTION = "editor.label.description";
    public static final String EDITOR_LABEL_STATUS = "editor.label.status";
    public static final String EDITOR_LABEL_RESPONSES = "editor.label.responses";
    public static final String EDITOR_LABEL_QUESTIONS = "editor.label.questions";
    public static final String EDITOR_LABEL_REWARDS = "editor.label.rewards";
    public static final String EDITOR_LABEL_ANONYMOUS = "editor.label.anonymous";
    public static final String EDITOR_LABEL_FEATURED = "editor.label.featured";
    public static final String EDITOR_LABEL_REQUIRES_PERMISSION = "editor.label.requires-permission";
    public static final String EDITOR_LABEL_KEY = "editor.label.key";
    public static final String EDITOR_LABEL_TEXT = "editor.label.text";
    public static final String EDITOR_LABEL_TYPE = "editor.label.type";
    public static final String EDITOR_LABEL_REQUIRED = "editor.label.required";
    public static final String EDITOR_LABEL_SELECTIONS = "editor.label.selections";
    public static final String EDITOR_LABEL_CONDITION = "editor.label.condition";
    public static final String EDITOR_LABEL_OPTIONS = "editor.label.options";
    public static final String EDITOR_LIST_TITLE = "editor.list.title";
    public static final String EDITOR_LIST_SUMMARY = "editor.list.summary";
    public static final String EDITOR_LIST_CREATE = "editor.list.create";
    public static final String EDITOR_LIST_PREVIOUS = "editor.list.previous";
    public static final String EDITOR_LIST_NEXT = "editor.list.next";
    public static final String EDITOR_LIST_ENTRY = "editor.list.entry";
    public static final String EDITOR_LIST_ENTRY_LOCKED = "editor.list.entry-locked";
    public static final String EDITOR_WIZARD_BASICS_TITLE = "editor.wizard.basics-title";
    public static final String EDITOR_WIZARD_SETTINGS_TITLE = "editor.wizard.settings-title";
    public static final String EDITOR_WIZARD_CONFIRM_TITLE = "editor.wizard.confirm-title";
    public static final String EDITOR_WIZARD_ID_INPUT = "editor.wizard.id-input";
    public static final String EDITOR_WIZARD_ANONYMOUS_INPUT = "editor.wizard.anonymous-input";
    public static final String EDITOR_WIZARD_FEATURED_INPUT = "editor.wizard.featured-input";
    public static final String EDITOR_WIZARD_NAME_REQUIRED = "editor.wizard.name-required";
    public static final String EDITOR_OPTIONS_TITLE = "editor.options.title";
    public static final String EDITOR_OPTIONS_HINT = "editor.options.hint";
    public static final String EDITOR_OPTIONS_ADD = "editor.options.add";
    public static final String EDITOR_OPTIONS_EDIT_TITLE = "editor.options.edit-title";
    public static final String EDITOR_OPTIONS_BULK = "editor.options.bulk";
    public static final String EDITOR_OPTIONS_SAVE_QUESTION = "editor.options.save-question";
    public static final String EDITOR_OPTIONS_KEY_INPUT = "editor.options.key-input";
    public static final String EDITOR_OPTIONS_LABEL_INPUT = "editor.options.label-input";
    public static final String EDITOR_OPTIONS_LABEL_REQUIRED = "editor.options.label-required";
    public static final String EDITOR_OPTIONS_KEY_INVALID = "editor.options.key-invalid";
    public static final String EDITOR_OPTIONS_KEY_TAKEN = "editor.options.key-taken";
    public static final String EDITOR_OPTIONS_BULK_TITLE = "editor.options.bulk-title";
    public static final String EDITOR_OPTIONS_BULK_HINT = "editor.options.bulk-hint";
    public static final String EDITOR_OPTIONS_BULK_SUBMIT = "editor.options.bulk-submit";
    public static final String EDITOR_OPTIONS_BULK_EMPTY = "editor.options.bulk-empty";
    public static final String EDITOR_QUESTIONS_TITLE = "editor.questions.title";
    public static final String EDITOR_QUESTIONS_LOCKED_NOTE = "editor.questions.locked-note";
    public static final String EDITOR_QUESTIONS_ADD = "editor.questions.add";
    public static final String EDITOR_QUESTIONS_NEW_TEXT = "editor.questions.new-text";
    public static final String EDITOR_QUESTION_TITLE = "editor.question.title";
    public static final String EDITOR_QUESTION_KEY_INPUT = "editor.question.key-input";
    public static final String EDITOR_QUESTION_TEXT_INPUT = "editor.question.text-input";
    public static final String EDITOR_QUESTION_MIN_INPUT = "editor.question.min-input";
    public static final String EDITOR_QUESTION_MAX_INPUT = "editor.question.max-input";
    public static final String EDITOR_QUESTION_CONDITION_INPUT = "editor.question.condition-input";
    public static final String EDITOR_QUESTION_MOVE_UP = "editor.question.move-up";
    public static final String EDITOR_QUESTION_MOVE_DOWN = "editor.question.move-down";
    public static final String EDITOR_QUESTION_DELETE = "editor.question.delete";
    public static final String EDITOR_QUESTION_DELETE_TITLE = "editor.question.delete-title";
    public static final String EDITOR_QUESTION_DELETE_BODY = "editor.question.delete-body";
    public static final String EDITOR_QUESTION_SELECTION_RANGE = "editor.question.selection-range";
    public static final String EDITOR_QUESTION_OPTIONS_NOT_APPLICABLE = "editor.question.options-not-applicable";
    public static final String EDITOR_QUESTION_CONDITION_ERROR = "editor.question.condition-error";
    public static final String EDITOR_HUB_TITLE = "editor.hub.title";
    public static final String EDITOR_HUB_LOCKED_WARNING = "editor.hub.locked-warning";
    public static final String EDITOR_HUB_PREVIEW = "editor.hub.preview";
    public static final String EDITOR_HUB_REPORT = "editor.hub.report";
    public static final String EDITOR_HUB_OPEN = "editor.hub.open";
    public static final String EDITOR_HUB_CLOSE = "editor.hub.close";
    public static final String EDITOR_HUB_REOPEN = "editor.hub.reopen";
    public static final String EDITOR_HUB_DUPLICATE = "editor.hub.duplicate";
    public static final String EDITOR_HUB_DELETE = "editor.hub.delete";
    public static final String EDITOR_HUB_EDIT_TITLE = "editor.hub.edit-title";
    public static final String EDITOR_HUB_FIELD_EMPTY = "editor.hub.field-empty";
    public static final String EDITOR_HUB_OPEN_TITLE = "editor.hub.open-title";
    public static final String EDITOR_HUB_DUPLICATE_TITLE = "editor.hub.duplicate-title";
    public static final String EDITOR_HUB_DUPLICATE_HINT = "editor.hub.duplicate-hint";
    public static final String EDITOR_HUB_DELETE_TITLE = "editor.hub.delete-title";
    public static final String EDITOR_HUB_DELETE_BODY = "editor.hub.delete-body";
    public static final String EDITOR_HUB_DELETE_BODY_RESPONSES = "editor.hub.delete-body-responses";
    public static final String EDITOR_HUB_REALLY_TITLE = "editor.hub.really-title";
    public static final String EDITOR_HUB_REALLY_BODY = "editor.hub.really-body";
    public static final String EDITOR_REWARDS_TITLE = "editor.rewards.title";
    public static final String EDITOR_REWARDS_NONE = "editor.rewards.none";
    public static final String EDITOR_REWARDS_REMOVE = "editor.rewards.remove";
    public static final String EDITOR_REWARDS_ADD = "editor.rewards.add";
    public static final String EDITOR_REWARDS_ADD_SUBMIT = "editor.rewards.add-submit";
    public static final String EDITOR_REWARDS_CLOSED_NOTE = "editor.rewards.closed-note";
    public static final String EDITOR_REWARDS_HINT = "editor.rewards.hint";
    public static final String EDITOR_REWARDS_TYPE_COMMAND = "editor.rewards.type-command";
    public static final String EDITOR_REWARDS_TYPE_PLAYER_COMMAND = "editor.rewards.type-player-command";
    public static final String EDITOR_REWARDS_TYPE_MESSAGE = "editor.rewards.type-message";
    public static final String EDITOR_REWARDS_TYPE_TITLE = "editor.rewards.type-title";
    public static final String EDITOR_REWARDS_TYPE_BROADCAST = "editor.rewards.type-broadcast";
    public static final String EDITOR_REWARDS_VALUE_INPUT = "editor.rewards.value-input";
    public static final String EDITOR_REWARDS_VALUE_REQUIRED = "editor.rewards.value-required";

    // In-game report screens.
    public static final String REPORT_SCREEN_LOADING_TITLE = "report.screen.loading-title";
    public static final String REPORT_SCREEN_LOAD_FAILED = "report.screen.load-failed";
    public static final String REPORT_SCREEN_TITLE = "report.screen.title";
    public static final String REPORT_SCREEN_PARTICIPANTS = "report.screen.participants";
    public static final String REPORT_SCREEN_AVERAGE_TIME = "report.screen.average-time";
    public static final String REPORT_SCREEN_NOT_AVAILABLE = "report.screen.not-available";
    public static final String REPORT_SCREEN_SHOWN_TO = "report.screen.shown-to";
    public static final String REPORT_SCREEN_ANSWERS_COUNT = "report.screen.answers-count";
    public static final String REPORT_SCREEN_VIEW_ANSWERS = "report.screen.view-answers";
    public static final String REPORT_SCREEN_PUBLISH = "report.screen.publish";
    public static final String REPORT_SCREEN_EXPORT = "report.screen.export";
    public static final String REPORT_SCREEN_DURATION_MINUTES = "report.screen.duration-minutes";
    public static final String REPORT_SCREEN_DURATION_SECONDS = "report.screen.duration-seconds";
    public static final String REPORT_ANSWERS_TITLE = "report.answers.title";
    public static final String REPORT_ANSWERS_TITLE_PAGED = "report.answers.title-paged";
    public static final String REPORT_ANSWERS_MISSING_QUESTION = "report.answers.missing-question";
    public static final String REPORT_ANSWERS_NONE = "report.answers.none";
    public static final String REPORT_ANSWERS_PREVIOUS = "report.answers.previous";
    public static final String REPORT_ANSWERS_BY = "report.answers.by";

    private MessageKeys() {
    }
}
