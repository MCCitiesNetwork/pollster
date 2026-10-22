package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One button per question. On a survey that is not a draft every button opens a read-only view. */
public final class QuestionListScreen {

    private QuestionListScreen() {
    }

    public static @NotNull Screen create(@NotNull AdminUi ui, @NotNull SurveyDefinition s) {
        boolean draft = s.status() == SurveyStatus.DRAFT;
        Screen.Builder b = Screen.builder(ui.style().title("Questions: " + AdminSupport.truncate(s.name(), 30)));
        if (!draft) {
            b.body(Component.text("Questions are locked once a survey has been opened.", NamedTextColor.GRAY));
        }
        List<Question> questions = s.questions();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            String label = (i + 1) + ". [" + q.type() + "] " + AdminSupport.truncate(q.text(), 40)
                    + (q.showIf() != null ? " ⓘ" : "");
            b.button(AdminSupport.wide(ui.style().edit(label), ctx -> ctx.open(QuestionEditScreen.create(ui, s, q, null))));
        }
        if (draft) {
            b.button(AdminSupport.wide(ui.style().primary("Add question"), ctx ->
                    ctx.open(QuestionEditScreen.create(ui, s, null, newQuestion(s), null))));
        }
        b.button(AdminSupport.wide(ui.style().secondary("Back"), ctx -> HubScreen.open(ui, ctx, s.id())));
        return b.build();
    }

    /** A blank text question under a key no other question uses. */
    private static Question newQuestion(SurveyDefinition s) {
        Set<String> used = new HashSet<>();
        s.questions().forEach(q -> used.add(q.key()));
        String key = QuestionEdits.uniqueKey("question", used);
        return new Question(key, QuestionType.TEXT, "New question", List.of(), false, 0, 0, null);
    }
}
