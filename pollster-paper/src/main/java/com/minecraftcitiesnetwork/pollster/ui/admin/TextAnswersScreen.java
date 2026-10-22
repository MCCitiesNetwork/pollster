package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.report.QuestionReport;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.api.report.TextAnswer;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** A paginated list of the answers to one text question. Names show only for non-anonymous surveys. */
public final class TextAnswersScreen {

    public static final int PAGE_SIZE = 8;

    private TextAnswersScreen() {
    }

    public static @NotNull Screen create(@NotNull ReportScreen.Deps deps, @NotNull SurveyReport report,
                                         @NotNull String questionKey, int page) {
        Style style = deps.style();
        QuestionReport question = report.questions().stream()
                .filter(q -> q.question().key().equals(questionKey)).findFirst().orElse(null);
        Button back = Button.of(style.secondary("Back"), ctx -> ctx.open(ReportScreen.create(deps, report)));
        if (question == null) {
            return Screen.builder(style.title("Answers"))
                    .body(Component.text("This question no longer exists.", NamedTextColor.GRAY))
                    .button(back).build();
        }

        List<TextAnswer> all = question.texts();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Screen.Builder screen = Screen.builder(style.title("Answers (" + (current + 1) + "/" + pages + ")"));
        screen.body(style.edit(ReportScreen.plain(question.question().text())));
        if (all.isEmpty()) {
            screen.body(Component.text("No answers yet.", NamedTextColor.GRAY));
        }
        boolean showNames = !report.survey().anonymous();
        for (int i = current * PAGE_SIZE; i < Math.min(all.size(), (current + 1) * PAGE_SIZE); i++) {
            TextAnswer answer = all.get(i);
            // Component.text keeps player input literal, so tags typed by players never render.
            Component line = Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text(answer.text(), NamedTextColor.WHITE));
            if (showNames && answer.playerName() != null) {
                line = line.append(Component.text(" - " + answer.playerName(), NamedTextColor.GRAY));
            }
            screen.body(line);
        }
        if (current > 0) {
            int previous = current - 1;
            screen.button(Button.of(style.secondary("Previous"),
                    ctx -> ctx.open(create(deps, report, questionKey, previous))));
        }
        if (current < pages - 1) {
            int next = current + 1;
            screen.button(Button.of(style.secondary("Next"),
                    ctx -> ctx.open(create(deps, report, questionKey, next))));
        }
        screen.button(back);
        return screen.columns(2).build();
    }
}
