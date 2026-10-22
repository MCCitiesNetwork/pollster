package com.minecraftcitiesnetwork.pollster.command;

import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.ui.player.PlayerFlow;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

/** {@code /surveys} (alias {@code /survey}) and {@code /surveys resume}, for players with pollster.use. */
public record SurveysCommand(@NotNull PlayerFlow flow, @NotNull Supplier<Messages> messages) {

    private static final String PERMISSION = "pollster.use";

    public void register(@NotNull CommandManager<Source> manager) {
        // Any sender type is accepted so the console gets a message from messages.yml, not Cloud's
        // generic "Unknown or incomplete command".
        Command.Builder<Source> root = manager.commandBuilder("surveys", "survey").permission(PERMISSION);
        manager.command(root.handler(ctx -> asPlayer(ctx, PlayerFlow::openList)));
        manager.command(root.literal("resume").handler(ctx -> asPlayer(ctx, PlayerFlow::resume)));
    }

    private void asPlayer(@NotNull CommandContext<Source> ctx, @NotNull BiConsumer<PlayerFlow, Player> action) {
        CommandSender sender = ctx.sender().source();
        if (sender instanceof Player player) {
            action.accept(this.flow, player);
        } else {
            sender.sendMessage(this.messages.get().messageFor(MessageKeys.SURVEY_PLAYER_ONLY));
        }
    }
}
