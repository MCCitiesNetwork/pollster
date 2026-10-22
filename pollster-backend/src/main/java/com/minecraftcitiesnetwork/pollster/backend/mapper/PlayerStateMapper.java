package com.minecraftcitiesnetwork.pollster.backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public interface PlayerStateMapper {

    @Select("SELECT last_featured_at FROM pollster_player_state WHERE player_uuid = #{player}")
    @Nullable Instant lastFeatured(@Param("player") UUID player);

    @Insert("""
            INSERT INTO pollster_player_state (player_uuid, last_featured_at) VALUES (#{player}, #{now})
            ON DUPLICATE KEY UPDATE last_featured_at = VALUES(last_featured_at)""")
    int upsertFeatured(@Param("player") UUID player, @Param("now") Instant now);
}
