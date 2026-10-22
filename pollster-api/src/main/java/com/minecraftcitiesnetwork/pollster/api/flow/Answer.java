package com.minecraftcitiesnetwork.pollster.api.flow;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** A player's answer to one question. */
public sealed interface Answer permits Answer.Choice, Answer.Choices, Answer.Text, Answer.Skipped {

    record Choice(String optionKey) implements Answer {
    }

    record Choices(Set<String> optionKeys) implements Answer {
        public Choices {
            // Insertion order is the order the player picked, which reports keep.
            optionKeys = Collections.unmodifiableSet(new LinkedHashSet<>(optionKeys));
        }
    }

    record Text(String value) implements Answer {
    }

    record Skipped() implements Answer {
    }
}
