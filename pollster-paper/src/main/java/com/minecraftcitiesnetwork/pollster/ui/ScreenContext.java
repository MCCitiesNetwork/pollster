package com.minecraftcitiesnetwork.pollster.ui;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** What a button callback can see and do: the clicking player, the submitted inputs, navigation. */
public interface ScreenContext {

    @NotNull Player player();

    /** The text or chosen option key submitted for {@code key}, or null when absent. */
    @Nullable String text(@NotNull String key);

    @Nullable Boolean bool(@NotNull String key);

    @Nullable Float number(@NotNull String key);

    /** Shows another screen in place of this one. */
    void open(@NotNull Screen screen);

    void close();
}
