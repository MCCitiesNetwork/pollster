package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.ui.StaffText;
import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import org.jetbrains.annotations.NotNull;

/** Shown after a response is saved, or at the end of a preview. */
public final class ThanksScreen {

    private ThanksScreen() {
    }

    public static @NotNull Screen create(@NotNull PlayerUi ui, @NotNull SurveyDefinition survey) {
        return create(ui, survey, false);
    }

    public static @NotNull Screen create(@NotNull PlayerUi ui, @NotNull SurveyDefinition survey, boolean preview) {
        Screen.Builder builder = Screen.builder(PlayerScreens.title(ui, survey, preview));
        builder.body(ui.messages().messageFor(MessageKeys.SURVEY_THANKS_BODY,
                MessageContainer.markup("name", StaffText.render(survey.name()))));
        if (preview) {
            builder.body(ui.messages().messageFor(MessageKeys.SURVEY_THANKS_PREVIEW));
        }
        builder.button(Button.of(ui.style().secondary("Close"), ScreenContext::close));
        return builder.build();
    }
}
