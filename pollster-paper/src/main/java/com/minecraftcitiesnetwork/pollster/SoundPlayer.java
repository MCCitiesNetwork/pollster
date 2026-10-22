package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.config.SoundSetting;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/** Plays the configured sound for each moment in the survey flow. */
public final class SoundPlayer {

    // Read through a supplier so /pollster reload applies new sounds without rebuilding the player UI.
    private final Supplier<Settings.Sounds> sounds;

    public SoundPlayer(@NotNull Settings.Sounds sounds) {
        this(() -> sounds);
    }

    public SoundPlayer(@NotNull Supplier<Settings.Sounds> sounds) {
        this.sounds = sounds;
    }

    public void open(@NotNull Player player) {
        play(player, this.sounds.get().open());
    }

    public void next(@NotNull Player player) {
        play(player, this.sounds.get().next());
    }

    public void error(@NotNull Player player) {
        play(player, this.sounds.get().error());
    }

    public void finish(@NotNull Player player) {
        play(player, this.sounds.get().finish());
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
