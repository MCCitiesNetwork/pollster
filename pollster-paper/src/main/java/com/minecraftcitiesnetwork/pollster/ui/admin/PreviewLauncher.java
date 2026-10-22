package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Starts a preview session: the survey as a player sees it, with nothing saved. */
@FunctionalInterface
public interface PreviewLauncher {

    void preview(@NotNull Player player, @NotNull SurveyDefinition survey);
}
