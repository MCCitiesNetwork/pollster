package com.minecraftcitiesnetwork.pollster.ui;

import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Dialog text styles. Each style is a MiniMessage template under {@code ui.style.*} with a
 * {@code <text>} tag, so server owners can recolour dialogs in messages.yml. String text is always
 * inserted unparsed, so player input cannot inject markup; staff text arrives as a component
 * already rendered by {@link StaffText}.
 */
public final class Style {

    public static final String TITLE = "ui.style.title";
    public static final String DANGER = "ui.style.danger";
    public static final String PRIMARY = "ui.style.primary";
    public static final String EDIT = "ui.style.edit";
    public static final String SECONDARY = "ui.style.secondary";
    public static final String LABEL = "ui.style.label";
    public static final String ERROR = "ui.style.error";

    /** Used for any key the configured container lacks, so a trimmed messages.yml still renders. */
    private static final MessageContainer DEFAULTS = new MessageContainer();

    static {
        DEFAULTS.setMessage(TITLE, "<gold><bold><text>");
        DEFAULTS.setMessage(DANGER, "<red><bold><text>");
        DEFAULTS.setMessage(PRIMARY, "<green><bold><text>");
        DEFAULTS.setMessage(EDIT, "<yellow><bold><text>");
        DEFAULTS.setMessage(SECONDARY, "<gray><text>");
        DEFAULTS.setMessage(LABEL, "<aqua><text>: <gray><value>");
        DEFAULTS.setMessage(ERROR, "<red><text>");
        DEFAULTS.setMessage(MessageKeys.UI_BUTTON_CONFIRM, "Confirm");
        DEFAULTS.setMessage(MessageKeys.UI_BUTTON_CANCEL, "Cancel");
        DEFAULTS.setMessage(MessageKeys.UI_BUTTON_OK, "OK");
        DEFAULTS.setMessage(MessageKeys.UI_TITLE_ERROR, "Error");
        DEFAULTS.setMessage(MessageKeys.UI_LOADING, "Loading...");
    }

    // Read through a supplier so /pollster reload recolours dialogs without rebuilding every holder of a Style.
    private final Supplier<? extends MessageContainer> messages;

    public Style(@NotNull MessageContainer messages) {
        this(() -> messages);
    }

    /** A style that always renders with the container the supplier currently returns. */
    public Style(@NotNull Supplier<? extends MessageContainer> messages) {
        this.messages = messages;
    }

    /** A style using only the built-in defaults. */
    public static @NotNull Style defaults() {
        return new Style(new MessageContainer());
    }

    public @NotNull Component title(@NotNull String text) {
        return render(TITLE, text);
    }

    /** A title whose text is already a component (staff-written text rendered by {@link StaffText}). */
    public @NotNull Component title(@NotNull Component text) {
        return renderMarkup(TITLE, text, Component.empty());
    }

    /** Destructive actions and confirmation titles. */
    public @NotNull Component danger(@NotNull String text) {
        return render(DANGER, text);
    }

    public @NotNull Component primary(@NotNull String text) {
        return render(PRIMARY, text);
    }

    /** Edit and navigation into a sub-screen. */
    public @NotNull Component edit(@NotNull String text) {
        return render(EDIT, text);
    }

    /** Back, Close, Cancel, page controls. */
    public @NotNull Component secondary(@NotNull String text) {
        return render(SECONDARY, text);
    }

    public @NotNull Component label(@NotNull String text, @NotNull Component value) {
        return renderWith(LABEL, text, value);
    }

    /** A label whose text is already a component (staff-written text rendered by {@link StaffText}). */
    public @NotNull Component label(@NotNull Component text, @NotNull Component value) {
        return renderMarkup(LABEL, text, value);
    }

    public @NotNull Component error(@NotNull String text) {
        return render(ERROR, text);
    }

    /**
     * A configurable word such as a button label, as plain text. It is fed back through the
     * styles above, which insert it unparsed, so only the style (not the label) carries markup.
     */
    public @NotNull String word(@NotNull String key) {
        MessageContainer current = this.messages.get();
        MessageContainer source = current.miniMessageFormattedFor(key).equals(key) ? DEFAULTS : current;
        return PlainTextComponentSerializer.plainText().serialize(source.messageFor(key));
    }

    private @NotNull Component render(@NotNull String key, @NotNull String text) {
        return renderWith(key, text, Component.empty());
    }

    private @NotNull Component renderWith(@NotNull String key, @NotNull String text, @NotNull Component value) {
        MessageContainer current = this.messages.get();
        MessageContainer source = current.miniMessageFormattedFor(key).equals(key) ? DEFAULTS : current;
        return source.messageFor(key, MessageContainer.value("text", text), MessageContainer.markup("value", value));
    }

    private @NotNull Component renderMarkup(@NotNull String key, @NotNull Component text, @NotNull Component value) {
        MessageContainer current = this.messages.get();
        MessageContainer source = current.miniMessageFormattedFor(key).equals(key) ? DEFAULTS : current;
        return source.messageFor(key, MessageContainer.markup("text", text), MessageContainer.markup("value", value));
    }
}
