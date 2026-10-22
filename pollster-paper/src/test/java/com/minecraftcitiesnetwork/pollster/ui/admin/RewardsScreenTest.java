package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.button;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.hasButton;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsScreenTest {

    @TempDir
    Path dir;

    private SurveyDefinition withRewards(AdminFixture f, SurveyStatus status) {
        SurveyDefinition s = f.survey("poll-one", status, AdminFixture.single("pick", "a", "b"))
                .withRewards(List.of(new Reward.CommandReward("eco give {player} 5"),
                        new Reward.MessageReward("<green>Thanks")));
        f.save(s);
        return s;
    }

    @Test
    void rewards_listsEachWithRemove() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = RewardsScreen.create(f.ui, withRewards(f, SurveyStatus.DRAFT));
        assertEquals(2, s.buttons().stream().filter(b -> plain(b.label()).contains("Remove")).count());
        assertTrue(plain(s).contains("eco give {player} 5"), plain(s));
        assertTrue(plain(s).contains("<green>Thanks"), plain(s));
    }

    @Test
    void rewards_removeDropsThatReward() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = RewardsScreen.create(f.ui, withRewards(f, SurveyStatus.DRAFT));
        AdminFixture.FakeContext ctx = f.context();
        button(s, "Remove 1").onClick().accept(ctx);
        List<Reward> left = f.store.get("poll-one").orElseThrow().rewards();
        assertEquals(List.of(new Reward.MessageReward("<green>Thanks")), left);
    }

    @Test
    void rewards_addTitleSplitsOnPipe() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = withRewards(f, SurveyStatus.OPEN);
        AdminFixture.FakeContext ctx = f.context().text("type", "title").text("value", "Thank you | for voting");
        button(RewardsScreen.add(f.ui, s), "Add").onClick().accept(ctx);
        List<Reward> rewards = f.store.get("poll-one").orElseThrow().rewards();
        assertEquals(new Reward.TitleReward("Thank you", "for voting"), rewards.get(2));
    }

    @Test
    void rewards_addCommandKeepsValueLiteral() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = withRewards(f, SurveyStatus.DRAFT);
        AdminFixture.FakeContext ctx = f.context().text("type", "player-command").text("value", "spawn");
        button(RewardsScreen.add(f.ui, s), "Add").onClick().accept(ctx);
        assertEquals(new Reward.PlayerCommandReward("spawn"),
                f.store.get("poll-one").orElseThrow().rewards().get(2));
    }

    @Test
    void rewards_editableWhileOpen() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = RewardsScreen.create(f.ui, withRewards(f, SurveyStatus.OPEN));
        assertTrue(hasButton(s, "Add reward"));
        assertTrue(hasButton(s, "Remove 1"));
    }

    @Test
    void rewards_readOnlyWhenClosed() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = RewardsScreen.create(f.ui, withRewards(f, SurveyStatus.CLOSED));
        assertTrue(!hasButton(s, "Add reward") && !hasButton(s, "Remove"));
        assertTrue(hasButton(s, "Back"));
        assertTrue(plain(s).contains("eco give {player} 5"));
    }
}
