package com.minecraftcitiesnetwork.pollster.ui;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public record Button(@NotNull Component label, @Nullable Component tooltip, int width,
                     @NotNull Consumer<ScreenContext> onClick) {

    public static final int DEFAULT_WIDTH = 150;

    public static @NotNull Button of(@NotNull Component label, @NotNull Consumer<ScreenContext> onClick) {
        return new Button(label, null, DEFAULT_WIDTH, onClick);
    }
}
