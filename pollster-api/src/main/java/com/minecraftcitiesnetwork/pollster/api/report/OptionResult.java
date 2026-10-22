package com.minecraftcitiesnetwork.pollster.api.report;

/** @param percent share of the respondents the question was shown to, 0 to 100 */
public record OptionResult(String key, String label, int votes, double percent) {
}
