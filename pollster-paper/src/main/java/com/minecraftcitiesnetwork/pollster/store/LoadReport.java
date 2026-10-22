package com.minecraftcitiesnetwork.pollster.store;

import java.util.Map;

/**
 * Outcome of loading the surveys directory.
 *
 * @param loaded   how many surveys are now available
 * @param failures file name to the reason the file was skipped
 */
public record LoadReport(int loaded, Map<String, String> failures) {

    public LoadReport {
        failures = Map.copyOf(failures);
    }
}
