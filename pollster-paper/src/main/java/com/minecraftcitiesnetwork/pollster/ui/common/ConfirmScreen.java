package com.minecraftcitiesnetwork.pollster.ui.common;

import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Screens;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/** Confirm / Cancel prompt with a red title, for destructive or irreversible actions. */
public final class ConfirmScreen {

    private ConfirmScreen() {
    }

    public static @NotNull Screen create(@NotNull Style style, @NotNull String title, @NotNull Component message,
                                         @NotNull Consumer<ScreenContext> onConfirm,
                                         @NotNull Consumer<ScreenContext> onCancel) {
        return Screens.confirm(style, style.danger(title), message, onConfirm, onCancel);
    }
}
