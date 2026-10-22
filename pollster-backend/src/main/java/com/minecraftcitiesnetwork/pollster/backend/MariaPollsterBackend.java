package com.minecraftcitiesnetwork.pollster.backend;

import com.minecraftcitiesnetwork.pollster.api.report.QuestionTally;
import com.minecraftcitiesnetwork.pollster.api.report.ReportData;
import com.minecraftcitiesnetwork.pollster.api.report.TextAnswer;
import com.minecraftcitiesnetwork.pollster.api.result.OpenResult;
import com.minecraftcitiesnetwork.pollster.api.result.SubmitResult;
import com.minecraftcitiesnetwork.pollster.backend.mapper.AnswerMapper;
import com.minecraftcitiesnetwork.pollster.backend.mapper.PlayerStateMapper;
import com.minecraftcitiesnetwork.pollster.backend.mapper.ReportMapper;
import com.minecraftcitiesnetwork.pollster.backend.mapper.ResponseMapper;
import com.minecraftcitiesnetwork.pollster.backend.mapper.SurveyStateMapper;
import org.apache.ibatis.session.SqlSession;

import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

public final class MariaPollsterBackend implements PollsterBackend {

    private final PollsterDatabase database;

    public MariaPollsterBackend(PollsterDatabase database) {
        this.database = database;
    }

    @Override
    public OpenResult open(String surveyId, String contentHash, Instant now) {
        try (SqlSession session = this.database.openSession()) {
            int inserted = session.getMapper(SurveyStateMapper.class).insertIgnore(surveyId, contentHash, now);
            session.commit();
            return inserted == 0 ? new OpenResult.AlreadyOpened() : new OpenResult.Opened();
        }
    }

    @Override
    public void close(String surveyId, Instant now) {
        try (SqlSession session = this.database.openSession()) {
            session.getMapper(SurveyStateMapper.class).close(surveyId, now);
            session.commit();
        }
    }

    @Override
    public void reopen(String surveyId) {
        try (SqlSession session = this.database.openSession()) {
            session.getMapper(SurveyStateMapper.class).reopen(surveyId);
            session.commit();
        }
    }

    @Override
    public Optional<SurveyStateRecord> state(String surveyId) {
        try (SqlSession session = this.database.openSession()) {
            return Optional.ofNullable(session.getMapper(SurveyStateMapper.class).select(surveyId));
        }
    }

    @Override
    public SubmitResult submit(ResponseSubmission submission) {
        try (SqlSession session = this.database.openSession()) {
            // Locking the state row serializes submits for one survey, so the checks below cannot
            // race with a close or a second click.
            SurveyStateRecord state = session.getMapper(SurveyStateMapper.class)
                    .selectForUpdate(submission.surveyId());
            if (state == null || state.closedAt() != null) {
                session.rollback();
                return new SubmitResult.SurveyNotOpen();
            }
            if (!state.contentHash().equals(submission.contentHash())) {
                session.rollback();
                return new SubmitResult.ContentChanged();
            }
            ResponseMapper responses = session.getMapper(ResponseMapper.class);
            try {
                responses.insert(submission.surveyId(), submission.player(), submission.startedAt(),
                        submission.submittedAt());
            } catch (RuntimeException ex) {
                if (isDuplicateKey(ex)) {
                    session.rollback();
                    return new SubmitResult.AlreadyResponded();
                }
                throw ex;
            }
            long responseId = responses.lastInsertId();
            AnswerMapper answers = session.getMapper(AnswerMapper.class);
            for (AnswerRowInput row : submission.answers()) {
                answers.insert(responseId, row.questionKey(), row.optionKey(), row.text());
            }
            session.commit();
            return new SubmitResult.Saved(responseId);
        }
    }

    /** MyBatis may wrap the driver's exception, so the whole cause chain is searched. */
    private static boolean isDuplicateKey(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasResponded(String surveyId, UUID player) {
        try (SqlSession session = this.database.openSession()) {
            return session.getMapper(ResponseMapper.class).countFor(surveyId, player) > 0;
        }
    }

    @Override
    public Set<String> completedSurveys(UUID player) {
        try (SqlSession session = this.database.openSession()) {
            return new HashSet<>(session.getMapper(ResponseMapper.class).surveysCompletedBy(player));
        }
    }

    @Override
    public Map<String, Integer> responseCounts() {
        try (SqlSession session = this.database.openSession()) {
            Map<String, Integer> counts = new HashMap<>();
            for (Map<String, Object> row : session.getMapper(ResponseMapper.class).counts()) {
                counts.put((String) row.get("surveyId"), ((Number) row.get("total")).intValue());
            }
            return counts;
        }
    }

    @Override
    public void deleteSurveyData(String surveyId) {
        try (SqlSession session = this.database.openSession()) {
            session.getMapper(ResponseMapper.class).deleteBySurvey(surveyId);
            session.getMapper(SurveyStateMapper.class).delete(surveyId);
            session.commit();
        }
    }

    @Override
    public Optional<Instant> lastFeatured(UUID player) {
        try (SqlSession session = this.database.openSession()) {
            return Optional.ofNullable(session.getMapper(PlayerStateMapper.class).lastFeatured(player));
        }
    }

    @Override
    public ReportData reportData(String surveyId) {
        try (SqlSession session = this.database.openSession()) {
            ReportMapper mapper = session.getMapper(ReportMapper.class);
            Map<String, Object> summary = mapper.summary(surveyId);
            int participants = ((Number) summary.get("participants")).intValue();
            Object average = summary.get("averageSeconds");
            OptionalDouble averageSeconds = average == null
                    ? OptionalDouble.empty() : OptionalDouble.of(((Number) average).doubleValue());

            Map<String, int[]> counts = new LinkedHashMap<>();
            for (Map<String, Object> row : mapper.respondentCounts(surveyId)) {
                counts.put((String) row.get("questionKey"), new int[]{
                        ((Number) row.get("answered")).intValue(), ((Number) row.get("skipped")).intValue()});
            }
            Map<String, Map<String, Integer>> votes = new HashMap<>();
            for (Map<String, Object> row : mapper.optionVotes(surveyId)) {
                votes.computeIfAbsent((String) row.get("questionKey"), k -> new LinkedHashMap<>())
                        .put((String) row.get("optionKey"), ((Number) row.get("votes")).intValue());
            }
            Map<String, Map<Integer, Integer>> selections = new HashMap<>();
            for (Map<String, Object> row : mapper.selectionCounts(surveyId)) {
                selections.computeIfAbsent((String) row.get("questionKey"), k -> new HashMap<>())
                        .put(((Number) row.get("selections")).intValue(),
                                ((Number) row.get("respondents")).intValue());
            }
            Map<String, List<TextAnswer>> texts = new HashMap<>();
            for (Map<String, Object> row : mapper.texts(surveyId)) {
                texts.computeIfAbsent((String) row.get("questionKey"), k -> new ArrayList<>())
                        .add(new TextAnswer((String) row.get("textValue"), UuidBinaryTypeHandler.fromBytes((byte[]) row.get("player")),
                                ((Timestamp) row.get("submittedAt")).toInstant()));
            }

            Map<String, QuestionTally> tallies = new HashMap<>();
            counts.forEach((key, pair) -> tallies.put(key, new QuestionTally(pair[0], pair[1],
                    votes.getOrDefault(key, Map.of()), selections.getOrDefault(key, Map.of()),
                    texts.getOrDefault(key, List.of()))));
            return new ReportData(participants, averageSeconds, tallies);
        }
    }

    @Override
    public void markFeatured(UUID player, Instant now) {
        try (SqlSession session = this.database.openSession()) {
            session.getMapper(PlayerStateMapper.class).upsertFeatured(player, now);
            session.commit();
        }
    }
}
