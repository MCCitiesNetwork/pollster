package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.SurveyService.SurveyLock;
import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.rewards.RewardRunner;
import com.minecraftcitiesnetwork.pollster.store.LoadReport;
import com.minecraftcitiesnetwork.pollster.store.SurveyStore;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.paper.util.sender.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

/** Builds the whole player, admin and report graph from mocks and checks the command tree. */
class PluginComponentsTest {

    @TempDir
    Path dir;

    private final SurveyService service = Mockito.mock(SurveyService.class);
    private final AtomicInteger reloads = new AtomicInteger();
    private final AtomicReference<Messages> messages = new AtomicReference<>(new Messages());
    private ExecutorState exec;
    private PluginComponents components;
    private CommandManager<Source> manager;

    @BeforeEach
    void setUp() {
        Server server = Mockito.mock(Server.class);
        Mockito.when(server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));
        Logger log = Logger.getAnonymousLogger();
        this.exec = new ExecutorState(Runnable::run, Executors.newSingleThreadExecutor(),
                Executors.newVirtualThreadPerTaskExecutor());
        Settings settings = new Settings(null, null, 0, null, null);
        this.components = new PluginComponents(Mockito.mock(Plugin.class), server, log, this.service,
                new SurveyStore(this.dir.resolve("surveys"), log), new RewardRunner(server, log),
                () -> settings, this.messages::get, this.exec, this.dir, () -> {
                    this.reloads.incrementAndGet();
                    return CompletableFuture.completedFuture(new LoadReport(0, Map.of()));
                }, Clock.systemUTC());
        this.manager = new CommandManager<>(ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(Source sender, String permission) {
                return true;
            }
        };
    }

    @AfterEach
    void tearDown() {
        this.exec.shutdown(Logger.getAnonymousLogger());
    }

    @Test
    void registerCommands_sharesOnePollsterRoot() {
        this.components.registerCommands(this.manager);

        CommandNode<Source> pollster = this.manager.commandTree().getNamedNode("pollster");
        assertNotNull(pollster);
        List<String> children = pollster.children().stream().map(child -> child.component().name()).toList();
        assertTrue(children.containsAll(List.of("reload", "report", "publish", "export")), children.toString());
        assertNotNull(this.manager.commandTree().getNamedNode("surveys"));
    }

    @Test
    void pollsterReload_runsTheReloaderFromTheMergedTree() {
        this.components.registerCommands(this.manager);
        Source console = Mockito.mock(Source.class);
        Mockito.when(console.source()).thenReturn(Mockito.mock(CommandSender.class));

        this.manager.commandExecutor().executeCommand(console, "pollster reload").join();

        assertEquals(1, this.reloads.get());
    }

    @Test
    void surveys_fromConsoleRepliesPlayerOnly() {
        this.messages.get().setMessage("survey.player-only", "Players only.");
        this.components.registerCommands(this.manager);
        for (String command : List.of("surveys", "surveys resume")) {
            CommandSender sender = Mockito.mock(CommandSender.class);
            Source console = Mockito.mock(Source.class);
            Mockito.when(console.source()).thenReturn(sender);

            this.manager.commandExecutor().executeCommand(console, command).join();

            org.mockito.ArgumentCaptor<net.kyori.adventure.text.Component> sent =
                    org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
            Mockito.verify(sender).sendMessage(sent.capture());
            assertEquals("Players only.", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(sent.getValue()), command);
        }
    }

    @Test
    void endSessionsLostOnReload_endsOnlySurveysThatStoppedBeingOk() {
        SurveyDefinition changed = survey("changed");
        SurveyDefinition kept = survey("kept");
        Mockito.when(this.service.survey(anyString())).thenReturn(Optional.empty());
        Mockito.when(this.service.survey("changed")).thenReturn(Optional.of(changed));
        Mockito.when(this.service.survey("kept")).thenReturn(Optional.of(kept));
        Mockito.when(this.service.lock("changed")).thenReturn(SurveyLock.CONTENT_CHANGED);
        Mockito.when(this.service.lock("kept")).thenReturn(SurveyLock.OK);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        this.components.sessions().start(a, flow(changed), false);
        this.components.sessions().start(b, flow(kept), false);

        this.components.endSessionsLostOnReload(Set.of("changed", "kept"));

        assertTrue(this.components.sessions().get(a).isEmpty());
        assertTrue(this.components.sessions().get(b).isPresent());
    }

    @Test
    void endAllSessions_clearsTheRegistry() {
        this.components.sessions().start(UUID.randomUUID(), flow(survey("one")), false);
        this.components.sessions().start(UUID.randomUUID(), flow(survey("two")), true);

        this.components.endAllSessions();

        assertTrue(this.components.sessions().all().isEmpty());
    }

    private static SurveyDefinition survey(String id) {
        SurveyDefinition survey = Mockito.mock(SurveyDefinition.class);
        Mockito.when(survey.id()).thenReturn(id);
        return survey;
    }

    private static SurveyFlow flow(SurveyDefinition survey) {
        SurveyFlow flow = Mockito.mock(SurveyFlow.class);
        Mockito.when(flow.survey()).thenReturn(survey);
        return flow;
    }
}
