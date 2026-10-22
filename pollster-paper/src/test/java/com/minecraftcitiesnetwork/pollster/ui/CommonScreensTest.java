package com.minecraftcitiesnetwork.pollster.ui;

import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonScreensTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static final class FakeContext implements ScreenContext {
        @Override public Player player() { return null; }
        @Override public String text(String key) { return null; }
        @Override public Boolean bool(String key) { return null; }
        @Override public Float number(String key) { return null; }
        @Override public void open(Screen s) { }
        @Override public void close() { }
    }

    @Test
    void confirm_hasConfirmThenCancel() {
        List<String> calls = new ArrayList<>();
        Screen s = Screens.confirm(Component.text("Delete?"), Component.text("Sure?"),
                c -> calls.add("confirm"), c -> calls.add("cancel"));
        assertEquals(2, s.buttons().size());
        assertEquals("Confirm", plain(s.buttons().get(0).label()));
        assertEquals("Cancel", plain(s.buttons().get(1).label()));
        s.buttons().get(0).onClick().accept(new FakeContext());
        assertEquals(List.of("confirm"), calls);
        s.buttons().get(1).onClick().accept(new FakeContext());
        assertEquals(List.of("confirm", "cancel"), calls);
    }

    @Test
    void error_listsDetailsAsGrayBullets() {
        Screen s = Screens.error(Component.text("Bad"),
                List.of(Component.text("one"), Component.text("two")), c -> { });
        assertEquals(3, s.body().size());
        assertEquals("• one", plain(s.body().get(1)));
        assertEquals(NamedTextColor.GRAY, s.body().get(1).color());
        assertEquals("• two", plain(s.body().get(2)));
        assertEquals(1, s.buttons().size());
    }

    @Test
    void loading_hasNoButtons() {
        Screen s = Screens.loading(Component.text("Wait"));
        assertTrue(s.buttons().isEmpty());
        assertTrue(s.inputs().isEmpty());
    }

    @Test
    void style_appliesConfiguredColours() {
        MessageContainer messages = new MessageContainer();
        messages.setMessage("ui.style.primary", "<blue><bold><text>");
        Component c = new Style(messages).primary("Go");
        assertEquals("Go", plain(c));
        assertEquals(NamedTextColor.BLUE, c.color());
        assertEquals(TextDecoration.State.TRUE, c.decoration(TextDecoration.BOLD));
    }

    @Test
    void style_fallsBackToDefaultsAndKeepsTextLiteral() {
        Component c = Style.defaults().title("<red>x");
        assertEquals("<red>x", plain(c));
        assertEquals(NamedTextColor.GOLD, c.color());
    }

    @Test
    void confirm_usesConfiguredButtonWords() {
        MessageContainer messages = new MessageContainer();
        messages.setMessage("ui.button.confirm", "Yes");
        messages.setMessage("ui.button.cancel", "No");
        Screen s = Screens.confirm(new Style(messages), Component.text("T"), Component.text("M"), c -> { }, c -> { });
        assertEquals("Yes", plain(s.buttons().get(0).label()));
        assertEquals("No", plain(s.buttons().get(1).label()));
    }
}
