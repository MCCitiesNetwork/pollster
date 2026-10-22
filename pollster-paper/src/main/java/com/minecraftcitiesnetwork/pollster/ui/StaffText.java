package com.minecraftcitiesnetwork.pollster.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.jetbrains.annotations.NotNull;

/**
 * Renders text written by staff (survey names and descriptions, question text, option labels).
 * The spec makes it MiniMessage, but only the look may change: colours, decorations, gradients,
 * rainbow, reset and newlines. Click, hover, insertion, fonts, selectors and the like are not
 * known to this parser, so they stay as literal text and can never act on whoever reads them.
 * Text typed by players never goes through here; it is always shown literally.
 */
public final class StaffText {

    private static final MiniMessage RESTRICTED = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset(),
                    StandardTags.newline()))
            .build();

    private StaffText() {
    }

    public static @NotNull Component render(@NotNull String text) {
        return RESTRICTED.deserialize(text);
    }
}
