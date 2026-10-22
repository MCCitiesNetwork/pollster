package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.api.report.QuestionReport;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.api.report.TextAnswer;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static com.minecraftcitiesnetwork.pollster.config.MessageKeys.*;

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
        Button back = Button.of(style.secondary(style.text(EDITOR_COMMON_BACK)), ctx -> ctx.open(ReportScreen.create(deps, report)));
        if (question == null) {
            return Screen.builder(style.title(style.text(REPORT_ANSWERS_TITLE)))
                    .body(Component.text(style.text(REPORT_ANSWERS_MISSING_QUESTION), NamedTextColor.GRAY))
                    .button(back).build();
        }

        boolean showNames = !report.survey().anonymous();
        List<TextAnswer> all = showNames ? question.texts() : shuffled(report, questionKey, question.texts());
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Screen.Builder screen = Screen.builder(style.title(style.text(REPORT_ANSWERS_TITLE_PAGED,
                MessageContainer.value("page", String.valueOf(current + 1)),
                MessageContainer.value("pages", String.valueOf(pages)))));
        screen.body(style.edit(ReportScreen.plain(question.question().text())));
        if (all.isEmpty()) {
            screen.body(Component.text(style.text(REPORT_ANSWERS_NONE), NamedTextColor.GRAY));
        }
        for (int i = current * PAGE_SIZE; i < Math.min(all.size(), (current + 1) * PAGE_SIZE); i++) {
            TextAnswer answer = all.get(i);
            // Component.text keeps player input literal, so tags typed by players never render.
            Component line = Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text(answer.text(), NamedTextColor.WHITE));
            if (showNames && answer.playerName() != null) {
                line = line.append(Component.text(style.text(REPORT_ANSWERS_BY,
                        MessageContainer.value("name", answer.playerName())), NamedTextColor.GRAY));
            }
            screen.body(line);
        }
        if (current > 0) {
            int previous = current - 1;
            screen.button(Button.of(style.secondary(style.text(REPORT_ANSWERS_PREVIOUS)),
                    ctx -> ctx.open(create(deps, report, questionKey, previous))));
        }
        if (current < pages - 1) {
            int next = current + 1;
            screen.button(Button.of(style.secondary(style.text(EDITOR_COMMON_NEXT)),
                    ctx -> ctx.open(create(deps, report, questionKey, next))));
        }
        screen.button(back);
        return screen.columns(2).build();
    }

    /**
     * Submission order could be matched against who was online, so anonymous answers are mixed.
     * The seed depends only on this report, so every page of it sees the same order and paging
     * never skips or repeats an answer.
     */
    private static List<TextAnswer> shuffled(SurveyReport report, String questionKey, List<TextAnswer> texts) {
        List<TextAnswer> mixed = new ArrayList<>(texts);
        long seed = Objects.hash(report.survey().id(), questionKey, texts.size(), report.participants());
        Collections.shuffle(mixed, new Random(seed));
        return mixed;
    }
}
