package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The rewards run after a response is saved. Editable while the survey is a draft or open. */
public final class RewardsScreen {

    private static final String COMMAND = "command";
    private static final String PLAYER_COMMAND = "player-command";
    private static final String MESSAGE = "message";
    private static final String TITLE = "title";
    private static final String BROADCAST = "broadcast";
    private static final int VALUE_MAX = 300;

    private RewardsScreen() {
    }

    public static @NotNull Screen create(@NotNull AdminUi ui, @NotNull SurveyDefinition s) {
        boolean editable = s.status() != SurveyStatus.CLOSED;
        Screen.Builder b = Screen.builder(ui.style().title("Rewards: " + AdminSupport.truncate(s.name(), 30)));
        if (s.rewards().isEmpty()) {
            b.body(Component.text("No rewards yet.", NamedTextColor.GRAY));
        }
        List<Reward> rewards = s.rewards();
        for (int i = 0; i < rewards.size(); i++) {
            int index = i;
            // Reward text is shown as plain text so a MiniMessage line is not rendered in the editor.
            b.body(Component.text((i + 1) + ". [" + typeOf(rewards.get(i)) + "] " + summary(rewards.get(i)),
                    NamedTextColor.GRAY));
            if (editable) {
                b.button(AdminSupport.wide(ui.style().danger("Remove " + (i + 1)), ctx -> remove(ui, ctx, s.id(), index)));
            }
        }
        if (editable) {
            b.button(AdminSupport.wide(ui.style().primary("Add reward"), ctx -> ctx.open(add(ui, s))));
        } else {
            b.body(Component.text("The survey is closed, so rewards can no longer change.", NamedTextColor.GRAY));
        }
        b.button(AdminSupport.wide(ui.style().secondary("Back"), ctx -> HubScreen.open(ui, ctx, s.id())));
        return b.build();
    }

    public static @NotNull Screen add(@NotNull AdminUi ui, @NotNull SurveyDefinition s) {
        return add(ui, s, null);
    }

    private static Screen add(AdminUi ui, SurveyDefinition s, @Nullable Component error) {
        Screen.Builder b = Screen.builder(ui.style().title("Add reward"));
        b.body(Component.text("Commands may use {player}. For a title, write \"title | subtitle\".",
                NamedTextColor.GRAY));
        if (error != null) {
            b.body(error);
        }
        return b.input(new Input.ChoiceInput("type", Component.text("Type"), List.of(
                        Map.entry(COMMAND, Component.text("Console command")),
                        Map.entry(PLAYER_COMMAND, Component.text("Player command")),
                        Map.entry(MESSAGE, Component.text("Message to the player")),
                        Map.entry(TITLE, Component.text("Title to the player")),
                        Map.entry(BROADCAST, Component.text("Broadcast to everyone"))), COMMAND))
                .input(new Input.TextInput("value", Component.text("Value"), "", VALUE_MAX, false))
                .button(Button.of(ui.style().primary("Add"), ctx -> {
                    String value = ctx.text("value") == null ? "" : ctx.text("value").strip();
                    if (value.isEmpty()) {
                        ctx.open(add(ui, s, ui.style().error("Type a value.")));
                        return;
                    }
                    Reward reward = build(ctx.text("type"), value);
                    save(ui, ctx, s.id(), current -> {
                        List<Reward> rewards = new ArrayList<>(current.rewards());
                        rewards.add(reward);
                        return current.withRewards(rewards);
                    }, failure -> ctx.open(add(ui, s, ui.style().error(HubScreen.reason(failure)))));
                }))
                .button(Button.of(ui.style().secondary("Cancel"), ctx -> ctx.open(create(ui, s))))
                .columns(2)
                .build();
    }

    private static Reward build(@Nullable String type, String value) {
        return switch (type == null ? COMMAND : type) {
            case PLAYER_COMMAND -> new Reward.PlayerCommandReward(value);
            case MESSAGE -> new Reward.MessageReward(value);
            case BROADCAST -> new Reward.BroadcastReward(value);
            case TITLE -> {
                int bar = value.indexOf('|');
                yield bar < 0 ? new Reward.TitleReward(value, "")
                        : new Reward.TitleReward(value.substring(0, bar).strip(), value.substring(bar + 1).strip());
            }
            default -> new Reward.CommandReward(value);
        };
    }

    private static void remove(AdminUi ui, ScreenContext ctx, String id, int index) {
        save(ui, ctx, id, current -> {
            List<Reward> rewards = new ArrayList<>(current.rewards());
            if (index < rewards.size()) {
                rewards.remove(index);
            }
            return current.withRewards(rewards);
        }, error -> ctx.open(AdminSupport.failure(ui, error, c -> openFresh(ui, c, id))));
    }

    /** Applies the change to the survey as it is in the file now, then shows the updated rewards list. */
    private static void save(AdminUi ui, ScreenContext ctx, String id,
                             java.util.function.UnaryOperator<SurveyDefinition> change,
                             java.util.function.Consumer<Throwable> onFail) {
        var current = ui.service().survey(id);
        if (current.isEmpty()) {
            ctx.open(AdminSupport.unavailable(ui, c -> AdminListScreen.open(ui, c)));
            return;
        }
        if (current.get().status() == SurveyStatus.CLOSED) {
            ctx.open(AdminSupport.notice(ui, "Rewards",
                    ui.messages().messageFor("editor.read-only"), c -> openFresh(ui, c, id)));
            return;
        }
        ctx.open(AdminSupport.loading(ui));
        ui.service().save(change.apply(current.get())).whenComplete((ignored, error) -> {
            if (error != null) {
                onFail.accept(error);
                return;
            }
            openFresh(ui, ctx, id);
        });
    }

    private static void openFresh(AdminUi ui, ScreenContext ctx, String id) {
        ui.service().survey(id).ifPresentOrElse(fresh -> ctx.open(create(ui, fresh)),
                () -> ctx.open(AdminSupport.unavailable(ui, c -> AdminListScreen.open(ui, c))));
    }

    private static String typeOf(Reward reward) {
        return switch (reward) {
            case Reward.CommandReward r -> COMMAND;
            case Reward.PlayerCommandReward r -> PLAYER_COMMAND;
            case Reward.MessageReward r -> MESSAGE;
            case Reward.TitleReward r -> TITLE;
            case Reward.BroadcastReward r -> BROADCAST;
        };
    }

    private static String summary(Reward reward) {
        return switch (reward) {
            case Reward.CommandReward r -> r.command();
            case Reward.PlayerCommandReward r -> r.command();
            case Reward.MessageReward r -> r.miniMessage();
            case Reward.TitleReward r -> r.subtitle().isEmpty() ? r.title() : r.title() + " | " + r.subtitle();
            case Reward.BroadcastReward r -> r.miniMessage();
        };
    }
}
