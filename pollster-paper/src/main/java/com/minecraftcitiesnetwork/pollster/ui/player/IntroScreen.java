package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.ui.StaffText;
import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.api.event.SurveyStartEvent;
import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/** Name, description and what to expect, with Start. */
public final class IntroScreen {

    private IntroScreen() {
    }

    public static @NotNull Screen create(@NotNull PlayerUi ui, @NotNull SurveyDefinition survey, boolean featured) {
        return create(ui, survey, featured, false);
    }

    /** {@code preview} runs the flow without saving, rewards or a start event. */
    public static @NotNull Screen create(@NotNull PlayerUi ui, @NotNull SurveyDefinition survey, boolean featured,
                                         boolean preview) {
        Screen.Builder builder = Screen.builder(PlayerScreens.title(ui, survey, preview));
        if (!survey.description().isBlank()) {
            builder.body(StaffText.render(survey.description()).colorIfAbsent(NamedTextColor.WHITE));
        }
        builder.body(ui.messages().messageFor(MessageKeys.SURVEY_INTRO_QUESTIONS,
                MessageContainer.value("count", Integer.toString(survey.questions().size()))));
        builder.body(ui.messages().messageFor(
                survey.anonymous() ? MessageKeys.SURVEY_INTRO_ANONYMOUS : MessageKeys.SURVEY_INTRO_NAMED));
        if (!survey.rewards().isEmpty()) {
            builder.body(ui.messages().messageFor(MessageKeys.SURVEY_INTRO_REWARDS));
        }
        builder.button(Button.of(ui.style().primary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_START)), ctx -> start(ui, survey.id(), preview, ctx)));
        if (featured) {
            builder.button(Button.of(ui.style().secondary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_NOT_NOW)), ScreenContext::close));
        }
        builder.button(Button.of(ui.style().secondary(PlayerScreens.word(ui, MessageKeys.SURVEY_BUTTON_CLOSE)), ScreenContext::close));
        builder.columns(featured ? 3 : 2);
        return builder.build();
    }

    private static void start(PlayerUi ui, String surveyId, boolean preview, ScreenContext ctx) {
        Player player = ctx.player();
        if (!PlayerScreens.available(ui, surveyId, preview)) {
            ui.sounds().error(player);
            ctx.open(PlayerScreens.unavailable(ui));
            return;
        }
        Optional<PlayerSession> existing = ui.sessions().get(player.getUniqueId()).filter(s -> !s.preview());
        if (!preview && existing.isPresent()) {
            PlayerSession paused = existing.get();
            if (paused.flow().survey().id().equals(surveyId)) {
                // Same survey: carry on where the player stopped rather than starting over.
                ui.sessions().touch(player.getUniqueId(), ui.sessions().now());
                ui.sounds().open(player);
                ctx.open(PlayerScreens.current(ui, ui.sessions().get(player.getUniqueId()).orElse(paused)));
                return;
            }
            ctx.open(Screens.confirm(ui.style(),
                    ui.style().danger(ui.messages().plaintextMessageFor(MessageKeys.SURVEY_REPLACE_TITLE)),
                    ui.messages().messageFor(MessageKeys.SURVEY_REPLACE_BODY),
                    confirmed -> begin(ui, surveyId, false, confirmed), ScreenContext::close));
            return;
        }
        begin(ui, surveyId, preview, ctx);
    }

    private static void begin(PlayerUi ui, String surveyId, boolean preview, ScreenContext ctx) {
        Player player = ctx.player();
        Optional<SurveyDefinition> fresh = ui.service().survey(surveyId);
        if (fresh.isEmpty() || !PlayerScreens.available(ui, surveyId, preview)) {
            ui.sounds().error(player);
            ctx.open(PlayerScreens.unavailable(ui));
            return;
        }
        SurveyDefinition survey = fresh.get();
        if (!preview) {
            SurveyStartEvent event = new SurveyStartEvent(player, survey);
            ui.events().accept(event);
            if (event.isCancelled()) {
                ctx.close();
                return;
            }
        }
        UUID playerId = player.getUniqueId();
        SurveyFlow flow = new SurveyFlow(survey, node -> ui.hasPermission(playerId, node), ui.sessions().now(),
                ui.settings().textMaxLength());
        PlayerSession session = ui.sessions().start(player.getUniqueId(), flow, preview);
        ui.sounds().open(player);
        ctx.open(PlayerScreens.current(ui, session));
    }
}
