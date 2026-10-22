package com.minecraftcitiesnetwork.pollster.ui;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenTest {

    @Test
    void builder_collectsPartsInOrder() {
        Screen s = Screen.builder(Component.text("T"))
                .body(Component.text("a")).body(Component.text("b"))
                .input(new Input.BoolInput("k", Component.text("K"), true))
                .button(Button.of(Component.text("Go"), c -> { }))
                .columns(3).closeOnEscape(false).build();
        assertEquals(2, s.body().size());
        assertEquals(1, s.inputs().size());
        assertEquals(1, s.buttons().size());
        assertEquals(150, s.buttons().get(0).width());
        assertEquals(3, s.columns());
        assertEquals(false, s.closeOnEscape());
    }

    @Test
    void builder_defaultsToOneColumnAndEscapeCloses() {
        Screen s = Screen.builder(Component.text("T")).build();
        assertEquals(1, s.columns());
        assertTrue(s.closeOnEscape());
    }

    @Test
    void screen_listsAreImmutable() {
        Screen s = Screen.builder(Component.text("T")).body(Component.text("a")).build();
        assertThrows(UnsupportedOperationException.class, () -> s.body().add(Component.text("x")));
    }

    @Test
    void choiceInput_copiesOptions() {
        var input = new Input.ChoiceInput("k", Component.text("K"),
                List.of(Map.entry("a", Component.text("A"))), "a");
        assertEquals(1, input.options().size());
    }
}
