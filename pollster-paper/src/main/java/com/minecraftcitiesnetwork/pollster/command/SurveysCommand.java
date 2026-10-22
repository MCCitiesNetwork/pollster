package com.minecraftcitiesnetwork.pollster.command;

import com.minecraftcitiesnetwork.pollster.ui.player.PlayerFlow;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

/** {@code /surveys} (alias {@code /survey}) and {@code /surveys resume}, for players with pollster.use. */
public record SurveysCommand(@NotNull PlayerFlow flow) {

    private static final String PERMISSION = "pollster.use";

    public void register(@NotNull CommandManager<Source> manager) {
        Command.Builder<Source> root = manager.commandBuilder("surveys", "survey").permission(PERMISSION);
        manager.command(root.senderType(PlayerSource.class).handler(this::openList));
        manager.command(root.literal("resume").senderType(PlayerSource.class).handler(this::resume));
    }

    private void openList(@NotNull CommandContext<PlayerSource> ctx) {
        this.flow.openList(player(ctx));
    }

    private void resume(@NotNull CommandContext<PlayerSource> ctx) {
        this.flow.resume(player(ctx));
    }

    private static Player player(CommandContext<PlayerSource> ctx) {
        return ctx.sender().source();
    }
}
