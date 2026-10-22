package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeaturedPickerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final Duration COOLDOWN = Duration.ofHours(24);

    private static SurveyDefinition survey(String id, boolean featured) {
        return new SurveyDefinition(id, id, "", SurveyStatus.OPEN, true, featured, false, List.of(), List.of());
    }

    @Test
    void pick_skipsCompleted() {
        Optional<SurveyDefinition> pick = FeaturedPicker.pick(
                List.of(survey("done-one", true), survey("todo-one", true)), Set.of("done-one"),
                Map.of("done-one", NOW.minusSeconds(100), "todo-one", NOW.minusSeconds(10)), null, COOLDOWN, NOW);
        assertEquals("todo-one", pick.orElseThrow().id());
    }

    @Test
    void pick_respectsCooldown() {
        List<SurveyDefinition> visible = List.of(survey("one-aaa", true));
        assertTrue(FeaturedPicker.pick(visible, Set.of(), Map.of(), NOW.minus(Duration.ofHours(1)), COOLDOWN, NOW)
                .isEmpty());
        assertTrue(FeaturedPicker.pick(visible, Set.of(), Map.of(), NOW.minus(Duration.ofHours(25)), COOLDOWN, NOW)
                .isPresent());
    }

    @Test
    void pick_prefersOldestOpened() {
        Optional<SurveyDefinition> pick = FeaturedPicker.pick(
                List.of(survey("newer-one", true), survey("older-one", true)), Set.of(),
                Map.of("newer-one", NOW.minusSeconds(10), "older-one", NOW.minusSeconds(500)),
                null, COOLDOWN, NOW);
        assertEquals("older-one", pick.orElseThrow().id());
    }

    @Test
    void pick_noneWhenNothingFeatured() {
        assertTrue(FeaturedPicker.pick(List.of(survey("plain-one", false)), Set.of(), Map.of(), null, COOLDOWN, NOW)
                .isEmpty());
    }
}
