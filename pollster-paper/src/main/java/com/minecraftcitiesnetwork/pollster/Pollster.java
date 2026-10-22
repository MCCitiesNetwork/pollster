package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.api.PollsterApi;
import com.minecraftcitiesnetwork.pollster.backend.MariaPollsterBackend;
import com.minecraftcitiesnetwork.pollster.backend.PollsterBackend;
import com.minecraftcitiesnetwork.pollster.backend.PollsterDatabase;
import com.minecraftcitiesnetwork.pollster.config.ConfigLoader;
import com.minecraftcitiesnetwork.pollster.config.DatabaseConfig;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.namebook.api.NamebookFinder;
import com.minecraftcitiesnetwork.pollster.names.NamebookPlayerNames;
import com.minecraftcitiesnetwork.pollster.names.PlayerNames;
import com.minecraftcitiesnetwork.pollster.names.ServerPlayerNames;
import com.minecraftcitiesnetwork.pollster.rewards.RewardRunner;
import com.minecraftcitiesnetwork.pollster.store.LoadReport;
import com.minecraftcitiesnetwork.pollster.store.SurveyStore;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plugin entry point and composition root. Every collaborator is constructed here and receives
 * its dependencies through its constructor.
 */
public final class Pollster extends JavaPlugin {

    private final AtomicReference<Settings> settings = new AtomicReference<>();
    private final AtomicReference<Messages> messages = new AtomicReference<>();
    private @Nullable ExecutorState executorState;
    private @Nullable PollsterDatabase database;
    private @Nullable SurveyStore store;
    private @Nullable DefaultSurveyService service;
    private @Nullable RewardRunner rewards;
    private @Nullable PollsterApi api;
    private @Nullable PluginComponents components;
    private @Nullable BukkitTask reminderTask;

    /** Reminders check idle sessions once a second. */
    private static final long REMINDER_PERIOD_TICKS = 20L;

    @Override
    public void onEnable() {
        ConfigLoader loader = new ConfigLoader(getDataFolder().toPath(), getClassLoader(), getLogger());
        DatabaseConfig databaseConfig;
        try {
            databaseConfig = loader.load("database.yml", DatabaseConfig.class);
            this.settings.set(loader.load("settings.yml", Settings.class));
            this.messages.set(loader.loadMessages());
        } catch (Exception ex) {
            getLogger().severe("Could not read Pollster's configuration, disabling: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (databaseConfig.isBlank()) {
            getLogger().severe("database.yml has no url. Fill in the database connection, then restart. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Threads need the plugin class loader so the JDBC driver and MyBatis resources resolve.
        ClassLoader pluginClassLoader = getClassLoader();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setContextClassLoader(pluginClassLoader);
            return thread;
        };
        this.executorState = new ExecutorState(getServer().getScheduler().getMainThreadExecutor(this),
                Executors.newFixedThreadPool(4, threadFactory),
                Executors.newVirtualThreadPerTaskExecutor());

        // Opening runs the migrations; blocking here keeps the plugin from enabling without a schema.
        try {
            this.database = this.executorState.database()
                    .submit(() -> PollsterDatabase.open(databaseConfig.toSettings(), getLogger())).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failEnable("Interrupted while opening the database.", ex);
            return;
        } catch (ExecutionException ex) {
            failEnable("Could not open the database: " + ex.getCause().getMessage(), ex.getCause());
            return;
        }
        PollsterBackend backend = new MariaPollsterBackend(this.database);

        this.store = new SurveyStore(getDataFolder().toPath().resolve("surveys"), getLogger());
        LoadReport report = this.store.loadAll();
        getLogger().info("Loaded " + report.loaded() + " surveys");

        PlayerNames names = choosePlayerNames();
        this.service = new DefaultSurveyService(this.store, backend, names, this.executorState, getLogger());
        // Blocking variant: the main thread cannot wait on a future that needs the main thread.
        this.service.refreshLocksBlocking();
        this.rewards = new RewardRunner(getServer(), getLogger());
        this.api = new PollsterApiImpl(this.service);
        getServer().getServicesManager().register(PollsterApi.class, this.api, this, ServicePriority.Normal);

        this.components = new PluginComponents(this, getServer(), getLogger(), this.service, this.store,
                this.rewards, this.settings::get, this.messages::get, this.executorState,
                getDataFolder().toPath(), this::reload, Clock.systemUTC());
        getServer().getPluginManager().registerEvents(this.components.joinListener(), this);
        this.reminderTask = getServer().getScheduler().runTaskTimer(this, this.components.reminderTask(),
                REMINDER_PERIOD_TICKS, REMINDER_PERIOD_TICKS);

        PaperCommandManager<Source> commands = PaperCommandManager
                .builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.<Source>simpleCoordinator())
                .buildOnEnable(this);
        this.components.registerCommands(commands);
        getLogger().info("Pollster enabled");
    }

    private void failEnable(String message, Throwable cause) {
        getLogger().severe(message);
        getLogger().log(Level.FINE, "Enable failure detail", cause);
        getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        // Reverse of enable. Cloud's Paper manager registers through the lifecycle API, which has
        // no unregister; Paper drops a disabled plugin's commands itself.
        if (this.reminderTask != null) {
            this.reminderTask.cancel();
            this.reminderTask = null;
        }
        HandlerList.unregisterAll(this);
        if (this.components != null) {
            // Dialog buttons still on screen check the session token, so they go stale, not live.
            this.components.endAllSessions();
            this.components = null;
        }
        // Stop handing out the API, drain executors, then close the pool they use.
        getServer().getServicesManager().unregisterAll(this);
        if (this.executorState != null) {
            this.executorState.shutdown(getLogger());
        }
        if (this.database != null) {
            this.database.close();
        }
    }

    /**
     * Re-reads configuration and every survey file off the main thread, swaps the results in, then
     * recomputes content locks. The returned future completes on the main thread.
     */
    public @NotNull CompletableFuture<LoadReport> reload() {
        ExecutorState exec = this.executorState;
        SurveyStore surveyStore = this.store;
        DefaultSurveyService surveyService = this.service;
        PluginComponents wired = this.components;
        if (exec == null || surveyStore == null || surveyService == null || wired == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Pollster is not enabled."));
        }
        ConfigLoader loader = new ConfigLoader(getDataFolder().toPath(), getClassLoader(), getLogger());
        record Loaded(Settings settings, Messages messages, LoadReport report) {
        }
        // Captured on the calling (main) thread, before the store or the locks change.
        Set<String> takeableBefore = wired.takeableSurveys();
        return exec.onMainThread(CompletableFuture.supplyAsync(() -> {
                    try {
                        Settings newSettings = loader.load("settings.yml", Settings.class);
                        Messages newMessages = loader.loadMessages();
                        return new Loaded(newSettings, newMessages, surveyStore.loadAll());
                    } catch (Exception ex) {
                        throw new IllegalStateException("Reload failed: " + ex.getMessage(), ex);
                    }
                }, exec.database()))
                .thenApply(loaded -> {
                    this.settings.set(loaded.settings());
                    this.messages.set(loaded.messages());
                    return loaded.report();
                })
                .thenCompose(report -> surveyService.refreshLocks().handle((locks, error) -> {
                    // Players mid-survey must not submit answers to questions that changed under them.
                    // refreshLocks completes on the main thread either way. When it failed, nothing
                    // could be verified, so every session that might be affected is ended.
                    wired.endSessionsLostOnReload(takeableBefore, error == null);
                    if (error != null) {
                        throw error instanceof CompletionException wrapped ? wrapped : new CompletionException(error);
                    }
                    return report;
                }));
    }

    public @NotNull AtomicReference<Settings> settings() {
        return this.settings;
    }

    public @NotNull AtomicReference<Messages> messages() {
        return this.messages;
    }

    public @Nullable ExecutorState executorState() {
        return this.executorState;
    }

    public @Nullable SurveyService surveyService() {
        return this.service;
    }

    public @Nullable RewardRunner rewards() {
        return this.rewards;
    }

    /** Namebook when it is installed, otherwise the server's own user cache. */
    private PlayerNames choosePlayerNames() {
        // Namebook is a soft dependency, so its classes may be absent; check the plugin first.
        if (getServer().getPluginManager().getPlugin("Namebook") != null) {
            try {
                var namebook = NamebookFinder.find(getServer().getServicesManager());
                if (namebook.isPresent()) {
                    getLogger().info("Resolving player names through Namebook");
                    return new NamebookPlayerNames(namebook.get());
                }
            } catch (LinkageError e) {
                getLogger().warning("Namebook is installed but its API could not be loaded: " + e);
            }
        }
        getLogger().info("Resolving player names from the server's user cache");
        return new ServerPlayerNames(getServer(), this.executorState.network());
    }
}
