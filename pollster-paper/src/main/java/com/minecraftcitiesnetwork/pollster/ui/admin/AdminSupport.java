package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import com.minecraftcitiesnetwork.pollster.api.validation.SurveyValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static com.minecraftcitiesnetwork.pollster.config.MessageKeys.*;

/** Small helpers shared by the admin screens. */
final class AdminSupport {

    /** Wide enough for "id · Name [STATUS] · n responses". */
    static final int WIDE = 300;
    static final int NARROW = 40;

    private AdminSupport() {
    }

    /** A message from messages.yml as plain text; see {@link com.minecraftcitiesnetwork.pollster.ui.Style#text}. */
    static @NotNull String t(@NotNull AdminUi ui, @NotNull String key, @NotNull TagResolver... placeholders) {
        return ui.style().text(key, placeholders);
    }

    /** A placeholder whose value is inserted as literal text, never parsed as markup. */
    static @NotNull TagResolver v(@NotNull String name, @NotNull Object value) {
        return MessageContainer.value(name, String.valueOf(value));
    }

    static @NotNull Throwable unwrap(@NotNull Throwable t) {
        return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
    }

    static @NotNull String truncate(@NotNull String s, int max) {
        String oneLine = s.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 1) + "…";
    }

    static @NotNull Button wide(@NotNull Component label, @NotNull Consumer<ScreenContext> onClick) {
        return new Button(label, null, WIDE, onClick);
    }

    static @NotNull Button narrow(@NotNull Component label, @NotNull Consumer<ScreenContext> onClick) {
        return new Button(label, null, NARROW, onClick);
    }

    /** A yellow button label with its current value after it in gray, as the manager hubs do. */
    static @NotNull Component valued(@NotNull AdminUi ui, @NotNull String name, @NotNull String value) {
        return ui.style().edit(name).append(Component.text(" " + truncate(value, 32), NamedTextColor.GRAY));
    }

    static @NotNull Screen loading(@NotNull AdminUi ui) {
        return Screens.loading(ui.style().title(t(ui, EDITOR_COMMON_WORKING)));
    }

    /** A message from messages.yml, shown with an OK button. */
    static @NotNull Screen notice(@NotNull AdminUi ui, @NotNull String title, @NotNull Component message,
                                  @NotNull Consumer<ScreenContext> onOk) {
        return Screen.builder(ui.style().title(title)).body(message)
                .button(Button.of(ui.style().secondary(ui.style().word(UI_BUTTON_OK)), onOk)).build();
    }

    static @NotNull Screen unavailable(@NotNull AdminUi ui, @NotNull Consumer<ScreenContext> onOk) {
        return notice(ui, t(ui, EDITOR_COMMON_SURVEY), ui.messages().messageFor(MessageKeys.SURVEY_UNAVAILABLE), onOk);
    }

    /** An error screen whose details are the validator problems joined by "; " in an exception message. */
    static @NotNull Screen failure(@NotNull AdminUi ui, @NotNull Throwable cause, @NotNull Consumer<ScreenContext> onOk) {
        Throwable t = unwrap(cause);
        String reason = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        List<Component> details = new ArrayList<>();
        for (String part : reason.split("; ")) {
            details.add(Component.text(part));
        }
        return Screens.error(ui.style(), ui.style().error(t(ui, EDITOR_COMMON_COULD_NOT_SAVE)), details, onOk);
    }

    /** The problems that stop a survey from being opened or previewed, as readable lines. */
    static @NotNull List<String> problems(@NotNull AdminUi ui, @NotNull SurveyDefinition s) {
        List<String> lines = new ArrayList<>();
        if (s.questions().isEmpty()) {
            lines.add(t(ui, EDITOR_COMMON_NEEDS_QUESTION));
        }
        SurveyValidator.validate(s).forEach(p -> lines.add(p.questionKey() == null ? p.message()
                : t(ui, EDITOR_COMMON_QUESTION_PROBLEM, v("key", p.questionKey()), v("message", p.message()))));
        return lines;
    }

    static @NotNull Screen problemsScreen(@NotNull AdminUi ui, @NotNull List<String> problems,
                                          @NotNull Consumer<ScreenContext> onOk) {
        List<Component> details = problems.stream().map(Component::text).map(c -> (Component) c).toList();
        return Screens.error(ui.style(), ui.style().error(t(ui, EDITOR_COMMON_HAS_PROBLEMS)), details, onOk);
    }

    static @NotNull Component rejection(@NotNull AdminUi ui, @NotNull QuestionEdits.Rejected rejected) {
        return ui.messages().messageFor(rejected.messageKey(),
                MessageContainer.value("args", String.join(", ", rejected.args())));
    }
}
