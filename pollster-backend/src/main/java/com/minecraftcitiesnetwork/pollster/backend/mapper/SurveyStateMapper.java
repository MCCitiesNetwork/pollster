package com.minecraftcitiesnetwork.pollster.backend.mapper;

import com.minecraftcitiesnetwork.pollster.backend.SurveyStateRecord;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public interface SurveyStateMapper {

    /** Returns 0 when a row already exists, which is how a second open is detected. */
    @Insert("""
            INSERT IGNORE INTO pollster_survey_state (survey_id, content_hash, opened_at)
            VALUES (#{surveyId}, #{hash}, #{now})""")
    int insertIgnore(@Param("surveyId") String surveyId, @Param("hash") String hash, @Param("now") Instant now);

    @Update("UPDATE pollster_survey_state SET closed_at = #{now} WHERE survey_id = #{surveyId} AND closed_at IS NULL")
    int close(@Param("surveyId") String surveyId, @Param("now") Instant now);

    @Update("UPDATE pollster_survey_state SET closed_at = NULL WHERE survey_id = #{surveyId}")
    int reopen(@Param("surveyId") String surveyId);

    @Select("""
            SELECT survey_id, content_hash, opened_at, closed_at
            FROM pollster_survey_state WHERE survey_id = #{surveyId}""")
    @ConstructorArgs({
            @Arg(column = "survey_id", javaType = String.class),
            @Arg(column = "content_hash", javaType = String.class),
            @Arg(column = "opened_at", javaType = Instant.class),
            @Arg(column = "closed_at", javaType = Instant.class)})
    @Nullable SurveyStateRecord select(@Param("surveyId") String surveyId);

    @Select("""
            SELECT survey_id, content_hash, opened_at, closed_at
            FROM pollster_survey_state WHERE survey_id = #{surveyId} FOR UPDATE""")
    @ConstructorArgs({
            @Arg(column = "survey_id", javaType = String.class),
            @Arg(column = "content_hash", javaType = String.class),
            @Arg(column = "opened_at", javaType = Instant.class),
            @Arg(column = "closed_at", javaType = Instant.class)})
    @Nullable SurveyStateRecord selectForUpdate(@Param("surveyId") String surveyId);

    @Delete("DELETE FROM pollster_survey_state WHERE survey_id = #{surveyId}")
    int delete(@Param("surveyId") String surveyId);
}
