package com.minecraftcitiesnetwork.pollster.command;

import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.publish.ReportPublisher;
import com.minecraftcitiesnetwork.pollster.ui.admin.ReportScreen;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * {@code /pollster report|publish|export <id>}, all under permission {@code pollster.admin.report}.
 * Registers beside {@code PollsterCommand} on the same root literal; Cloud merges the two trees.
 */
public final class ReportCommands {

    public static final String PERMISSION = "pollster.admin.report";

    private final SurveyService service;
    private final ReportPublisher publisher;
    private final ReportScreen.Opener opener;
    private final Supplier<Messages> messages;

    public ReportCommands(@NotNull SurveyService service, @NotNull ReportPublisher publisher,
                          @NotNull ReportScreen.Opener opener, @NotNull Supplier<Messages> messages) {
        this.service = service;
        this.publisher = publisher;
        this.opener = opener;
        this.messages = messages;
    }

    public void register(@NotNull CommandManager<CommandSourceStack> manager) {
        // Dialogs need a player; publish and export only send chat, so the console may run them.
        manager.command(command(manager, "report", (sender, id) -> {
            if (sender instanceof Player player) {
                this.opener.open(player, id);
            } else {
                sender.sendMessage(this.messages.get().messageFor(MessageKeys.REPORT_PLAYER_ONLY));
            }
        }));
        manager.command(command(manager, "publish", this.publisher::publish));
        manager.command(command(manager, "export", this.publisher::export));
    }

    private org.incendo.cloud.Command<CommandSourceStack> command(CommandManager<CommandSourceStack> manager,
                                                                   String literal,
                                                                   BiConsumer<CommandSender, String> action) {
        CommandComponent.Builder<CommandSourceStack, String> id = CommandComponent
                .<CommandSourceStack, String>builder("id", StringParser.stringParser())
                .suggestionProvider(SuggestionProvider.blockingStrings((context, input) -> surveyIds()));
        return manager.commandBuilder("pollster")
                .literal(literal)
                .required(id)
                .permission(PERMISSION)
                .handler((CommandContext<CommandSourceStack> context) -> {
                    CommandSender sender = context.sender().getSender();
                    String surveyId = context.get("id");
                    if (this.service.survey(surveyId).isEmpty()) {
                        sender.sendMessage(this.messages.get().messageFor(MessageKeys.REPORT_UNKNOWN_SURVEY,
                                MessageContainer.value("id", surveyId)));
                        return;
                    }
                    action.accept(sender, surveyId);
                })
                .build();
    }

    private List<String> surveyIds() {
        return this.service.surveys().stream().map(SurveyDefinition::id).toList();
    }
}
