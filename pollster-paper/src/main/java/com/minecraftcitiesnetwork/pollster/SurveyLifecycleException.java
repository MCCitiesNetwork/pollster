package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import org.jetbrains.annotations.NotNull;

/**
 * A save, open, close or reopen refused because of the survey's status or content lock. The
 * message is English for the log; dialogs show the {@link Reason#messageKey()} text instead.
 */
public final class SurveyLifecycleException extends IllegalStateException {

    public enum Reason {
        /** The survey is closed, so nothing about it may change. */
        CLOSED(MessageKeys.EDITOR_READ_ONLY),
        /** The survey is open, so its questions and anonymity may not change. */
        CONTENT_LOCKED(MessageKeys.EDITOR_LOCKED),
        /** The survey's status is not the one the action expected, usually because of a stale dialog. */
        STATUS_CHANGED(MessageKeys.EDITOR_STATUS_CHANGED),
        /** The survey file is gone, so a stale dialog must not recreate it. */
        MISSING(MessageKeys.SURVEY_UNAVAILABLE);

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }

        public @NotNull String messageKey() {
            return this.messageKey;
        }
    }

    private final Reason reason;

    public SurveyLifecycleException(@NotNull Reason reason, @NotNull String message) {
        super(message);
        this.reason = reason;
    }

    public @NotNull Reason reason() {
        return this.reason;
    }

    public @NotNull String messageKey() {
        return this.reason.messageKey();
    }
}
