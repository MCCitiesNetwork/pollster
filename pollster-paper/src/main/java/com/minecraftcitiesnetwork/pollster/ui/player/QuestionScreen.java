package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.ui.StaffText;
import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import com.minecraftcitiesnetwork.pollster.api.flow.AnswerResult;
import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** One question of a survey. */
public final class QuestionScreen {

    /** Dialog input key for the free-text answer. */
    static final String TEXT_KEY = "text";
    /** Dialog input keys are not free-form, so option checkboxes are numbered instead of using option keys. */
    static final String OPTION_PREFIX = "o";

    private QuestionScreen() {
    }

    /** What the player had typed or ticked when an answer was refused, so it is not lost. */
    public record Draft(@Nullable String text, @NotNull Set<String> selected) {
    }

    public static @NotNull Screen create(@NotNull PlayerUi ui, @NotNull PlayerSession session,
                                         @Nullable Component error) {
        return create(ui, session, error, null);
    }

    public static @NotNull Screen create(@NotNull PlayerUi ui, @NotNull PlayerSession session,
                                         @Nullable Component error, @Nullable Draft draft) {
        SurveyFlow flow = session.flow();
        Optional<Question> current = flow.current();
        if (current.isEmpty()) {
            return ReviewScreen.create(ui, session);
        }
        Question question = current.get();
        // Every button acts only while the flow is still on the step this screen was built for.
        int step = flow.step();
        Screen.Builder builder = Screen.builder(PlayerScreens.title(ui, flow.survey(), session.preview()));
        if (error != null) {
            builder.body(error);
        }
        builder.body(ui.messages().messageFor(MessageKeys.SURVEY_QUESTION_HEADER,
                MessageContainer.value("position", Integer.toString(flow.position())),
                MessageContainer.value("total", Integer.toString(flow.visibleCount()))));
        builder.body(StaffText.render(question.text()).colorIfAbsent(NamedTextColor.WHITE));

        Optional<Answer> existing = flow.answerFor(question.key());
        switch (question.type()) {
            case SINGLE -> {
                builder.columns(2);
                String chosen = existing.filter(Answer.Choice.class::isInstance)
                        .map(a -> ((Answer.Choice) a).optionKey()).orElse(null);
                for (Option option : question.options()) {
                    Component label = option.key().equals(chosen)
                            ? Component.text("✔ ", NamedTextColor.GREEN)
                                    .append(StaffText.render(option.label()).colorIfAbsent(NamedTextColor.GREEN))
                            : StaffText.render(option.label()).colorIfAbsent(NamedTextColor.WHITE);
                    builder.button(Button.of(label, ctx -> submit(ui, session, ctx, step,
                            new Answer.Choice(option.key()), null)));
                }
            }
            case YES_NO -> {
                builder.columns(2);
                builder.button(Button.of(ui.style().primary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_YES)),
                        ctx -> submit(ui, session, ctx, step, new Answer.Choice("yes"), null)));
                builder.button(Button.of(ui.style().danger(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_NO)),
                        ctx -> submit(ui, session, ctx, step, new Answer.Choice("no"), null)));
            }
            case MULTIPLE -> {
                Set<String> selected = draft != null ? draft.selected()
                        : existing.filter(Answer.Choices.class::isInstance)
                        .map(a -> ((Answer.Choices) a).optionKeys()).orElse(Set.of());
                List<Option> options = question.options();
                for (int i = 0; i < options.size(); i++) {
                    Option option = options.get(i);
                    builder.input(new Input.BoolInput(OPTION_PREFIX + i, StaffText.render(option.label()),
                            selected.contains(option.key())));
                }
                builder.button(Button.of(ui.style().primary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_NEXT)), ctx -> {
                    Set<String> ticked = new LinkedHashSet<>();
                    for (int i = 0; i < options.size(); i++) {
                        if (Boolean.TRUE.equals(ctx.bool(OPTION_PREFIX + i))) {
                            ticked.add(options.get(i).key());
                        }
                    }
                    submit(ui, session, ctx, step, new Answer.Choices(ticked), new Draft(null, ticked));
                }));
            }
            case TEXT -> {
                String initial = draft != null && draft.text() != null ? draft.text()
                        : existing.filter(Answer.Text.class::isInstance)
                        .map(a -> ((Answer.Text) a).value()).orElse("");
                builder.input(new Input.TextInput(TEXT_KEY, Component.text(PlayerScreens.word(ui, MessageKeys.SURVEY_TEXT_ANSWER_LABEL)), initial,
                        ui.settings().textMaxLength(), true));
                builder.button(Button.of(ui.style().primary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_NEXT)), ctx -> {
                    String text = ctx.text(TEXT_KEY);
                    String value = text == null ? "" : text;
                    submit(ui, session, ctx, step, new Answer.Text(value), new Draft(value, Set.of()));
                }));
            }
        }

        // Back only makes sense once there is an earlier shown question to return to.
        if (flow.position() > 1) {
            builder.button(Button.of(ui.style().secondary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_BACK)), ctx ->
                    PlayerScreens.guardedStep(ui, session, ctx, step, () -> {
                        flow.back();
                        ctx.open(PlayerScreens.current(ui, session));
                    })));
        }
        if (!question.required()) {
            builder.button(Button.of(ui.style().edit(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_SKIP)), ctx ->
                    PlayerScreens.guardedStep(ui, session, ctx, step, () -> {
                        flow.skip();
                        ui.sounds().next(ctx.player());
                        ctx.open(PlayerScreens.current(ui, session));
                    })));
        }
        return builder.build();
    }

    private static void submit(PlayerUi ui, PlayerSession session, ScreenContext ctx, int step, Answer answer,
                               @Nullable Draft draft) {
        PlayerScreens.guardedStep(ui, session, ctx, step, () -> {
            Question question = session.flow().current().orElse(null);
            AnswerResult result = session.flow().answer(answer);
            if (result instanceof AnswerResult.Invalid invalid && question != null) {
                ui.sounds().error(ctx.player());
                ctx.open(create(ui, session, errorLine(ui, question, invalid.reason()), draft));
                return;
            }
            ui.sounds().next(ctx.player());
            ctx.open(PlayerScreens.current(ui, session));
        });
    }

    private static Component errorLine(PlayerUi ui, Question question, AnswerResult.Reason reason) {
        return switch (reason) {
            case REQUIRED -> ui.messages().messageFor(MessageKeys.SURVEY_INVALID_REQUIRED);
            case TOO_FEW -> ui.messages().messageFor(MessageKeys.SURVEY_INVALID_TOO_FEW,
                    MessageContainer.value("min", Integer.toString(question.min())));
            case TOO_MANY -> ui.messages().messageFor(MessageKeys.SURVEY_INVALID_TOO_MANY,
                    MessageContainer.value("max", Integer.toString(question.effectiveMax())));
            case TOO_LONG -> ui.messages().messageFor(MessageKeys.SURVEY_INVALID_TOO_LONG,
                    MessageContainer.value("max", Integer.toString(ui.settings().textMaxLength())));
            case NOT_AN_OPTION, NOT_SKIPPABLE, WRONG_TYPE ->
                    ui.messages().messageFor(MessageKeys.SURVEY_INVALID_GENERIC);
        };
    }
}
