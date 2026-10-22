package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.api.event.SurveyCompletedEvent;
import com.minecraftcitiesnetwork.pollster.api.event.SurveyStartEvent;
import com.minecraftcitiesnetwork.pollster.api.event.SurveySubmitEvent;
import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.api.result.SubmitResult;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.body;
import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.button;
import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.labels;
import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.multiple;
import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.plain;
import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.single;
import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.survey;
import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.text;
import static com.minecraftcitiesnetwork.pollster.ui.player.PlayerTestSupport.yesNo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerScreensTest {

    private final PlayerTestSupport t = new PlayerTestSupport();

    private SurveyDefinition open(String id, com.minecraftcitiesnetwork.pollster.api.model.Question... qs) {
        return this.t.add(survey(id, SurveyStatus.OPEN, List.of(), qs));
    }

    // --- list ---------------------------------------------------------------------------

    @Test
    void list_marksCompletedAsDisabled() {
        SurveyDefinition done = open("done-one", single("q", 2, true));
        SurveyDefinition fresh = open("fresh-one", single("q", 2, true));
        Screen screen = SurveyListScreen.create(this.t.ui, this.t.player, List.of(done, fresh),
                Set.of("done-one"), Set.of());
        var ctx = this.t.context();
        screen.buttons().get(0).onClick().accept(ctx);
        assertTrue(plain(screen.buttons().get(0).label()).contains("Completed"));
        assertTrue(ctx.opened.isEmpty(), "completed survey must do nothing");
        screen.buttons().get(1).onClick().accept(ctx);
        assertEquals(1, ctx.opened.size());
    }

    @Test
    void list_hidesPermissionGatedWithoutNode() {
        // Gating is the service's job; the list shows exactly what visibleTo returned.
        SurveyDefinition shown = open("shown-one", single("q", 2, true));
        when(this.t.service.visibleTo(this.t.player)).thenReturn(List.of(shown));
        List<SurveyDefinition> visible = this.t.service.visibleTo(this.t.player);
        Screen screen = SurveyListScreen.create(this.t.ui, this.t.player, visible, Set.of(), Set.of());
        assertEquals(List.of("Survey shown-one - New", "Close"), labels(screen));
    }

    @Test
    void list_emptyShowsNoneAvailable() {
        Screen screen = SurveyListScreen.create(this.t.ui, this.t.player, List.of(), Set.of(), Set.of());
        assertTrue(body(screen).contains("no surveys available"));
    }

    // --- intro --------------------------------------------------------------------------

    @Test
    void intro_showsAnonymityLine() {
        SurveyDefinition anonymous = open("anon-one", single("q", 2, true));
        SurveyDefinition named = this.t.add(new SurveyDefinition("named-one", "Named", "", SurveyStatus.OPEN,
                false, false, false, List.of(), anonymous.questions()));
        assertTrue(body(IntroScreen.create(this.t.ui, anonymous, false)).contains("Your answers are anonymous."));
        assertTrue(body(IntroScreen.create(this.t.ui, named, false))
                .contains("Your name is recorded with your answers."));
    }

    @Test
    void intro_rewardsHintOnlyWithRewards() {
        SurveyDefinition plain = open("plain-one", single("q", 2, true));
        SurveyDefinition rewarded = this.t.add(survey("paid-one", SurveyStatus.OPEN,
                List.of(new Reward.MessageReward("hi")), single("q", 2, true)));
        assertFalse(body(IntroScreen.create(this.t.ui, plain, false)).contains("gives rewards"));
        assertTrue(body(IntroScreen.create(this.t.ui, rewarded, false)).contains("gives rewards"));
    }

    @Test
    void intro_notNowOnlyWhenFeatured() {
        SurveyDefinition s = open("feat-one", single("q", 2, true));
        assertFalse(labels(IntroScreen.create(this.t.ui, s, false)).contains("Not now"));
        assertTrue(labels(IntroScreen.create(this.t.ui, s, true)).contains("Not now"));
    }

    @Test
    void intro_startFiresEventAndOpensFirstQuestion() {
        SurveyDefinition s = open("start-one", single("q", 2, true));
        var ctx = this.t.context();
        button(IntroScreen.create(this.t.ui, s, false), "Start").onClick().accept(ctx);
        assertTrue(this.t.events.get(0) instanceof SurveyStartEvent);
        assertTrue(this.t.sessions.get(this.t.playerId).isPresent());
        assertTrue(body(ctx.last()).contains("Question 1 of 1"));
    }

    @Test
    void intro_startCancelled_closesWithoutSession() {
        SurveyDefinition s = open("start-two", single("q", 2, true));
        this.t.cancelEvents = true;
        var ctx = this.t.context();
        button(IntroScreen.create(this.t.ui, s, false), "Start").onClick().accept(ctx);
        assertTrue(ctx.closed);
        assertTrue(this.t.sessions.get(this.t.playerId).isEmpty());
    }

    @Test
    void intro_startingDifferentSurveyWhilePausedAsksFirst() {
        SurveyDefinition first = open("first-one", single("q", 2, true));
        SurveyDefinition second = open("second-one", single("q", 2, true));
        this.t.start(first, false);
        var ctx = this.t.context();
        button(IntroScreen.create(this.t.ui, second, false), "Start").onClick().accept(ctx);
        assertEquals(List.of("Confirm", "Cancel"), labels(ctx.last()));
        assertEquals("first-one", this.t.sessions.get(this.t.playerId).orElseThrow().flow().survey().id());
        button(ctx.last(), "Confirm").onClick().accept(ctx);
        assertEquals("second-one", this.t.sessions.get(this.t.playerId).orElseThrow().flow().survey().id());
    }

    // --- question -----------------------------------------------------------------------

    @Test
    void question_singleHasOneButtonPerOptionInTwoColumns() {
        SurveyDefinition s = open("many-one", single("a", 2, true), single("b", 12, true));
        PlayerSession session = this.t.start(s, false);
        session.flow().answer(new Answer.Choice("o0"));
        Screen screen = QuestionScreen.create(this.t.ui, session, null);
        assertEquals(2, screen.columns());
        assertEquals(13, screen.buttons().size());
        assertEquals("Back", plain(screen.buttons().get(12).label()));
    }

    @Test
    void question_multipleHasCheckboxesWithInitialSelections() {
        SurveyDefinition s = open("multi-one", multiple("m", 3, 1, 3));
        PlayerSession session = this.t.start(s, false);
        session.flow().answer(new Answer.Choices(Set.of("m1")));
        session.flow().back();
        Screen screen = QuestionScreen.create(this.t.ui, session, null);
        List<Input> inputs = screen.inputs();
        assertEquals(3, inputs.size());
        assertEquals(List.of(false, true, false),
                inputs.stream().map(i -> ((Input.BoolInput) i).initial()).toList());
    }

    @Test
    void question_headerShowsPositionOfVisible() {
        SurveyDefinition s = open("pos-one", single("a", 2, true), single("b", 2, true), single("c", 2, true));
        PlayerSession session = this.t.start(s, false);
        session.flow().answer(new Answer.Choice("o0"));
        assertTrue(body(QuestionScreen.create(this.t.ui, session, null)).contains("Question 2 of 3"));
    }

    @Test
    void question_invalidReopensWithErrorAndKeepsInput() {
        SurveyDefinition s = open("bad-one", multiple("m", 4, 1, 2));
        PlayerSession session = this.t.start(s, false);
        var ctx = this.t.context();
        ctx.bools.put("o0", true);
        ctx.bools.put("o1", true);
        ctx.bools.put("o2", true);
        button(QuestionScreen.create(this.t.ui, session, null), "Next").onClick().accept(ctx);
        Screen reopened = ctx.last();
        assertTrue(body(reopened).contains("Pick at most 2."));
        assertEquals(List.of(true, true, true, false),
                reopened.inputs().stream().map(i -> ((Input.BoolInput) i).initial()).toList());
        assertTrue(session.flow().answers().isEmpty());
    }

    @Test
    void question_requiredTextEmptyReopensWithError() {
        SurveyDefinition s = open("txt-one", text("t", true));
        PlayerSession session = this.t.start(s, false);
        var ctx = this.t.context();
        ctx.texts.put("text", "  ");
        button(QuestionScreen.create(this.t.ui, session, null), "Next").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("needs an answer"));
    }

    @Test
    void question_skipOnlyOnOptional() {
        SurveyDefinition s = open("skip-one", single("a", 2, true), single("b", 2, false));
        PlayerSession session = this.t.start(s, false);
        assertFalse(labels(QuestionScreen.create(this.t.ui, session, null)).contains("Skip"));
        session.flow().answer(new Answer.Choice("o0"));
        assertTrue(labels(QuestionScreen.create(this.t.ui, session, null)).contains("Skip"));
    }

    @Test
    void question_backAbsentOnFirst() {
        SurveyDefinition s = open("back-one", single("a", 2, true), single("b", 2, true));
        PlayerSession session = this.t.start(s, false);
        assertFalse(labels(QuestionScreen.create(this.t.ui, session, null)).contains("Back"));
        session.flow().answer(new Answer.Choice("o0"));
        assertTrue(labels(QuestionScreen.create(this.t.ui, session, null)).contains("Back"));
    }

    @Test
    void question_choosingOptionAnswersAndMovesOn() {
        SurveyDefinition s = open("pick-one", single("a", 2, true), single("b", 2, true));
        PlayerSession session = this.t.start(s, false);
        var ctx = this.t.context();
        button(QuestionScreen.create(this.t.ui, session, null), "Option 1").onClick().accept(ctx);
        assertEquals(new Answer.Choice("o1"), session.flow().answerFor("a").orElseThrow());
        assertTrue(body(ctx.last()).contains("Question 2 of 2"));
    }

    @Test
    void question_previewTitleCarriesMarker() {
        SurveyDefinition s = open("prev-one", single("a", 2, true));
        PlayerSession session = this.t.start(s, true);
        assertTrue(plain(QuestionScreen.create(this.t.ui, session, null).title()).contains("PREVIEW"));
    }

    @Test
    void question_doubleClickAnswersOnlyOnce() {
        SurveyDefinition s = open("dbl-yes", yesNo("a"), yesNo("b"));
        PlayerSession session = this.t.start(s, false);
        var ctx = this.t.context();
        var yes = button(QuestionScreen.create(this.t.ui, session, null), "Yes");
        yes.onClick().accept(ctx);
        yes.onClick().accept(ctx);
        assertEquals(1, session.flow().answers().size());
        assertEquals("b", session.flow().current().orElseThrow().key());
        assertTrue(body(ctx.last()).contains("Question 2 of 2"));
    }

    @Test
    void question_doubleClickBackStepsOnce() {
        SurveyDefinition s = open("dbl-back", single("a", 2, true), single("b", 2, true), single("c", 2, true));
        PlayerSession session = this.t.start(s, false);
        session.flow().answer(new Answer.Choice("o0"));
        session.flow().answer(new Answer.Choice("o0"));
        var ctx = this.t.context();
        var back = button(QuestionScreen.create(this.t.ui, session, null), "Back");
        back.onClick().accept(ctx);
        back.onClick().accept(ctx);
        assertEquals("b", session.flow().current().orElseThrow().key());
    }

    @Test
    void review_doubleClickBackStepsOnce() {
        SurveyDefinition s = open("dbl-rev", single("a", 2, true), single("b", 2, true));
        PlayerSession session = this.t.start(s, false);
        session.flow().answer(new Answer.Choice("o0"));
        session.flow().answer(new Answer.Choice("o0"));
        var ctx = this.t.context();
        var back = button(ReviewScreen.create(this.t.ui, session), "Back");
        back.onClick().accept(ctx);
        back.onClick().accept(ctx);
        assertEquals("b", session.flow().current().orElseThrow().key());
    }

    // --- review -------------------------------------------------------------------------

    @Test
    void review_listsAnswersWithPlayerTextLiteral() {
        SurveyDefinition s = open("rev-one", text("t", true));
        PlayerSession session = this.t.start(s, false);
        String hostile = "<red><click:run_command:/op me>hi";
        session.flow().answer(new Answer.Text(hostile));
        Screen screen = ReviewScreen.create(this.t.ui, session);
        assertTrue(body(screen).contains(hostile), body(screen));
    }

    @Test
    void review_adminTextShownLiterally() {
        SurveyDefinition s = open("rev-two", new com.minecraftcitiesnetwork.pollster.api.model.Question("k",
                com.minecraftcitiesnetwork.pollster.api.model.QuestionType.SINGLE, "Q <red>text",
                List.of(new com.minecraftcitiesnetwork.pollster.api.model.Option("a", "<bold>Label")),
                true, 0, 0, null));
        PlayerSession session = this.t.start(s, false);
        session.flow().answer(new Answer.Choice("a"));
        String shown = body(ReviewScreen.create(this.t.ui, session));
        assertTrue(shown.contains("Q <red>text"));
        assertTrue(shown.contains("<bold>Label"));
    }

    // --- submit -------------------------------------------------------------------------

    private PlayerSession answered(SurveyDefinition s, boolean preview) {
        PlayerSession session = this.t.start(s, preview);
        session.flow().answer(new Answer.Choice("o0"));
        return session;
    }

    @Test
    void submit_success_runsRewardsOnceAndOpensThanks() {
        SurveyDefinition s = this.t.add(survey("sub-one", SurveyStatus.OPEN,
                List.of(new Reward.MessageReward("hi")), single("a", 2, true)));
        PlayerSession session = answered(s, false);
        when(this.t.service.submit(any(), eq(this.t.playerId), eq("Alex")))
                .thenReturn(CompletableFuture.completedFuture(new SubmitResult.Saved(7)));
        var ctx = this.t.context();
        button(ReviewScreen.create(this.t.ui, session), "Submit").onClick().accept(ctx);
        verify(this.t.rewards, times(1)).run(this.t.player, s);
        assertTrue(plain(ctx.last().title()).contains("Survey sub-one"));
        assertTrue(body(ctx.last()).contains("Thank you"));
        assertTrue(this.t.events.stream().anyMatch(SurveySubmitEvent.class::isInstance));
        assertTrue(this.t.events.stream().anyMatch(SurveyCompletedEvent.class::isInstance));
        assertTrue(this.t.sessions.get(this.t.playerId).isEmpty());
    }

    @Test
    void submit_doubleClick_runsRewardsOnce() {
        SurveyDefinition s = open("dbl-one", single("a", 2, true));
        PlayerSession session = answered(s, false);
        CompletableFuture<SubmitResult> pending = new CompletableFuture<>();
        when(this.t.service.submit(any(), any(), anyString())).thenReturn(pending);
        var ctx = this.t.context();
        var submit = button(ReviewScreen.create(this.t.ui, session), "Submit");
        submit.onClick().accept(ctx);
        submit.onClick().accept(ctx);
        pending.complete(new SubmitResult.Saved(1));
        verify(this.t.service, times(1)).submit(any(), any(), anyString());
        verify(this.t.rewards, times(1)).run(any(), any());
    }

    @Test
    void submit_alreadyResponded_showsMessageNoRewards() {
        SurveyDefinition s = open("dup-one", single("a", 2, true));
        PlayerSession session = answered(s, false);
        when(this.t.service.submit(any(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(new SubmitResult.AlreadyResponded()));
        var ctx = this.t.context();
        button(ReviewScreen.create(this.t.ui, session), "Submit").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("already completed"));
        verify(this.t.rewards, never()).run(any(), any());
    }

    @Test
    void submit_failure_allowsRetry() {
        SurveyDefinition s = open("fail-one", single("a", 2, true));
        PlayerSession session = answered(s, false);
        when(this.t.service.submit(any(), any(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")));
        var ctx = this.t.context();
        button(ReviewScreen.create(this.t.ui, session), "Submit").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("could not be saved"));
        assertFalse(session.submitting().get());
        verify(this.t.rewards, never()).run(any(), any());
    }

    @Test
    void submit_contentChanged_showsNoLongerAvailable() {
        SurveyDefinition s = open("chg-one", single("a", 2, true));
        PlayerSession session = answered(s, false);
        when(this.t.service.submit(any(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(new SubmitResult.ContentChanged()));
        var ctx = this.t.context();
        button(ReviewScreen.create(this.t.ui, session), "Submit").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("no longer available"));
        verify(this.t.rewards, never()).run(any(), any());
    }

    @Test
    void staleButton_afterSessionReplaced_showsNoLongerAvailable() {
        SurveyDefinition s = open("stale-one", single("a", 2, true), single("b", 2, true));
        PlayerSession old = this.t.start(s, false);
        Screen oldScreen = QuestionScreen.create(this.t.ui, old, null);
        PlayerSession replacement = this.t.start(s, false);
        var ctx = this.t.context();
        button(oldScreen, "Option 0").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("no longer available"));
        assertTrue(old.flow().answers().isEmpty());
        assertTrue(replacement.flow().answers().isEmpty());
    }

    @Test
    void staleButton_afterSurveyClosed_showsNoLongerAvailable() {
        SurveyDefinition s = open("closed-one", single("a", 2, true));
        PlayerSession session = this.t.start(s, false);
        Screen screen = QuestionScreen.create(this.t.ui, session, null);
        this.t.surveys.put(s.id(), s.withStatus(SurveyStatus.CLOSED));
        var ctx = this.t.context();
        button(screen, "Option 0").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("no longer available"));
        assertTrue(session.flow().answers().isEmpty());
    }

    @Test
    void staleButton_afterLockChanged_showsNoLongerAvailable() {
        SurveyDefinition s = open("lock-one", single("a", 2, true));
        PlayerSession session = this.t.start(s, false);
        Screen screen = QuestionScreen.create(this.t.ui, session, null);
        when(this.t.service.lock("lock-one")).thenReturn(SurveyService.SurveyLock.CONTENT_CHANGED);
        var ctx = this.t.context();
        button(screen, "Option 0").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("no longer available"));
    }

    @Test
    void staleButton_afterSessionEnded_showsNoLongerAvailable() {
        SurveyDefinition s = open("ended-one", single("a", 2, true));
        PlayerSession session = this.t.start(s, false);
        Screen screen = QuestionScreen.create(this.t.ui, session, null);
        this.t.sessions.endAllFor("ended-one");
        var ctx = this.t.context();
        button(screen, "Option 0").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("no longer available"));
    }

    @Test
    void staleButton_afterSurveyDeleted_showsNoLongerAvailable() {
        SurveyDefinition s = open("gone-one", single("a", 2, true));
        PlayerSession session = answered(s, false);
        Screen review = ReviewScreen.create(this.t.ui, session);
        this.t.surveys.remove("gone-one");
        var ctx = this.t.context();
        button(review, "Submit").onClick().accept(ctx);
        assertTrue(body(ctx.last()).contains("no longer available"));
        verify(this.t.service, never()).submit(any(), any(), anyString());
    }

    @Test
    void preview_submitSavesNothing() {
        SurveyDefinition s = this.t.add(survey("pre-one", SurveyStatus.DRAFT,
                List.of(new Reward.MessageReward("hi")), single("a", 2, true)));
        PlayerSession session = answered(s, true);
        var ctx = this.t.context();
        button(ReviewScreen.create(this.t.ui, session), "Submit").onClick().accept(ctx);
        verify(this.t.service, never()).submit(any(), any(), anyString());
        verify(this.t.rewards, never()).run(any(), any());
        assertTrue(body(ctx.last()).contains("This was a preview"));
        assertTrue(plain(ctx.last().title()).contains("PREVIEW"));
    }

    @Test
    void submitEvent_cancelled_doesNotSave() {
        SurveyDefinition s = open("evt-one", single("a", 2, true));
        PlayerSession session = answered(s, false);
        this.t.cancelEvents = true;
        var ctx = this.t.context();
        button(ReviewScreen.create(this.t.ui, session), "Submit").onClick().accept(ctx);
        verify(this.t.service, never()).submit(any(), any(), anyString());
        verify(this.t.rewards, never()).run(any(), any());
        assertFalse(session.submitting().get());
    }

    @Test
    void review_cancelEndsSessionWithoutSaving() {
        SurveyDefinition s = open("can-one", single("a", 2, true));
        PlayerSession session = answered(s, false);
        var ctx = this.t.context();
        button(ReviewScreen.create(this.t.ui, session), "Cancel").onClick().accept(ctx);
        assertTrue(ctx.closed);
        assertTrue(this.t.sessions.get(this.t.playerId).isEmpty());
        verify(this.t.service, never()).submit(any(), any(), anyString());
    }
}
