package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Chooses which featured survey, if any, to offer a player on join. */
public final class FeaturedPicker {

    private FeaturedPicker() {
    }

    /**
     * @param visible      surveys the player may take now
     * @param openedAt     when each survey was opened; a survey with no entry sorts last
     * @param lastFeatured when the player was last shown a featured survey
     */
    public static @NotNull Optional<SurveyDefinition> pick(@NotNull List<SurveyDefinition> visible,
                                                           @NotNull Set<String> completed,
                                                           @NotNull Map<String, Instant> openedAt,
                                                           @Nullable Instant lastFeatured,
                                                           @NotNull Duration cooldown, @NotNull Instant now) {
        if (lastFeatured != null && now.isBefore(lastFeatured.plus(cooldown))) {
            return Optional.empty();
        }
        return visible.stream()
                .filter(SurveyDefinition::featured)
                .filter(survey -> !completed.contains(survey.id()))
                .min(Comparator
                        .comparing((SurveyDefinition survey) -> openedAt.getOrDefault(survey.id(), Instant.MAX))
                        .thenComparing(SurveyDefinition::id));
    }
}
