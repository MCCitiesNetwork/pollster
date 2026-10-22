package com.minecraftcitiesnetwork.pollster.backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.Nullable;

public interface AnswerMapper {

    @Insert("""
            INSERT INTO pollster_answers (response_id, question_key, option_key, text_value)
            VALUES (#{responseId}, #{questionKey}, #{optionKey}, #{text})""")
    int insert(@Param("responseId") long responseId, @Param("questionKey") String questionKey,
               @Param("optionKey") @Nullable String optionKey, @Param("text") @Nullable String text);
}
