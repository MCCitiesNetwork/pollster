package com.minecraftcitiesnetwork.pollster.api.model;

/** An action run once, on the main thread, after a response is saved. */
public sealed interface Reward
        permits Reward.CommandReward,
                Reward.PlayerCommandReward,
                Reward.MessageReward,
                Reward.TitleReward,
                Reward.BroadcastReward {

    /** Runs as the console. */
    record CommandReward(String command) implements Reward {
    }

    /** Runs as the player. */
    record PlayerCommandReward(String command) implements Reward {
    }

    /** Sends a MiniMessage line to the player. */
    record MessageReward(String miniMessage) implements Reward {
    }

    /** Shows a title to the player. */
    record TitleReward(String title, String subtitle) implements Reward {
    }

    /** Sends a MiniMessage line to everyone. */
    record BroadcastReward(String miniMessage) implements Reward {
    }
}
