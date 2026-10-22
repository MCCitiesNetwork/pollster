package com.minecraftcitiesnetwork.pollster.ui;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** A form field on a {@link Screen}. Values come back through {@link ScreenContext} by key. */
public sealed interface Input permits Input.TextInput, Input.BoolInput, Input.ChoiceInput, Input.RangeInput {

    @NotNull String key();

    @NotNull Component label();

    record TextInput(@NotNull String key, @NotNull Component label, @NotNull String initial,
                     int maxLength, boolean multiline) implements Input {
    }

    record BoolInput(@NotNull String key, @NotNull Component label, boolean initial) implements Input {
    }

    /** Pick one option; the value returned for the key is the chosen option key. */
    record ChoiceInput(@NotNull String key, @NotNull Component label,
                       @NotNull List<Map.Entry<String, Component>> options,
                       @NotNull String initialKey) implements Input {
        public ChoiceInput {
            options = List.copyOf(options);
        }
    }

    record RangeInput(@NotNull String key, @NotNull Component label, float min, float max,
                      float step, float initial) implements Input {
    }
}
