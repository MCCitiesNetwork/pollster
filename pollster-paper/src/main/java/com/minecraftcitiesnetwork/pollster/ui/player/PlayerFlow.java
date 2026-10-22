package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Entry points that open player screens from outside a dialog button: the command, the join
 * listener and the admin preview. {@code show} displays a screen to a player (the composition
 * root passes {@code DialogRenderer::show}).
 */
public final class PlayerFlow {

    private final PlayerUi ui;
    private final BiConsumer<Player, Screen> show;

    public PlayerFlow(@NotNull PlayerUi ui, @NotNull BiConsumer<Player, Screen> show) {
        this.ui = ui;
        this.show = show;
    }

    /** {@code /surveys}: the list of surveys this player can see. */
    public void openList(@NotNull Player player) {
        UUID id = player.getUniqueId();
        this.show.accept(player, Screens.loading(this.ui.style().title(PlayerScreens.word(this.ui, MessageKeys.SURVEY_LIST_TITLE))));
        List<SurveyDefinition> visible = this.ui.service().visibleTo(player);
        this.ui.service().completed(id).whenComplete((completed, failure) -> {
            if (failure != null || completed == null) {
                this.show.accept(player, Screens.error(this.ui.style(),
                        this.ui.messages().messageFor(MessageKeys.SURVEY_LIST_LOAD_FAILED), List.of(),
                        ctx -> ctx.close()));
                return;
            }
            Set<String> inProgress = this.ui.sessions().get(id).filter(s -> !s.preview())
                    .map(s -> Set.of(s.flow().survey().id())).orElse(Set.of());
            this.show.accept(player, SurveyListScreen.create(this.ui, player, visible, completed, inProgress));
        });
    }

    /** The intro for a featured survey, with "Not now". */
    public void openFeatured(@NotNull Player player, @NotNull SurveyDefinition survey) {
        this.ui.sounds().open(player);
        this.show.accept(player, IntroScreen.create(this.ui, survey, true));
    }

    /**
     * Admin preview: the full flow with conditions, but nothing is saved, no rewards run and no
     * start event fires. Replaces any unfinished survey the admin had.
     */
    public void startPreview(@NotNull Player player, @NotNull SurveyDefinition survey) {
        this.ui.sounds().open(player);
        this.show.accept(player, IntroScreen.create(this.ui, survey, false, true));
    }

    /** {@code /surveys resume}. Returns false (after telling the player) when nothing is unfinished. */
    public boolean resume(@NotNull Player player) {
        Optional<PlayerSession> session = this.ui.sessions().get(player.getUniqueId());
        if (session.isEmpty()) {
            player.sendMessage(this.ui.messages().messageFor(MessageKeys.SURVEY_NO_UNFINISHED));
            return false;
        }
        PlayerSession current = session.get();
        if (!PlayerScreens.available(this.ui, current.flow().survey().id(), current.preview())) {
            this.ui.sessions().end(player.getUniqueId());
            this.show.accept(player, PlayerScreens.unavailable(this.ui));
            return false;
        }
        this.ui.sessions().touch(player.getUniqueId(), this.ui.sessions().now());
        this.ui.sounds().open(player);
        this.show.accept(player, PlayerScreens.current(this.ui,
                this.ui.sessions().get(player.getUniqueId()).orElse(current)));
        return true;
    }
}
