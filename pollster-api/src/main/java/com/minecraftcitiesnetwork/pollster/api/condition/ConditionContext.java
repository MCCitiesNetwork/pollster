package com.minecraftcitiesnetwork.pollster.api.condition;

import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import java.util.Optional;

/** What a condition can look at while it is evaluated. */
public interface ConditionContext {

    /** The answer to a question, or empty when the question is hidden or not yet answered. */
    Optional<Answer> answer(String questionKey);

    boolean hasPermission(String node);
}
