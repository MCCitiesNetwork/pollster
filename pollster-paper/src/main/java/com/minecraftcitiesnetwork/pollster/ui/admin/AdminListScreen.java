package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** The paginated list of surveys: the editor's entry screen. */
public final class AdminListScreen {

    public static final int PAGE_SIZE = 10;

    private AdminListScreen() {
    }

    /** Shows the loading notice, fetches response counts, then opens the first page. */
    public static void open(@NotNull AdminUi ui, @NotNull ScreenContext ctx) {
        open(ui, ctx, 0);
    }

    public static void open(@NotNull AdminUi ui, @NotNull ScreenContext ctx, int page) {
        ctx.open(AdminSupport.loading(ui));
        ui.service().responseCounts().whenComplete((counts, error) -> {
            if (error != null) {
                ctx.open(AdminSupport.failure(ui, error, ScreenContext::close));
                return;
            }
            ctx.open(create(ui, page, counts));
        });
    }

    public static @NotNull Screen create(@NotNull AdminUi ui, int page, @NotNull Map<String, Integer> counts) {
        List<SurveyDefinition> all = ui.service().surveys();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Screen.Builder builder = Screen.builder(ui.style().title("Surveys"))
                .body(Component.text(all.size() + " surveys · page " + (current + 1) + "/" + pages,
                        NamedTextColor.GRAY));

        int from = current * PAGE_SIZE;
        for (SurveyDefinition survey : all.subList(from, Math.min(all.size(), from + PAGE_SIZE))) {
            builder.button(AdminSupport.wide(ui.style().edit(label(ui, survey, counts)), ctx -> {
                // Read the survey again: the file may have changed since this list was drawn.
                ui.service().survey(survey.id()).ifPresentOrElse(
                        fresh -> ctx.open(HubScreen.create(ui, fresh, ui.service().lock(fresh.id()),
                                counts.getOrDefault(fresh.id(), 0))),
                        () -> ctx.open(AdminSupport.unavailable(ui, c -> open(ui, c, current))));
            }));
        }

        builder.button(AdminSupport.wide(ui.style().primary("Create survey"),
                ctx -> ctx.open(CreateWizard.basics(ui, CreateWizard.Draft.EMPTY, null))));
        if (current > 0) {
            builder.button(AdminSupport.wide(ui.style().secondary("Previous page"),
                    ctx -> ctx.open(create(ui, current - 1, counts))));
        }
        if (current < pages - 1) {
            builder.button(AdminSupport.wide(ui.style().secondary("Next page"),
                    ctx -> ctx.open(create(ui, current + 1, counts))));
        }
        builder.button(AdminSupport.wide(ui.style().secondary("Close"), ScreenContext::close));
        return builder.build();
    }

    private static String label(AdminUi ui, SurveyDefinition survey, Map<String, Integer> counts) {
        boolean locked = survey.status() != SurveyStatus.DRAFT
                && ui.service().lock(survey.id()) != SurveyService.SurveyLock.OK;
        return survey.id() + " · " + AdminSupport.truncate(survey.name(), 30)
                + " [" + survey.status() + "]" + (locked ? " [LOCKED]" : "")
                + " · " + counts.getOrDefault(survey.id(), 0) + " responses";
    }

    static @NotNull Button back(@NotNull AdminUi ui) {
        return Button.of(ui.style().secondary("Back"), ctx -> open(ui, ctx));
    }
}
