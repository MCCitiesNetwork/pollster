package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.SurveyService.SurveyLock;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.command.PollsterCommand;
import com.minecraftcitiesnetwork.pollster.command.ReportCommands;
import com.minecraftcitiesnetwork.pollster.command.SurveysCommand;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.listener.JoinListener;
import com.minecraftcitiesnetwork.pollster.listener.ReminderTask;
import com.minecraftcitiesnetwork.pollster.publish.ExportWriter;
import com.minecraftcitiesnetwork.pollster.publish.PasteClient;
import com.minecraftcitiesnetwork.pollster.publish.ReportPublisher;
import com.minecraftcitiesnetwork.pollster.publish.SnapshotBuilder;
import com.minecraftcitiesnetwork.pollster.rewards.RewardRunner;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.session.SessionRegistry;
import com.minecraftcitiesnetwork.pollster.store.LoadReport;
import com.minecraftcitiesnetwork.pollster.store.SurveyStore;
import com.minecraftcitiesnetwork.pollster.ui.DialogRenderer;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import com.minecraftcitiesnetwork.pollster.ui.admin.AdminUi;
import com.minecraftcitiesnetwork.pollster.ui.admin.HubScreen;
import com.minecraftcitiesnetwork.pollster.ui.admin.ReportScreen;
import com.minecraftcitiesnetwork.pollster.ui.player.PlayerFlow;
import com.minecraftcitiesnetwork.pollster.ui.player.PlayerUi;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The player, admin and report object graph, built once on enable. It lives apart from the plugin
 * class so a test can build the whole graph from mocks and check the command tree.
 *
 * <p>Reload strategy: nothing here captures {@link Settings} or {@link Messages} by value. Every
 * holder reads through the plugin's atomic references (directly, or via {@link Style},
 * {@link SoundPlayer}, {@link PlayerUi} and {@link AdminUi}), so a reload swaps both references and
 * the next screen, chat line or reminder uses the new values. Commands and listeners are never
 * rebuilt, which matters because Cloud commands cannot be re-registered after enable.
 */
public final class PluginComponents {

    private final SurveyService service;
    private final SessionRegistry sessions;
    private final DialogRenderer renderer;
    private final PlayerFlow playerFlow;
    private final AdminUi adminUi;
    private final ReportPublisher publisher;
    private final ReportScreen.Opener reportOpener;
    private final JoinListener joinListener;
    private final ReminderTask reminderTask;
    private final Supplier<CompletableFuture<LoadReport>> reloader;
    private final Supplier<Messages> messages;

    public PluginComponents(@NotNull Plugin plugin, @NotNull Server server, @NotNull Logger log,
                            @NotNull SurveyService service, @NotNull SurveyStore store,
                            @NotNull RewardRunner rewards, @NotNull Supplier<Settings> settings,
                            @NotNull Supplier<Messages> messages, @NotNull ExecutorState exec,
                            @NotNull Path dataFolder, @NotNull Supplier<CompletableFuture<LoadReport>> reloader,
                            @NotNull Clock clock) {
        this.service = service;
        this.reloader = reloader;
        this.messages = messages;
        this.sessions = new SessionRegistry(clock);
        this.renderer = new DialogRenderer();
        Style style = new Style(messages);
        SoundPlayer sounds = new SoundPlayer(() -> settings.get().sounds());

        PlayerUi playerUi = new PlayerUi(service, this.sessions, rewards, messages, style, settings, sounds,
                server.getPluginManager()::callEvent);
        this.playerFlow = new PlayerFlow(playerUi, this.renderer::show);

        PasteClient paste = new PasteClient(HttpClient.newHttpClient(), exec.network());
        // File writes for exports block, so they run on virtual threads like the network calls.
        this.publisher = new ReportPublisher(service, new SnapshotBuilder(clock, new SecureRandom()), paste,
                new ExportWriter(), settings, messages, dataFolder.resolve("exports"), clock,
                exec.network(), exec.mainThread());

        // The report's Back button reopens the admin hub, and the hub's Report button opens the
        // report, so one side has to be filled in after the other exists.
        AtomicReference<AdminUi> admin = new AtomicReference<>();
        ReportScreen.Deps deps = new ReportScreen.Deps(style, this.renderer, service, this.publisher,
                (ctx, surveyId) -> HubScreen.open(admin.get(), ctx, surveyId));
        this.reportOpener = ReportScreen.opener(deps);
        this.adminUi = new AdminUi(service, store, messages, style, this.sessions::endAllFor,
                this.playerFlow::startPreview, this.reportOpener::open);
        admin.set(this.adminUi);

        this.joinListener = new JoinListener(plugin, server, log, service, this.sessions, this.playerFlow,
                settings, clock);
        this.reminderTask = new ReminderTask(this.sessions, clock, settings,
                id -> Optional.ofNullable(server.getPlayer(id)), (player, line) -> player.sendMessage(line),
                messages);
    }

    /** Registers {@code /surveys} and every {@code /pollster} subcommand on one manager. */
    public void registerCommands(@NotNull CommandManager<Source> manager) {
        new SurveysCommand(this.playerFlow).register(manager);
        // PollsterCommand and ReportCommands both build from the "pollster" literal; Cloud merges
        // commands that share a root into one tree node.
        new PollsterCommand(this.adminUi, this.renderer, this.reloader, this.messages).register(manager);
        new ReportCommands(this.service, this.publisher, this.reportOpener, this.messages).register(manager);
    }

    /** Ids of surveys players can currently take; captured before a reload to compare after it. */
    public @NotNull Set<String> surveysWithLockOk() {
        return this.service.surveys().stream().map(SurveyDefinition::id)
                .filter(id -> this.service.lock(id) == SurveyLock.OK)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Ends the sessions of every survey that was takeable before a reload and is not now (its file
     * changed after opening, or it was removed), so no one submits answers to edited questions.
     */
    public void endSessionsLostOnReload(@NotNull Set<String> okBefore) {
        for (String id : okBefore) {
            if (this.service.lock(id) != SurveyLock.OK || this.service.survey(id).isEmpty()) {
                this.sessions.endAllFor(id);
            }
        }
    }

    /** Ends every session, for plugin disable. */
    public void endAllSessions() {
        for (PlayerSession session : this.sessions.all()) {
            this.sessions.end(session.player());
        }
    }

    public @NotNull SessionRegistry sessions() {
        return this.sessions;
    }

    public @NotNull PlayerFlow playerFlow() {
        return this.playerFlow;
    }

    public @NotNull AdminUi adminUi() {
        return this.adminUi;
    }

    public @NotNull ReportScreen.Opener reportOpener() {
        return this.reportOpener;
    }

    public @NotNull JoinListener joinListener() {
        return this.joinListener;
    }

    public @NotNull ReminderTask reminderTask() {
        return this.reminderTask;
    }
}
