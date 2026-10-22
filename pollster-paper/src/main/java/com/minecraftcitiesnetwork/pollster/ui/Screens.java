package com.minecraftcitiesnetwork.pollster.ui;

import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/** The shared screens: confirmation, error and loading. */
public final class Screens {

    private Screens() {
    }

    public static @NotNull Screen confirm(@NotNull Component title, @NotNull Component message,
                                          @NotNull Consumer<ScreenContext> onConfirm,
                                          @NotNull Consumer<ScreenContext> onCancel) {
        return confirm(Style.defaults(), title, message, onConfirm, onCancel);
    }

    public static @NotNull Screen confirm(@NotNull Style style, @NotNull Component title,
                                          @NotNull Component message,
                                          @NotNull Consumer<ScreenContext> onConfirm,
                                          @NotNull Consumer<ScreenContext> onCancel) {
        return Screen.builder(title)
                .body(message)
                .button(Button.of(style.primary(style.word(MessageKeys.UI_BUTTON_CONFIRM)), onConfirm))
                .button(Button.of(style.secondary(style.word(MessageKeys.UI_BUTTON_CANCEL)), onCancel))
                .columns(2)
                .build();
    }

    public static @NotNull Screen error(@NotNull Component message, @NotNull List<Component> details,
                                        @NotNull Consumer<ScreenContext> onOk) {
        return error(Style.defaults(), message, details, onOk);
    }

    public static @NotNull Screen error(@NotNull Style style, @NotNull Component message,
                                        @NotNull List<Component> details,
                                        @NotNull Consumer<ScreenContext> onOk) {
        Screen.Builder builder = Screen.builder(style.danger(style.word(MessageKeys.UI_TITLE_ERROR))).body(message);
        for (Component detail : details) {
            builder.body(Component.text("• ", NamedTextColor.GRAY)
                    .append(detail.colorIfAbsent(NamedTextColor.GRAY)));
        }
        return builder.button(Button.of(style.secondary(style.word(MessageKeys.UI_BUTTON_OK)), onOk)).build();
    }

    /** A notice with no buttons, shown while a database call runs. */
    public static @NotNull Screen loading(@NotNull Component title) {
        return loading(Style.defaults(), title);
    }

    public static @NotNull Screen loading(@NotNull Style style, @NotNull Component title) {
        return Screen.builder(title)
                .body(Component.text(style.word(MessageKeys.UI_LOADING), NamedTextColor.GRAY)).build();
    }
}
