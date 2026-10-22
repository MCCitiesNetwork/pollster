package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.config.SoundSetting;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Plays the configured sound for each moment in the survey flow. */
public final class SoundPlayer {

    private final Settings.Sounds sounds;

    public SoundPlayer(@NotNull Settings.Sounds sounds) {
        this.sounds = sounds;
    }

    public void open(@NotNull Player player) {
        play(player, this.sounds.open());
    }

    public void next(@NotNull Player player) {
        play(player, this.sounds.next());
    }

    public void error(@NotNull Player player) {
        play(player, this.sounds.error());
    }

    public void finish(@NotNull Player player) {
        play(player, this.sounds.finish());
    }

    private static void play(Player player, SoundSetting setting) {
        if (!setting.isEnabled()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(setting.key()), Sound.Source.UI, setting.volume(), setting.pitch()));
        } catch (InvalidKeyException ignored) {
            // A mistyped key in settings.yml must not break the survey flow.
        }
    }
}
