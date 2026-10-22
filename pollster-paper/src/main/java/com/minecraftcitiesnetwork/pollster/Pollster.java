package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.api.PollsterApi;
import com.minecraftcitiesnetwork.pollster.backend.MariaPollsterBackend;
import com.minecraftcitiesnetwork.pollster.backend.PollsterBackend;
import com.minecraftcitiesnetwork.pollster.backend.PollsterDatabase;
import com.minecraftcitiesnetwork.pollster.config.ConfigLoader;
import com.minecraftcitiesnetwork.pollster.config.DatabaseConfig;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.rewards.RewardRunner;
import com.minecraftcitiesnetwork.pollster.store.LoadReport;
import com.minecraftcitiesnetwork.pollster.store.SurveyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
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

        this.service = new DefaultSurveyService(this.store, backend, this.executorState, getLogger());
        // Blocking variant: the main thread cannot wait on a future that needs the main thread.
        this.service.refreshLocksBlocking();
        this.rewards = new RewardRunner(getServer(), getLogger());
        this.api = new PollsterApiImpl(this.service);
        getServer().getServicesManager().register(PollsterApi.class, this.api, this, ServicePriority.Normal);

        // wired in integration: SessionRegistry, PlayerFlow, FeaturedPicker and the player listeners
        // wired in integration: AdminUi screens and the ReportScreen opener
        // wired in integration: Cloud commands (/surveys, /pollster, /pollster reload, report|publish|export)
        getLogger().info("Pollster enabled");
    }

    private void failEnable(String message, Throwable cause) {
        getLogger().severe(message);
        getLogger().log(Level.FINE, "Enable failure detail", cause);
        getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        // Reverse of enable: stop handing out the API, drain executors, then close the pool they use.
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
        if (exec == null || surveyStore == null || surveyService == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Pollster is not enabled."));
        }
        ConfigLoader loader = new ConfigLoader(getDataFolder().toPath(), getClassLoader(), getLogger());
        record Loaded(Settings settings, Messages messages, LoadReport report) {
        }
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        Settings newSettings = loader.load("settings.yml", Settings.class);
                        Messages newMessages = loader.loadMessages();
                        return new Loaded(newSettings, newMessages, surveyStore.loadAll());
                    } catch (Exception ex) {
                        throw new IllegalStateException("Reload failed: " + ex.getMessage(), ex);
                    }
                }, exec.database())
                .thenApplyAsync(loaded -> {
                    this.settings.set(loaded.settings());
                    this.messages.set(loaded.messages());
                    return loaded.report();
                }, exec.mainThread())
                .thenCompose(report -> surveyService.refreshLocks().thenApply(locks -> report));
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
}
