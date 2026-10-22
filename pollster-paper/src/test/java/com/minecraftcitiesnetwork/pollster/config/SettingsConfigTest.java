package com.minecraftcitiesnetwork.pollster.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsConfigTest {

    @TempDir
    Path dir;

    @Test
    void settings_bundledFileLoads() throws Exception {
        Settings s = ConfigTestSupport.loader(this.dir).load("settings.yml", Settings.class);
        assertTrue(s.featured().onJoin());
        assertEquals(Duration.ofSeconds(3), s.featured().delay());
        assertEquals(Duration.ofHours(24), s.featured().cooldown());
        assertTrue(s.reminders().enabled());
        assertEquals(Duration.ofSeconds(60), s.reminders().idle());
        assertEquals(Duration.ofSeconds(300), s.reminders().repeat());
        assertEquals(255, s.textMaxLength());
        assertEquals("https://bytebin.lucko.me", s.publishing().pasteUrl());
        assertEquals("https://mccitiesnetwork.github.io/pollster", s.publishing().viewerUrl());
        assertEquals(Duration.ofSeconds(10), s.publishing().timeout());
        assertTrue(Files.exists(this.dir.resolve("defaults/default-settings.yml")));
    }

    @Test
    void settings_mergeAddsMissingKeys() throws Exception {
        Files.writeString(this.dir.resolve("settings.yml"), "text-max-length: 100\n");
        Settings s = ConfigTestSupport.loader(this.dir).load("settings.yml", Settings.class);
        assertEquals(100, s.textMaxLength());
        assertEquals(Duration.ofSeconds(3), s.featured().delay());
        assertTrue(Files.readString(this.dir.resolve("settings.yml")).contains("featured"));
    }

    @Test
    void settings_invalidValuesFallBack() throws Exception {
        Files.writeString(this.dir.resolve("settings.yml"),
                "text-max-length: -5\npublishing:\n  timeout: 0s\nreminders:\n  repeat: 0s\n");
        Settings s = ConfigTestSupport.loader(this.dir).load("settings.yml", Settings.class);
        assertEquals(255, s.textMaxLength());
        assertEquals(Duration.ofSeconds(10), s.publishing().timeout());
        // Zero repeat is meaningful: remind once.
        assertEquals(Duration.ZERO, s.reminders().repeat());
    }

    @Test
    void settings_garbageDurationFallsBack() throws Exception {
        Files.writeString(this.dir.resolve("settings.yml"), "publishing:\n  timeout: soon\n");
        Settings s = ConfigTestSupport.loader(this.dir).load("settings.yml", Settings.class);
        assertEquals(Duration.ofSeconds(10), s.publishing().timeout());
    }

    @Test
    void database_bundledIsBlank() throws Exception {
        DatabaseConfig db = ConfigTestSupport.loader(this.dir).load("database.yml", DatabaseConfig.class);
        assertTrue(db.isBlank());
    }

    @Test
    void database_filledConvertsToSettings() throws Exception {
        Files.writeString(this.dir.resolve("database.yml"), "url: 'mariadb://h:3306/p'\nusername: u\npassword: w\n");
        DatabaseConfig db = ConfigTestSupport.loader(this.dir).load("database.yml", DatabaseConfig.class);
        assertEquals("mariadb://h:3306/p", db.toSettings().url());
        assertEquals("u", db.toSettings().username());
    }
}
