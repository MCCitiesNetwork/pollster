import { beforeEach, expect, test, waitForAssertion, waitForStable } from '@plugwright/runner';
import { readdirSync } from 'node:fs';
import { bodyText, click, clickOn, currentDialog, waitForDialog } from './dialogs.js';
import { EXPORTS_DIR, removeE2eSurveys, screenOf, seedOpenSurvey, startSurvey, unloadSurvey } from './support.js';

beforeEach(() => removeE2eSurveys());

/** Four questions, one of each type, and a chat reward. */
const FULL_SURVEY = `
description: "Every question type."
anonymous: true
featured: false
requires-permission: false
rewards:
  - message: "<green>Reward for the e2e survey"
questions:
  - key: colour
    type: SINGLE
    text: "Favourite colour?"
    options:
      - { key: red, label: "Red" }
      - { key: blue, label: "Blue" }
  - key: likes-cats
    type: YES_NO
    text: "Do you like cats?"
  - key: why
    type: TEXT
    text: "Why?"
    required: false
  - key: snacks
    type: MULTIPLE
    text: "Which snacks?"
    min: 2
    max: 3
    options:
      - { key: crisps, label: "Crisps" }
      - { key: nuts, label: "Nuts" }
      - { key: fruit, label: "Fruit" }
`;

const CONDITIONAL_SURVEY = `
description: "The second question depends on the first."
anonymous: false
questions:
  - key: has-pet
    type: YES_NO
    text: "Do you have a pet?"
  - key: pet-name
    type: TEXT
    text: "What is its name?"
    show-if: 'answer(has-pet) == "yes"'
`;

test('a player completes a survey of every question type and gets its reward', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'full', 'Full survey', FULL_SURVEY);
    const at = screenOf('Full survey');

    const intro = await startSurvey(player, 'Full survey', 'Full survey - New');
    expect(bodyText(intro)).toContain('Questions: 4');
    expect(bodyText(intro)).toContain('Your answers are anonymous.');
    expect(bodyText(intro)).toContain('Completing this survey gives rewards.');

    await clickOn(player, at('Question 1 of 4'), 'Red');
    await clickOn(player, at('Question 2 of 4'), 'Yes');
    await clickOn(player, at('Question 3 of 4'), 'Next', { text: 'They purr.' });
    const snacks = await waitForDialog(player, at('Question 4 of 4'));
    expect(snacks.inputs.map(i => i.label)).toEqual(['Crisps', 'Nuts', 'Fruit']);
    click(player, snacks, 'Next', { [snacks.inputs[0].key]: true, [snacks.inputs[2].key]: true });

    const review = await waitForDialog(player, at('Check your answers'));
    const answers = bodyText(review);
    expect(answers).toContain('Red');
    expect(answers).toContain('They purr.');
    expect(answers).toContain('Crisps');
    expect(answers).toContain('Fruit');
    click(player, review, 'Submit');

    await waitForDialog(player, at('Thank you for completing Full survey.'), 8000);
    await expect(player).toHaveReceivedMessage('Reward for the e2e survey');

    player.chat('/surveys');
    const list = await waitForDialog(player, 'Surveys');
    expect(list.buttons.map(b => b.label)).toContain('Full survey - Completed');
});

test('a multiple choice question refuses too few selections and keeps the dialog open', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'limits', 'Limits survey', FULL_SURVEY);
    const at = screenOf('Limits survey');

    await startSurvey(player, 'Limits survey');
    await clickOn(player, at('Question 1 of 4'), 'Blue');
    await clickOn(player, at('Question 2 of 4'), 'No');
    await clickOn(player, at('Question 3 of 4'), 'Skip');
    const snacks = await waitForDialog(player, at('Question 4 of 4'));
    click(player, snacks, 'Next', { [snacks.inputs[1].key]: true });

    const again = await waitForDialog(player, at('Pick at least 2.'));
    expect(bodyText(again)).toContain('Question 4 of 4');
    expect(again.inputs[1].initial).toBe(true);
});

test('a skipped optional question shows as skipped on the review', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'skip', 'Skip survey', FULL_SURVEY);
    const at = screenOf('Skip survey');

    await startSurvey(player, 'Skip survey');
    await clickOn(player, at('Question 1 of 4'), 'Red');
    await clickOn(player, at('Question 2 of 4'), 'Yes');
    const optional = await waitForDialog(player, at('Question 3 of 4'));
    expect(optional.buttons.map(b => b.label)).toContain('Skip');
    click(player, optional, 'Skip');
    const snacks = await waitForDialog(player, at('Question 4 of 4'));
    click(player, snacks, 'Next', { [snacks.inputs[0].key]: true, [snacks.inputs[1].key]: true });

    const review = await waitForDialog(player, at('Check your answers'));
    expect(bodyText(review)).toContain('(skipped)');
});

test('Back returns to the previous question with its answer kept', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'back', 'Back survey', FULL_SURVEY);
    const at = screenOf('Back survey');

    await startSurvey(player, 'Back survey');
    await clickOn(player, at('Question 1 of 4'), 'Red');
    await clickOn(player, at('Question 2 of 4'), 'Yes');
    await clickOn(player, at('Question 3 of 4'), 'Back', { text: 'Typed then went back' });
    await clickOn(player, at('Question 2 of 4'), 'Yes');

    const text = await waitForDialog(player, at('Question 3 of 4'));
    expect(text.buttons.map(b => b.label)).toContain('Back');
});

test('a condition hides a question when its answer does not match', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'condition', 'Condition survey', CONDITIONAL_SURVEY);
    const at = screenOf('Condition survey');

    const intro = await startSurvey(player, 'Condition survey');
    expect(bodyText(intro)).toContain('Your name is recorded with your answers.');
    await clickOn(player, at('Do you have a pet?'), 'No');

    const review = await waitForDialog(player, at('Check your answers'));
    expect(bodyText(review)).not.toContain('What is its name?');
});

test('a condition shows a question when its answer matches', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'condition-yes', 'Pet survey', CONDITIONAL_SURVEY);
    const at = screenOf('Pet survey');

    await startSurvey(player, 'Pet survey');
    await clickOn(player, at('Do you have a pet?'), 'Yes');
    await clickOn(player, at('What is its name?'), 'Next', { text: 'Biscuit' });

    const review = await waitForDialog(player, at('Check your answers'));
    expect(bodyText(review)).toContain('Biscuit');
});

test('a required text question refuses an empty answer', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'required', 'Required survey', CONDITIONAL_SURVEY);
    const at = screenOf('Required survey');

    await startSurvey(player, 'Required survey');
    await clickOn(player, at('Do you have a pet?'), 'Yes');
    await clickOn(player, at('What is its name?'), 'Next', { text: '   ' });

    await waitForDialog(player, at('This question needs an answer.'));
});

test('Cancel on the review discards the answers', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'cancel', 'Cancel survey', CONDITIONAL_SURVEY);
    const at = screenOf('Cancel survey');

    await startSurvey(player, 'Cancel survey');
    await clickOn(player, at('Do you have a pet?'), 'No');
    await clickOn(player, at('Check your answers'), 'Cancel');

    await expect(player).toHaveReceivedMessage('Survey cancelled. Nothing was saved.');
    player.chat('/surveys resume');
    await expect(player).toHaveReceivedMessage('You have no unfinished survey.');
});

test('a paused survey reminds the player and /surveys resume continues it', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'resume', 'Resume survey', FULL_SURVEY);
    const at = screenOf('Resume survey');

    await startSurvey(player, 'Resume survey');
    await clickOn(player, at('Question 1 of 4'), 'Blue');
    // The player walks away from question 2. The e2e settings remind after 2 seconds idle.
    await waitForDialog(player, at('Question 2 of 4'));
    await expect(player).toHaveReceivedMessage('You have an unfinished survey. Click to continue', { timeout: 10000 });

    player.chat('/surveys resume');
    await waitForDialog(player, at('Question 2 of 4'));
});

test('a survey that needs a permission is hidden from players without it', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'gated', 'Gated survey',
        CONDITIONAL_SURVEY.replace('anonymous: false', 'anonymous: false\nrequires-permission: true'));

    player.chat('/surveys');
    const list = await waitForDialog(player, 'Surveys');
    expect(bodyText(list)).toContain('There are no surveys available right now.');
    expect(list.buttons.some(b => b.label.includes('Gated survey'))).toBe(false);
});

test('a featured survey opens on join, with a Not now button', async (ctx) => {
    const { server, createPlayer } = ctx;
    const { id } = await seedOpenSurvey(ctx, 'featured', 'Featured survey',
        CONDITIONAL_SURVEY.replace('anonymous: false', 'anonymous: false\nfeatured: true'));
    try {
        const newcomer = await createPlayer();
        const intro = await waitForDialog(newcomer, screenOf('Featured survey')('Questions:'), 10000);
        expect(intro.buttons.map(b => b.label)).toContain('Not now');
    } finally {
        // Bots that join before the next reload must not be greeted by this survey.
        await unloadSurvey(server, id);
    }
});

test('the report counts a completed response and the console can export it', async (ctx) => {
    const { player, server } = ctx;
    const { id, admin } = await seedOpenSurvey(ctx, 'report', 'Report survey', CONDITIONAL_SURVEY);
    const at = screenOf('Report survey');

    await startSurvey(player, 'Report survey');
    await clickOn(player, at('Do you have a pet?'), 'No');
    await clickOn(player, at('Check your answers'), 'Submit');
    await waitForDialog(player, at('Thank you for completing'), 8000);

    admin.chat(`/pollster report ${id}`);
    const report = await waitForDialog(admin, 'Report: Report survey', 8000);
    expect(bodyText(report)).toMatch(/Participants: 1\b/);

    await server.execute(`pollster export ${id}`);
    await waitForAssertion(async () => {
        expect(readdirSync(EXPORTS_DIR).some(f => f.startsWith(`${id}-`) && f.endsWith('.json'))).toBe(true);
    }, { timeout: 5000 });
});

test('the player cannot take a survey twice', async (ctx) => {
    const { player } = ctx;
    await seedOpenSurvey(ctx, 'once', 'Once survey', CONDITIONAL_SURVEY);
    const at = screenOf('Once survey');

    await startSurvey(player, 'Once survey', 'Once survey - New');
    await clickOn(player, at('Do you have a pet?'), 'No');
    await clickOn(player, at('Check your answers'), 'Submit');
    await waitForDialog(player, at('Thank you for completing'), 8000);

    player.chat('/surveys');
    await clickOn(player, 'Surveys', 'Once survey - Completed');
    // The completed entry is not clickable: nothing new opens.
    await waitForStable(() => currentDialog(player) == null, { duration: 500 });
});
