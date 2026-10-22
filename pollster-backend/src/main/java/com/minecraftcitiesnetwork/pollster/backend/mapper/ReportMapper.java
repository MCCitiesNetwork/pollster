package com.minecraftcitiesnetwork.pollster.backend.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** Aggregate queries. Rows come back as maps keyed by the column aliases below. */
public interface ReportMapper {

    @Select("""
            SELECT COUNT(*) AS participants,
                   AVG(TIMESTAMPDIFF(SECOND, started_at, submitted_at)) AS averageSeconds
            FROM pollster_responses WHERE survey_id = #{surveyId}""")
    Map<String, Object> summary(@Param("surveyId") String surveyId);

    @Select("""
            SELECT a.question_key AS questionKey, a.option_key AS optionKey, COUNT(*) AS votes
            FROM pollster_answers a JOIN pollster_responses r ON r.id = a.response_id
            WHERE r.survey_id = #{surveyId} AND a.option_key IS NOT NULL
            GROUP BY a.question_key, a.option_key
            ORDER BY a.question_key, a.option_key""")
    List<Map<String, Object>> optionVotes(@Param("surveyId") String surveyId);

    /** Respondents per question that answered (a choice or text row) or skipped (a row with neither). */
    @Select("""
            SELECT a.question_key AS questionKey,
                   COUNT(DISTINCT CASE WHEN a.option_key IS NOT NULL OR a.text_value IS NOT NULL
                                       THEN a.response_id END) AS answered,
                   COUNT(DISTINCT CASE WHEN a.option_key IS NULL AND a.text_value IS NULL
                                       THEN a.response_id END) AS skipped
            FROM pollster_answers a JOIN pollster_responses r ON r.id = a.response_id
            WHERE r.survey_id = #{surveyId}
            GROUP BY a.question_key""")
    List<Map<String, Object>> respondentCounts(@Param("surveyId") String surveyId);

    @Select("""
            SELECT t.question_key AS questionKey, t.selections AS selections, COUNT(*) AS respondents
            FROM (SELECT a.question_key, a.response_id, COUNT(*) AS selections
                  FROM pollster_answers a JOIN pollster_responses r ON r.id = a.response_id
                  WHERE r.survey_id = #{surveyId} AND a.option_key IS NOT NULL
                  GROUP BY a.question_key, a.response_id) t
            GROUP BY t.question_key, t.selections""")
    List<Map<String, Object>> selectionCounts(@Param("surveyId") String surveyId);

    @Select("""
            SELECT a.question_key AS questionKey, a.text_value AS textValue,
                   r.player_name AS playerName, r.submitted_at AS submittedAt
            FROM pollster_answers a JOIN pollster_responses r ON r.id = a.response_id
            WHERE r.survey_id = #{surveyId} AND a.text_value IS NOT NULL
            ORDER BY r.submitted_at, a.id""")
    List<Map<String, Object>> texts(@Param("surveyId") String surveyId);
}
