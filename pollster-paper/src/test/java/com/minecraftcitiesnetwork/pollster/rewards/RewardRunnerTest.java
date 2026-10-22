package com.minecraftcitiesnetwork.pollster.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RewardRunnerTest {

    private Server server;
    private ConsoleCommandSender console;
    private Player player;
    private final List<LogRecord> logs = new ArrayList<>();
    private RewardRunner runner;

    @BeforeEach
    void setUp() {
        this.server = mock(Server.class);
        this.console = mock(ConsoleCommandSender.class);
        this.player = mock(Player.class);
        when(this.server.getConsoleSender()).thenReturn(this.console);
        when(this.player.getName()).thenReturn("Steve");
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { logs.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        this.runner = new RewardRunner(this.server, log);
    }

    private static SurveyDefinition survey(String name, Reward... rewards) {
        return new SurveyDefinition("town-vote", name, "", SurveyStatus.OPEN, false, false, false,
                List.of(rewards), List.of());
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void run_dispatchesConsoleCommandWithPlayerName() {
        this.runner.run(this.player, survey("Town", new Reward.CommandReward("give {player} diamond 1")));
        verify(this.server).dispatchCommand(this.console, "give Steve diamond 1");
    }

    @Test
    void run_playerCommandRunsAsPlayer() {
        this.runner.run(this.player, survey("Town", new Reward.PlayerCommandReward("spawn {survey}")));
        verify(this.server).dispatchCommand(this.player, "spawn Town");
    }

    @Test
    void run_continuesAfterFailingReward() {
        when(this.server.dispatchCommand(this.console, "boom")).thenThrow(new IllegalStateException("x"));
        this.runner.run(this.player, survey("Town",
                new Reward.CommandReward("boom"), new Reward.CommandReward("ok")));
        verify(this.server).dispatchCommand(this.console, "ok");
        assertEquals(1, this.logs.stream().filter(r -> r.getLevel() == Level.SEVERE).count());
    }

    @Test
    void run_messageDoesNotParseSurveyName() {
        this.runner.run(this.player, survey("<red>x", new Reward.MessageReward("<green>Thanks {player} for {survey}")));
        ArgumentCaptor<Component> c = ArgumentCaptor.forClass(Component.class);
        verify(this.player).sendMessage(c.capture());
        assertEquals("Thanks Steve for <red>x", plain(c.getValue()));
    }

    @Test
    void run_broadcastSendsToServer() {
        this.runner.run(this.player, survey("Town", new Reward.BroadcastReward("<gold>{player} voted")));
        ArgumentCaptor<Component> c = ArgumentCaptor.forClass(Component.class);
        verify(this.server).broadcast(c.capture());
        assertEquals("Steve voted", plain(c.getValue()));
    }

    @Test
    void run_titleShowsTitle() {
        this.runner.run(this.player, survey("Town", new Reward.TitleReward("Thanks", "{player}")));
        ArgumentCaptor<Title> c = ArgumentCaptor.forClass(Title.class);
        verify(this.player).showTitle(c.capture());
        assertEquals("Thanks", plain(c.getValue().title()));
        assertEquals("Steve", plain(c.getValue().subtitle()));
        assertTrue(this.logs.isEmpty());
    }
}
