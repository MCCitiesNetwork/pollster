package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.store.SurveyStore;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Everything the admin screens need, passed explicitly so screens stay static factories.
 *
 * @param endSessions ends every active player session for a survey id (the player flow's
 *                    {@code SessionRegistry.endAllFor})
 * @param preview     starts a preview session of a survey for an admin
 * @param report      opens the report screen for a survey id
 */
public record AdminUi(@NotNull SurveyService service, @NotNull SurveyStore store, @NotNull Messages messages,
                      @NotNull Style style, @NotNull Consumer<String> endSessions,
                      @NotNull PreviewLauncher preview, @NotNull ReportOpener report) {
}
