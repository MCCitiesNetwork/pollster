package com.minecraftcitiesnetwork.pollster.ui.common;

import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import org.jetbrains.annotations.NotNull;

/** Button-less notice shown while a database call runs. */
public final class LoadingScreen {

    private LoadingScreen() {
    }

    public static @NotNull Screen create(@NotNull Style style, @NotNull String title) {
        return Screens.loading(style, style.title(title));
    }
}
