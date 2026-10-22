package com.minecraftcitiesnetwork.pollster.ui;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record Screen(@NotNull Component title, @NotNull List<Component> body, @NotNull List<Input> inputs,
                     @NotNull List<Button> buttons, int columns, boolean closeOnEscape) {

    public Screen {
        body = List.copyOf(body);
        inputs = List.copyOf(inputs);
        buttons = List.copyOf(buttons);
        columns = Math.max(1, columns);
    }

    public static @NotNull Builder builder(@NotNull Component title) {
        return new Builder(title);
    }

    public static final class Builder {
        private final Component title;
        private final List<Component> body = new ArrayList<>();
        private final List<Input> inputs = new ArrayList<>();
        private final List<Button> buttons = new ArrayList<>();
        private int columns = 1;
        private boolean closeOnEscape = true;

        private Builder(Component title) {
            this.title = title;
        }

        public @NotNull Builder body(@NotNull Component line) {
            this.body.add(line);
            return this;
        }

        public @NotNull Builder input(@NotNull Input input) {
            this.inputs.add(input);
            return this;
        }

        public @NotNull Builder button(@NotNull Button button) {
            this.buttons.add(button);
            return this;
        }

        public @NotNull Builder columns(int columns) {
            this.columns = columns;
            return this;
        }

        public @NotNull Builder closeOnEscape(boolean closeOnEscape) {
            this.closeOnEscape = closeOnEscape;
            return this;
        }

        public @NotNull Screen build() {
            return new Screen(this.title, this.body, this.inputs, this.buttons, this.columns, this.closeOnEscape);
        }
    }
}
