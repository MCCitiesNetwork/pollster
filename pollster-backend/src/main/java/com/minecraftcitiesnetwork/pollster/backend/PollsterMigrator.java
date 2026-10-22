package com.minecraftcitiesnetwork.pollster.backend;

import com.minecraftcitiesnetwork.pluginInfrastructure.sql.MigrationStep;
import com.minecraftcitiesnetwork.pluginInfrastructure.sql.SchemaMigrator;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/** Applies Pollster's schema migrations, tracking progress in {@code pollster_schema_version}. */
public final class PollsterMigrator extends SchemaMigrator {

    public static final List<MigrationStep> STEPS = List.of(
            new MigrationStep(1, "initial schema", "V1__initial_schema.sql"));

    public static final Path BASE_DIR = Path.of("sql/migrations");

    @Override
    protected @NotNull String bootstrapDdl() {
        return "CREATE TABLE IF NOT EXISTS pollster_schema_version ("
                + "version INT PRIMARY KEY, "
                + "description VARCHAR(255), "
                + "applied_at DATETIME DEFAULT NOW())";
    }

    @Override
    protected @NotNull String selectVersion() {
        return "SELECT COALESCE(MAX(version), 0) FROM pollster_schema_version";
    }

    @Override
    protected @NotNull String insertVersion() {
        return "INSERT INTO pollster_schema_version (version, description) VALUES (?, ?)";
    }
}
