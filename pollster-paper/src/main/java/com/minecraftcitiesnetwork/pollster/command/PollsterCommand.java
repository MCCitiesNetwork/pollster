package com.minecraftcitiesnetwork.pollster.command;

import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.store.LoadReport;
import com.minecraftcitiesnetwork.pollster.ui.DialogRenderer;
import com.minecraftcitiesnetwork.pollster.ui.admin.AdminListScreen;
import com.minecraftcitiesnetwork.pollster.ui.admin.AdminUi;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * {@code /pollster} opens the survey editor ({@code pollster.admin}); {@code /pollster reload} re-reads
 * the config and survey files ({@code pollster.admin.reload}).
 *
 * <p>Register with {@link #register(CommandManager)}, or build the commands from your own root
 * builder with {@link #commands(Command.Builder)} so other beans can share the {@code pollster} literal.
 */
public final class PollsterCommand {

    private static final String ROOT = "pollster";

    private final AdminUi ui;
    private final DialogRenderer renderer;
    private final Supplier<CompletableFuture<LoadReport>> reloader;
    private final Messages messages;

    public PollsterCommand(@NotNull AdminUi ui, @NotNull DialogRenderer renderer,
                           @NotNull Supplier<CompletableFuture<LoadReport>> reloader, @NotNull Messages messages) {
        this.ui = ui;
        this.renderer = renderer;
        this.reloader = reloader;
        this.messages = messages;
    }

    public void register(@NotNull CommandManager<Source> manager) {
        this.commands(manager.commandBuilder(ROOT)).forEach(manager::command);
    }

    /** @param root a builder already at the {@code pollster} literal */
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> root) {
        return List.of(
                root.senderType(PlayerSource.class)
                        .permission("pollster.admin")
                        .handler(this::openEditor)
                        .build(),
                root.literal("reload")
                        .permission("pollster.admin.reload")
                        .handler(this::reload)
                        .build());
    }

    private void openEditor(CommandContext<PlayerSource> ctx) {
        var player = ctx.sender().source();
        this.ui.service().responseCounts().whenComplete((counts, error) -> {
            if (error != null) {
                player.sendMessage(this.messages.messageFor(MessageKeys.EDITOR_SAVE_FAILED,
                        MessageContainer.value("reason", String.valueOf(error.getMessage()))));
                return;
            }
            this.renderer.show(player, AdminListScreen.create(this.ui, 0, counts));
        });
    }

    private void reload(CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        this.reloader.get().whenComplete((report, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                sender.sendMessage(this.messages.messageFor(MessageKeys.EDITOR_RELOAD_ERROR,
                        MessageContainer.value("reason", String.valueOf(cause.getMessage()))));
                return;
            }
            sender.sendMessage(this.messages.messageFor(MessageKeys.EDITOR_RELOAD_OK,
                    MessageContainer.value("count", String.valueOf(report.loaded()))));
            for (Map.Entry<String, String> failure : report.failures().entrySet()) {
                sender.sendMessage(this.messages.messageFor(MessageKeys.EDITOR_RELOAD_FAILURE,
                        MessageContainer.value("file", failure.getKey()),
                        MessageContainer.value("reason", failure.getValue())));
            }
        });
    }
}
