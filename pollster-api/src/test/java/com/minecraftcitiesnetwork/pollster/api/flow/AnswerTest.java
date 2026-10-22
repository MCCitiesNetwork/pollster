package com.minecraftcitiesnetwork.pollster.api.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnswerTest {

    @Test
    void choices_preservesInsertionOrderAndIsImmutable() {
        Set<String> source = new LinkedHashSet<>(List.of("zeta", "alpha", "mid"));
        Answer.Choices choices = new Answer.Choices(source);
        source.add("late");

        assertEquals(List.of("zeta", "alpha", "mid"), List.copyOf(choices.optionKeys()));
        assertThrows(UnsupportedOperationException.class, () -> choices.optionKeys().add("x"));
    }
}
