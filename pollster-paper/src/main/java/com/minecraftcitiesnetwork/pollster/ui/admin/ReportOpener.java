package com.minecraftcitiesnetwork.pollster.ui.admin;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Opens the report screen for a survey. */
@FunctionalInterface
public interface ReportOpener {

    void open(@NotNull Player player, @NotNull String surveyId);
}
