package com.minecraftcitiesnetwork.pollster.api.report;

import java.util.Map;
import java.util.OptionalDouble;

/** Raw aggregates for one survey, as read from storage. */
public record ReportData(int participants, OptionalDouble averageSeconds, Map<String, QuestionTally> tallies) {

    public ReportData {
        tallies = Map.copyOf(tallies);
    }
}
