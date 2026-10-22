package com.minecraftcitiesnetwork.pollster.ui.player;

import net.kyori.adventure.text.JoinConfiguration;
import com.minecraftcitiesnetwork.pollster.ui.StaffText;
import com.minecraftcitiesnetwork.pollster.api.event.SurveyCompletedEvent;
import com.minecraftcitiesnetwork.pollster.api.event.SurveySubmitEvent;
import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.result.SubmitResult;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lists every shown question with the answer given, then Submit, Back or Cancel. */
public final class ReviewScreen {

    private static final int BUTTON_WIDTH = 100;

    private ReviewScreen() {
    }

    public static @NotNull Screen create(@NotNull PlayerUi ui, @NotNull PlayerSession session) {
        SurveyDefinition survey = session.flow().survey();
        int step = session.flow().step();
        Screen.Builder builder = Screen.builder(PlayerScreens.title(ui, survey, session.preview()));
        builder.body(ui.messages().messageFor(MessageKeys.SURVEY_REVIEW_INTRO));
        for (Map.Entry<String, Answer> entry : session.flow().answers().entrySet()) {
            Optional<Question> question = survey.question(entry.getKey());
            if (question.isEmpty()) {
                continue;
            }
            // Question text and option labels are staff text (restricted markup); a player's typed
            // answer is never parsed, so markup in it shows up literally.
            builder.body(ui.style().label(StaffText.render(question.get().text()),
                    describe(ui, question.get(), entry.getValue())));
        }
        builder.button(new Button(ui.style().primary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_SUBMIT)), null, BUTTON_WIDTH, ctx -> submit(ui, session, ctx)));
        builder.button(new Button(ui.style().secondary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_BACK)), null, BUTTON_WIDTH, ctx ->
                PlayerScreens.guardedStep(ui, session, ctx, step, () -> {
                    session.flow().back();
                    ctx.open(PlayerScreens.current(ui, session));
                })));
        builder.button(new Button(ui.style().secondary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_CANCEL)), null, BUTTON_WIDTH, ctx ->
                PlayerScreens.guarded(ui, session, ctx, () -> {
                    ui.sessions().end(ctx.player().getUniqueId());
                    ctx.player().sendMessage(ui.messages().messageFor(MessageKeys.SURVEY_CANCELLED));
                    ctx.close();
                })));
        builder.columns(3);
        return builder.build();
    }

    private static Component describe(PlayerUi ui, Question question, Answer answer) {
        return switch (answer) {
            case Answer.Choice choice -> labelOf(question, choice.optionKey());
            case Answer.Choices choices -> Component.join(JoinConfiguration.commas(true),
                    choices.optionKeys().stream().map(key -> labelOf(question, key)).toList());
            case Answer.Text text -> Component.text(text.value());
            case Answer.Skipped skipped -> ui.messages().messageFor(MessageKeys.SURVEY_REVIEW_SKIPPED);
        };
    }

    private static Component labelOf(Question question, String key) {
        for (Option option : question.options()) {
            if (option.key().equals(key)) {
                return StaffText.render(option.label());
            }
        }
        return Component.text(key);
    }

    private static void submit(PlayerUi ui, PlayerSession session, ScreenContext ctx) {
        PlayerScreens.guarded(ui, session, ctx, () -> {
            Player player = ctx.player();
            SurveyDefinition survey = session.flow().survey();
            if (session.preview()) {
                ui.sessions().end(player.getUniqueId());
                ui.sounds().finish(player);
                ctx.open(ThanksScreen.create(ui, survey, true));
                return;
            }
            // Two quick clicks both reach here before the first save returns; only one may proceed.
            if (!session.submitting().compareAndSet(false, true)) {
                return;
            }
            SurveySubmitEvent event = new SurveySubmitEvent(player, survey, session.flow().answers());
            ui.events().accept(event);
            if (event.isCancelled()) {
                session.submitting().set(false);
                ui.sounds().error(player);
                ctx.open(Screens.error(ui.style(), ui.messages().messageFor(MessageKeys.SURVEY_SUBMIT_CANCELLED),
                        List.of(), back -> back.open(create(ui, session))));
                return;
            }
            ctx.open(Screens.loading(PlayerScreens.title(ui, survey, false)));
            ui.service().submit(session.flow(), player.getUniqueId(), player.getName())
                    .whenComplete((result, failure) -> finish(ui, session, ctx, result, failure));
        });
    }

    /** Runs on the main thread: the service completes its futures there. */
    private static void finish(PlayerUi ui, PlayerSession session, ScreenContext ctx, SubmitResult result,
                               Throwable failure) {
        Player player = ctx.player();
        SurveyDefinition survey = session.flow().survey();
        if (failure != null || result == null) {
            session.submitting().set(false);
            ui.sounds().error(player);
            ctx.open(Screens.error(ui.style(), ui.messages().messageFor(MessageKeys.SURVEY_SUBMIT_FAILED),
                    new ArrayList<>(), back -> back.open(create(ui, session))));
            return;
        }
        switch (result) {
            case SubmitResult.Saved saved -> {
                ui.sessions().end(player.getUniqueId());
                ui.rewards().run(player, survey);
                ui.events().accept(new SurveyCompletedEvent(player, survey, saved.responseId()));
                ui.sounds().finish(player);
                ctx.open(ThanksScreen.create(ui, survey, false));
            }
            case SubmitResult.AlreadyResponded ignored -> {
                ui.sessions().end(player.getUniqueId());
                ui.sounds().error(player);
                ctx.open(Screens.error(ui.style(), ui.messages().messageFor(MessageKeys.SURVEY_ALREADY_RESPONDED),
                        List.of(), ScreenContext::close));
            }
            case SubmitResult.SurveyNotOpen ignored -> unavailable(ui, player, ctx);
            case SubmitResult.ContentChanged ignored -> unavailable(ui, player, ctx);
        }
    }

    private static void unavailable(PlayerUi ui, Player player, ScreenContext ctx) {
        ui.sessions().end(player.getUniqueId());
        ui.sounds().error(player);
        ctx.open(PlayerScreens.unavailable(ui));
    }
}
