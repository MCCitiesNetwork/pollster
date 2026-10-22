package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.report.OptionResult;
import com.minecraftcitiesnetwork.pollster.api.report.QuestionReport;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.publish.ReportPublisher;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.DialogRenderer;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import com.minecraftcitiesnetwork.pollster.ui.common.ErrorScreen;
import com.minecraftcitiesnetwork.pollster.ui.common.LoadingScreen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

import static com.minecraftcitiesnetwork.pollster.config.MessageKeys.*;

/**
 * The in-game report for one survey: totals, a bar per option, and buttons to view text answers,
 * publish and export. Built fresh from a {@link SurveyReport} each time it is opened.
 */
public final class ReportScreen {

    static final int BAR_BLOCKS = 10;
    static final String FILLED = "█";
    static final String EMPTY = "░";

    private ReportScreen() {
    }

    /**
     * What the report screens need from the rest of the plugin.
     *
     * @param back reopens the survey hub; given the clicking context and the survey id
     */
    public record Deps(@NotNull Style style, @Nullable DialogRenderer renderer, @Nullable SurveyService service,
                       @Nullable ReportPublisher publisher, @NotNull BiConsumer<ScreenContext, String> back) {
    }

    /** Opens the report for a survey id; the admin UI wires one into its hub as the Report button. */
    @FunctionalInterface
    public interface Opener {
        void open(@NotNull Player player, @NotNull String surveyId);
    }

    /** Shows a loading notice, fetches the report, then shows it (or an error). */
    public static @NotNull Opener opener(@NotNull Deps deps) {
        DialogRenderer renderer = java.util.Objects.requireNonNull(deps.renderer());
        SurveyService service = java.util.Objects.requireNonNull(deps.service());
        return (player, surveyId) -> {
            renderer.show(player, LoadingScreen.create(deps.style(), deps.style().text(REPORT_SCREEN_LOADING_TITLE)));
            service.report(surveyId).whenComplete((report, error) -> {
                // report() completes on the main thread, where dialogs may be shown.
                if (error != null || report == null) {
                    renderer.show(player, ErrorScreen.create(deps.style(), deps.style().text(REPORT_SCREEN_LOAD_FAILED),
                            List.of(), ctx -> ctx.close()));
                } else {
                    renderer.show(player, create(deps, report));
                }
            });
        };
    }

    public static @NotNull Screen create(@NotNull Deps deps, @NotNull SurveyReport report) {
        Style style = deps.style();
        String surveyId = report.survey().id();
        Screen.Builder screen = Screen.builder(style.title(style.text(REPORT_SCREEN_TITLE, MessageContainer.value("name", report.survey().name()))));

        String average = report.averageSeconds().isPresent()
                ? formatSeconds(style, Math.round(report.averageSeconds().getAsDouble()))
                : style.text(REPORT_SCREEN_NOT_AVAILABLE);
        screen.body(style.label(style.text(REPORT_SCREEN_PARTICIPANTS), Component.text(report.participants())));
        screen.body(style.label(style.text(REPORT_SCREEN_AVERAGE_TIME), Component.text(average)));

        for (QuestionReport questionReport : report.questions()) {
            Question question = questionReport.question();
            screen.body(Component.empty());
            screen.body(style.edit(plain(question.text())));
            if (questionReport.shownTo() < report.participants()) {
                screen.body(Component.text(style.text(REPORT_SCREEN_SHOWN_TO,
                        MessageContainer.value("shown", String.valueOf(questionReport.shownTo())),
                        MessageContainer.value("total", String.valueOf(report.participants()))), NamedTextColor.GRAY));
            }
            if (question.type() == QuestionType.TEXT) {
                screen.body(Component.text(style.text(REPORT_SCREEN_ANSWERS_COUNT,
                        MessageContainer.value("count", String.valueOf(questionReport.texts().size()))),
                        NamedTextColor.GRAY));
                continue;
            }
            for (OptionResult option : questionReport.options()) {
                // Option labels are admin text: Component.text keeps any markup literal.
                screen.body(Component.text(bar(option.percent()) + " ", NamedTextColor.GREEN)
                        .append(Component.text(Math.round(option.percent()) + "% ", NamedTextColor.YELLOW))
                        .append(Component.text(option.label(), NamedTextColor.WHITE))
                        .append(Component.text(" (" + option.votes() + ")", NamedTextColor.GRAY)));
            }
        }

        for (QuestionReport questionReport : report.questions()) {
            Question question = questionReport.question();
            if (question.type() == QuestionType.TEXT) {
                screen.button(new Button(style.edit(style.text(REPORT_SCREEN_VIEW_ANSWERS)),
                        Component.text(plain(question.text())), Button.DEFAULT_WIDTH,
                        ctx -> ctx.open(TextAnswersScreen.create(deps, report, question.key(), 0))));
            }
        }
        screen.button(Button.of(style.primary(style.text(REPORT_SCREEN_PUBLISH)), ctx -> {
            if (deps.publisher() != null) {
                deps.publisher().publish(ctx.player(), surveyId);
            }
            // Rebuilt so the dialog stays current; the link arrives in chat when the upload ends.
            ctx.open(create(deps, report));
        }));
        screen.button(Button.of(style.primary(style.text(REPORT_SCREEN_EXPORT)), ctx -> {
            if (deps.publisher() != null) {
                deps.publisher().export(ctx.player(), surveyId);
            }
            ctx.open(create(deps, report));
        }));
        screen.button(Button.of(style.secondary(style.text(EDITOR_COMMON_BACK)), ctx -> deps.back().accept(ctx, surveyId)));
        return screen.columns(2).build();
    }

    /** A bar of {@value #BAR_BLOCKS} blocks, one filled block per 10 percent, rounded. */
    static @NotNull String bar(double percent) {
        int filled = (int) Math.max(0, Math.min(BAR_BLOCKS, Math.round(percent / 100.0 * BAR_BLOCKS)));
        return FILLED.repeat(filled) + EMPTY.repeat(BAR_BLOCKS - filled);
    }

    /** Question text is stored as MiniMessage; dialogs show it as plain words. */
    static @NotNull String plain(@NotNull String miniMessage) {
        return PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private static String formatSeconds(Style style, long seconds) {
        return seconds >= 60
                ? style.text(REPORT_SCREEN_DURATION_MINUTES, MessageContainer.value("minutes", String.valueOf(seconds / 60)),
                        MessageContainer.value("seconds", String.valueOf(seconds % 60)))
                : style.text(REPORT_SCREEN_DURATION_SECONDS, MessageContainer.value("seconds", String.valueOf(seconds)));
    }
}
