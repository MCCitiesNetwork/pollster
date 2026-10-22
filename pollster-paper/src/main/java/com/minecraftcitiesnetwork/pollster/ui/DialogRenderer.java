package com.minecraftcitiesnetwork.pollster.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns a {@link Screen} into a Paper dialog and wires its buttons to {@link ScreenContext}. */
public final class DialogRenderer {

    /** Buttons on a screen the player keeps open stay usable for this long. */
    private static final Duration BUTTON_LIFETIME = Duration.ofMinutes(10);
    private static final int INPUT_WIDTH = 300;
    private static final int MULTILINE_LINES = 6;
    private static final int MULTILINE_HEIGHT = 100;

    private final ClickCallback.Options clickOptions = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(BUTTON_LIFETIME)
            .build();

    public void show(@NotNull Player player, @NotNull Screen screen) {
        player.showDialog(render(screen, player));
    }

    /** Builds the dialog without a viewer; button clicks resolve the player from the audience. */
    public @NotNull Dialog render(@NotNull Screen screen) {
        return render(screen, null);
    }

    private @NotNull Dialog render(@NotNull Screen screen, @Nullable Player viewer) {
        List<DialogBody> body = new ArrayList<>();
        for (Component line : screen.body()) {
            body.add(DialogBody.plainMessage(line));
        }
        List<DialogInput> inputs = new ArrayList<>();
        for (Input input : screen.inputs()) {
            inputs.add(toDialogInput(input));
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (Button button : screen.buttons()) {
            ActionButton.Builder builder = ActionButton.builder(button.label())
                    .width(button.width())
                    .action(DialogAction.customClick((response, audience) -> {
                        Player player = viewer != null ? viewer : (audience instanceof Player p ? p : null);
                        if (player != null) {
                            button.onClick().accept(new Context(player, response));
                        }
                    }, this.clickOptions));
            if (button.tooltip() != null) {
                builder.tooltip(button.tooltip());
            }
            buttons.add(builder.build());
        }

        DialogBase base = DialogBase.builder(screen.title())
                .canCloseWithEscape(screen.closeOnEscape())
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(inputs)
                .build();
        return Dialog.create(factory -> {
            var empty = factory.empty().base(base);
            if (buttons.isEmpty()) {
                // A screen with no buttons (loading) still needs a way out for the client.
                empty.type(DialogType.notice());
            } else {
                empty.type(DialogType.multiAction(buttons).columns(screen.columns()).build());
            }
        });
    }

    private static @NotNull DialogInput toDialogInput(@NotNull Input input) {
        return switch (input) {
            case Input.TextInput t -> {
                TextDialogInput.Builder builder = DialogInput.text(t.key(), t.label())
                        .width(INPUT_WIDTH)
                        .initial(t.initial())
                        .maxLength(t.maxLength());
                if (t.multiline()) {
                    builder.multiline(TextDialogInput.MultilineOptions.create(MULTILINE_LINES, MULTILINE_HEIGHT));
                }
                yield builder.build();
            }
            case Input.BoolInput b -> DialogInput.bool(b.key(), b.label())
                    .initial(b.initial())
                    .onTrue("true")
                    .onFalse("false")
                    .build();
            case Input.ChoiceInput c -> {
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                for (Map.Entry<String, Component> option : c.options()) {
                    entries.add(SingleOptionDialogInput.OptionEntry.create(option.getKey(), option.getValue(),
                            option.getKey().equals(c.initialKey())));
                }
                yield DialogInput.singleOption(c.key(), c.label(), entries)
                        .width(INPUT_WIDTH)
                        .labelVisible(true)
                        .build();
            }
            case Input.RangeInput r -> DialogInput.numberRange(r.key(), r.label(), r.min(), r.max())
                    .width(INPUT_WIDTH)
                    .initial(r.initial())
                    .step(r.step())
                    .build();
        };
    }

    private final class Context implements ScreenContext {
        private final Player player;
        private final DialogResponseView response;

        private Context(Player player, DialogResponseView response) {
            this.player = player;
            this.response = response;
        }

        @Override
        public @NotNull Player player() {
            return this.player;
        }

        @Override
        public @Nullable String text(@NotNull String key) {
            return this.response == null ? null : this.response.getText(key);
        }

        @Override
        public @Nullable Boolean bool(@NotNull String key) {
            return this.response == null ? null : this.response.getBoolean(key);
        }

        @Override
        public @Nullable Float number(@NotNull String key) {
            return this.response == null ? null : this.response.getFloat(key);
        }

        @Override
        public void open(@NotNull Screen screen) {
            show(this.player, screen);
        }

        @Override
        public void close() {
            this.player.closeDialog();
        }
    }
}
