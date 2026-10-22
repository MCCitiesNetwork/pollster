package com.minecraftcitiesnetwork.pollster.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.time.Duration;

/**
 * Plugin behaviour settings from {@code settings.yml}. Every compact constructor repairs
 * missing or nonsensical values, so a hand-edited file can never leave a null or a zero
 * timeout in the running plugin.
 */
@ConfigSerializable
public record Settings(
        Featured featured,
        Reminders reminders,
        @Setting("text-max-length") int textMaxLength,
        Sounds sounds,
        Publishing publishing
) {

    static final int DEFAULT_TEXT_MAX_LENGTH = 255;

    public Settings {
        featured = featured == null ? new Featured(true, null, null) : featured;
        reminders = reminders == null ? new Reminders(true, null, null) : reminders;
        if (textMaxLength <= 0) {
            textMaxLength = DEFAULT_TEXT_MAX_LENGTH;
        }
        sounds = sounds == null ? new Sounds(null, null, null, null) : sounds;
        publishing = publishing == null ? new Publishing(null, null, null) : publishing;
    }

    /** Featured-survey prompt shown to players on join. */
    @ConfigSerializable
    public record Featured(
            @Setting("on-join") boolean onJoin,
            Duration delay,
            Duration cooldown
    ) {
        public Featured {
            delay = delay == null || delay.isNegative() ? Duration.ofSeconds(3) : delay;
            cooldown = cooldown == null || cooldown.isNegative() ? Duration.ofHours(24) : cooldown;
        }
    }

    /** Nudges for players who leave a survey unfinished. A zero {@code repeat} means remind once. */
    @ConfigSerializable
    public record Reminders(
            boolean enabled,
            Duration idle,
            Duration repeat
    ) {
        public Reminders {
            idle = idle == null || idle.isNegative() ? Duration.ofSeconds(60) : idle;
            repeat = repeat == null || repeat.isNegative() ? Duration.ofSeconds(300) : repeat;
        }
    }

    /** One sound per moment in the flow. */
    @ConfigSerializable
    public record Sounds(
            SoundSetting open,
            SoundSetting next,
            SoundSetting error,
            SoundSetting finish
    ) {
        public Sounds {
            open = open == null ? new SoundSetting("block.note_block.chime", 1.0f, 1.2f) : open;
            next = next == null ? new SoundSetting("ui.button.click", 0.6f, 1.2f) : next;
            error = error == null ? new SoundSetting("block.note_block.bass", 1.0f, 0.8f) : error;
            finish = finish == null ? new SoundSetting("entity.player.levelup", 1.0f, 1.0f) : finish;
        }
    }

    /** Where the report command uploads, and which page shows the result. */
    @ConfigSerializable
    public record Publishing(
            @Setting("paste-url") String pasteUrl,
            @Setting("viewer-url") String viewerUrl,
            Duration timeout
    ) {
        public Publishing {
            pasteUrl = pasteUrl == null || pasteUrl.isBlank() ? "https://bytebin.lucko.me" : pasteUrl;
            viewerUrl = viewerUrl == null || viewerUrl.isBlank()
                    ? "https://mccitiesnetwork.github.io/pollster" : viewerUrl;
            // A zero timeout would fail every upload instantly.
            timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                    ? Duration.ofSeconds(10) : timeout;
        }
    }
}
