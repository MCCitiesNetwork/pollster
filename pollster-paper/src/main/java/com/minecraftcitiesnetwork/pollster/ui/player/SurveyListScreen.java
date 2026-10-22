package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/** Every survey the player may take, with their state next to each name. */
public final class SurveyListScreen {

    private SurveyListScreen() {
    }

    public static @NotNull Screen create(@NotNull PlayerUi ui, @NotNull Player player,
                                         @NotNull List<SurveyDefinition> visible, @NotNull Set<String> completed,
                                         @NotNull Set<String> inProgress) {
        Screen.Builder builder = Screen.builder(ui.style().title("Surveys"));
        if (visible.isEmpty()) {
            builder.body(ui.messages().messageFor(MessageKeys.SURVEY_LIST_EMPTY));
        }
        for (SurveyDefinition survey : visible) {
            if (completed.contains(survey.id())) {
                // The dialog has no disabled buttons, so a completed survey is gray and does nothing.
                builder.button(Button.of(
                        Component.text(survey.name() + " - Completed", NamedTextColor.GRAY), ctx -> { }));
                continue;
            }
            Component state = inProgress.contains(survey.id())
                    ? Component.text(" - In progress", NamedTextColor.YELLOW)
                    : Component.text(" - New", NamedTextColor.GREEN);
            builder.button(Button.of(Component.text(survey.name(), NamedTextColor.WHITE).append(state), ctx -> {
                if (!PlayerScreens.available(ui, survey.id(), false)) {
                    ui.sounds().error(ctx.player());
                    ctx.open(PlayerScreens.unavailable(ui));
                    return;
                }
                ui.sounds().open(ctx.player());
                // Show the current definition, not the one captured when the list was built.
                ctx.open(IntroScreen.create(ui, ui.service().survey(survey.id()).orElse(survey), false));
            }));
        }
        builder.button(Button.of(ui.style().secondary("Close"), ScreenContext::close));
        return builder.build();
    }
}
