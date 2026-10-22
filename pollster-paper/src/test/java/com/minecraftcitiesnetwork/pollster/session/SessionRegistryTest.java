package com.minecraftcitiesnetwork.pollster.session;

import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRegistryTest {

    private static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");
    private final SessionRegistry registry = new SessionRegistry(Clock.fixed(T0, ZoneOffset.UTC));

    private static SurveyFlow flow(String id) {
        Question q = new Question("q", QuestionType.YES_NO, "Q", List.of(new Option("yes", "Yes")), true, 0, 0, null);
        return new SurveyFlow(new SurveyDefinition(id, "N", "", SurveyStatus.OPEN, true, false, false,
                List.of(), List.of(q)), p -> false, T0);
    }

    @Test
    void start_replacesExistingAndIssuesNewToken() {
        UUID p = UUID.randomUUID();
        PlayerSession first = this.registry.start(p, flow("one-aaa"), false);
        PlayerSession second = this.registry.start(p, flow("two-aaa"), false);
        assertNotEquals(first.token(), second.token());
        assertFalse(this.registry.isCurrent(p, first.token()));
        assertTrue(this.registry.isCurrent(p, second.token()));
        assertEquals(1, this.registry.all().size());
    }

    @Test
    void touch_updatesActivityAndKeepsToken() {
        UUID p = UUID.randomUUID();
        PlayerSession session = this.registry.start(p, flow("one-aaa"), false);
        Instant later = T0.plusSeconds(30);
        this.registry.touch(p, later);
        PlayerSession touched = this.registry.get(p).orElseThrow();
        assertEquals(later, touched.lastActivity());
        assertEquals(session.token(), touched.token());
        assertSame(session.submitting(), touched.submitting());
    }

    @Test
    void end_removesSession() {
        UUID p = UUID.randomUUID();
        PlayerSession session = this.registry.start(p, flow("one-aaa"), false);
        this.registry.end(p);
        assertTrue(this.registry.get(p).isEmpty());
        assertFalse(this.registry.isCurrent(p, session.token()));
    }

    @Test
    void endAllFor_onlyEndsThatSurvey() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        this.registry.start(a, flow("one-aaa"), false);
        this.registry.start(b, flow("two-aaa"), false);
        this.registry.endAllFor("one-aaa");
        assertTrue(this.registry.get(a).isEmpty());
        assertTrue(this.registry.get(b).isPresent());
    }
}
