package com.minecraftcitiesnetwork.pollster.backend.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ResponseMapper {

    @Insert("""
            INSERT INTO pollster_responses (survey_id, player_uuid, started_at, submitted_at)
            VALUES (#{surveyId}, #{player}, #{startedAt}, #{submittedAt})""")
    int insert(@Param("surveyId") String surveyId, @Param("player") UUID player,
               @Param("startedAt") Instant startedAt, @Param("submittedAt") Instant submittedAt);

    /** Valid on the same connection straight after {@link #insert}. */
    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Select("SELECT COUNT(*) FROM pollster_responses WHERE survey_id = #{surveyId} AND player_uuid = #{player}")
    int countFor(@Param("surveyId") String surveyId, @Param("player") UUID player);

    @Select("SELECT survey_id FROM pollster_responses WHERE player_uuid = #{player}")
    List<String> surveysCompletedBy(@Param("player") UUID player);

    @Select("SELECT survey_id AS surveyId, COUNT(*) AS total FROM pollster_responses GROUP BY survey_id")
    List<Map<String, Object>> counts();

    @Delete("DELETE FROM pollster_responses WHERE survey_id = #{surveyId}")
    int deleteBySurvey(@Param("surveyId") String surveyId);
}
