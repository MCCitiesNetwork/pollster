package com.minecraftcitiesnetwork.pollster.rewards;

import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/** Runs a survey's rewards for a player. Call on the main thread, once, after the response is saved. */
public final class RewardRunner {

    private final Server server;
    private final Logger log;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public RewardRunner(Server server, Logger log) {
        this.server = server;
        this.log = log;
    }

    public void run(Player player, SurveyDefinition survey) {
        for (Reward reward : survey.rewards()) {
            // One failing reward must not stop the ones after it.
            try {
                apply(reward, player, survey);
            } catch (RuntimeException e) {
                this.log.log(Level.SEVERE, "Reward " + reward + " failed for survey '"
                        + survey.id() + "' and player " + player.getName(), e);
            }
        }
    }

    private void apply(Reward reward, Player player, SurveyDefinition survey) {
        switch (reward) {
            case Reward.CommandReward r ->
                    this.server.dispatchCommand(this.server.getConsoleSender(), plain(r.command(), player, survey));
            case Reward.PlayerCommandReward r ->
                    this.server.dispatchCommand(player, plain(r.command(), player, survey));
            case Reward.MessageReward r -> player.sendMessage(parse(r.miniMessage(), player, survey));
            case Reward.BroadcastReward r -> this.server.broadcast(parse(r.miniMessage(), player, survey));
            case Reward.TitleReward r -> player.showTitle(Title.title(
                    parse(r.title(), player, survey), parse(r.subtitle(), player, survey)));
        }
    }

    private static String plain(String template, Player player, SurveyDefinition survey) {
        return template.replace("{player}", player.getName()).replace("{survey}", survey.name());
    }

    // Names go in as unparsed placeholders so "<red>" in a survey name shows literally.
    private Component parse(String template, Player player, SurveyDefinition survey) {
        String rewritten = template.replace("{player}", "<player>").replace("{survey}", "<survey>");
        return this.miniMessage.deserialize(rewritten, TagResolver.resolver(
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("survey", survey.name())));
    }
}
