package com.minecraftcitiesnetwork.pollster.ui.common;

import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/** Error message with optional detail bullets and an OK button. */
public final class ErrorScreen {

    private ErrorScreen() {
    }

    public static @NotNull Screen create(@NotNull Style style, @NotNull String message,
                                         @NotNull List<Component> details,
                                         @NotNull Consumer<ScreenContext> onOk) {
        return Screens.error(style, style.error(message), details, onOk);
    }
}
