package com.minecraftcitiesnetwork.pollster.store;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds repeated keys in a block-style YAML mapping.
 *
 * <p>Configurate's YAML loader keeps the last of two equal keys without complaint, so a hand-edit that
 * repeats {@code status:} would silently change a survey. This line scan covers the block style the
 * plugin writes; flow-style maps ({@code { key: a }}) and block scalars are not inspected.
 */
final class DuplicateKeyScanner {

    private static final Pattern KEY_LINE =
            Pattern.compile("^( *)((?:- +)*)([A-Za-z0-9_.-]+|\"[^\"]*\"|'[^']*'):(?:\\s|$)");
    private static final Pattern BLOCK_SCALAR_START = Pattern.compile(":\\s*[|>][+-]?\\d*\\s*$");

    private DuplicateKeyScanner() {
    }

    /** Returns a description of the first duplicate key, if there is one. */
    static Optional<String> findDuplicate(List<String> lines) {
        record Scope(int column, Set<String> keys) {
        }
        Deque<Scope> scopes = new ArrayDeque<>();
        int skipAbove = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (skipAbove >= 0) {
                if (indent > skipAbove) {
                    continue;
                }
                skipAbove = -1;
            }
            Matcher m = KEY_LINE.matcher(line);
            if (!m.find()) {
                continue;
            }
            int dashes = m.group(2).isEmpty() ? 0 : m.group(2).length();
            int column = m.group(1).length() + dashes;
            // A list item opens a new mapping, so keys from the previous item must not carry over.
            if (dashes > 0) {
                while (!scopes.isEmpty() && scopes.peek().column() >= column) {
                    scopes.pop();
                }
            } else {
                while (!scopes.isEmpty() && scopes.peek().column() > column) {
                    scopes.pop();
                }
            }
            if (scopes.isEmpty() || scopes.peek().column() < column) {
                scopes.push(new Scope(column, new HashSet<>()));
            }
            String key = unquote(m.group(3));
            if (!scopes.peek().keys().add(key)) {
                return Optional.of("Duplicate key \"" + key + "\" on line " + (i + 1) + ".");
            }
            if (BLOCK_SCALAR_START.matcher(line).find()) {
                skipAbove = m.group(1).length();
            }
        }
        return Optional.empty();
    }

    private static String unquote(String key) {
        if (key.length() >= 2 && (key.startsWith("\"") || key.startsWith("'"))) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }
}
