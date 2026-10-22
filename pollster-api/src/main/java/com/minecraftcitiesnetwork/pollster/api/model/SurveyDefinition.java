package com.minecraftcitiesnetwork.pollster.api.model;

import java.util.List;
import java.util.Optional;

public record SurveyDefinition(
        String id,
        String name,
        String description,
        SurveyStatus status,
        boolean anonymous,
        boolean featured,
        boolean requiresPermission,
        List<Reward> rewards,
        List<Question> questions) {

    public SurveyDefinition {
        // Null lists survive construction so SurveyValidator can report them instead of the record throwing.
        rewards = rewards == null ? null : List.copyOf(rewards);
        questions = questions == null ? null : List.copyOf(questions);
    }

    public SurveyDefinition withStatus(SurveyStatus status) {
        return new SurveyDefinition(this.id, this.name, this.description, status, this.anonymous, this.featured,
                this.requiresPermission, this.rewards, this.questions);
    }

    public SurveyDefinition withQuestions(List<Question> questions) {
        return new SurveyDefinition(this.id, this.name, this.description, this.status, this.anonymous,
                this.featured, this.requiresPermission, this.rewards, questions);
    }

    public SurveyDefinition withRewards(List<Reward> rewards) {
        return new SurveyDefinition(this.id, this.name, this.description, this.status, this.anonymous,
                this.featured, this.requiresPermission, rewards, this.questions);
    }

    public Optional<Question> question(String key) {
        return this.questions.stream().filter(question -> question.key().equals(key)).findFirst();
    }
}
