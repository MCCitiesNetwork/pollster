package com.minecraftcitiesnetwork.pollster.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * A sound to play. An empty {@code key} disables it.
 *
 * @param key    namespaced or plain sound key, e.g. {@code ui.button.click}
 * @param volume playback volume, never negative
 * @param pitch  playback pitch, always positive
 */
@ConfigSerializable
public record SoundSetting(String key, float volume, float pitch) {

    public SoundSetting {
        key = key == null ? "" : key.strip();
        if (volume < 0f) {
            volume = 1.0f;
        }
        if (pitch <= 0f) {
            pitch = 1.0f;
        }
    }

    public boolean isEnabled() {
        return !this.key.isEmpty();
    }
}
