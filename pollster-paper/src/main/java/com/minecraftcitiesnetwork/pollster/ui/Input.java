package com.minecraftcitiesnetwork.pollster.ui;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** A form field on a {@link Screen}. Values come back through {@link ScreenContext} by key. */
public sealed interface Input permits Input.TextInput, Input.BoolInput, Input.ChoiceInput, Input.RangeInput {

    /** Paper refuses any other dialog input key when the dialog is built. */
    Pattern VALID_KEY = Pattern.compile("[a-zA-Z0-9_]+");

    @NotNull String key();

    private static void requireValidKey(@NotNull String key) {
        if (!VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Input key \"" + key + "\" must use only letters, digits and underscores");
        }
    }

    @NotNull Component label();

    record TextInput(@NotNull String key, @NotNull Component label, @NotNull String initial,
                     int maxLength, boolean multiline) implements Input {
        public TextInput {
            Input.requireValidKey(key);
        }
    }

    record BoolInput(@NotNull String key, @NotNull Component label, boolean initial) implements Input {
        public BoolInput {
            Input.requireValidKey(key);
        }
    }

    /** Pick one option; the value returned for the key is the chosen option key. */
    record ChoiceInput(@NotNull String key, @NotNull Component label,
                       @NotNull List<Map.Entry<String, Component>> options,
                       @NotNull String initialKey) implements Input {
        public ChoiceInput {
            Input.requireValidKey(key);
            options = List.copyOf(options);
        }
    }

    record RangeInput(@NotNull String key, @NotNull Component label, float min, float max,
                      float step, float initial) implements Input {
        public RangeInput {
            Input.requireValidKey(key);
        }
    }
}
