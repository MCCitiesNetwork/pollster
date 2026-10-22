package com.minecraftcitiesnetwork.pollster.api.hash;

import com.minecraftcitiesnetwork.pollster.api.condition.Condition;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParseException;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParser;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * Fingerprint of everything that changes what a respondent sees or how answers are read. Name,
 * description, rewards and the like are left out on purpose: they may change after opening.
 * {@code anonymous} is in, because it is DRAFT-only and changing it later would break the promise
 * made to respondents.
 */
public final class ContentHash {

    private ContentHash() {
    }

    public static String of(SurveyDefinition survey) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, Boolean.toString(survey.anonymous()));
        for (Question question : survey.questions()) {
            field(canonical, question.key());
            field(canonical, question.type().name());
            field(canonical, question.text());
            field(canonical, Boolean.toString(question.required()));
            field(canonical, Integer.toString(question.min()));
            field(canonical, Integer.toString(question.max()));
            field(canonical, canonicalCondition(question.showIf()));
            field(canonical, Integer.toString(question.options().size()));
            for (Option option : question.options()) {
                field(canonical, option.key());
                field(canonical, option.label());
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime must provide SHA-256.
            throw new IllegalStateException(e);
        }
    }

    // The length prefix keeps "ab","c" distinct from "a","bc".
    private static void field(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value).append(';');
    }

    private static String canonicalCondition(String showIf) {
        if (showIf == null) {
            return "-";
        }
        try {
            return "+" + canonical(ConditionParser.parse(showIf));
        } catch (ConditionParseException e) {
            // An unparsable condition cannot be opened anyway; fall back to the raw text.
            return "?" + showIf;
        }
    }

    // Written by hand, not via toString, because the records carry source columns that whitespace changes.
    private static String canonical(Condition c) {
        return switch (c) {
            case Condition.And and -> "and(" + canonical(and.l()) + "," + canonical(and.r()) + ")";
            case Condition.Or or -> "or(" + canonical(or.l()) + "," + canonical(or.r()) + ")";
            case Condition.Not not -> "not(" + canonical(not.c()) + ")";
            case Condition.Equals eq -> "eq(" + quote(eq.question()) + "," + quote(eq.value()) + ")";
            case Condition.NotEquals ne -> "ne(" + quote(ne.question()) + "," + quote(ne.value()) + ")";
            case Condition.In in -> "in(" + quote(in.question()) + ",["
                    + in.values().stream().map(ContentHash::quote).collect(Collectors.joining(",")) + "])";
            case Condition.Has has -> "has(" + quote(has.question()) + "," + quote(has.value()) + ")";
            case Condition.Answered answered -> "answered(" + quote(answered.question()) + ")";
            case Condition.HasPermission perm -> "perm(" + quote(perm.node()) + ")";
        };
    }

    private static String quote(String value) {
        return value.length() + ":" + value;
    }
}
