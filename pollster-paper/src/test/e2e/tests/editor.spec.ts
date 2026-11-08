import { beforeEach, expect, test } from '@plugwright/runner';
import { existsSync } from 'node:fs';
import { bodyText, click, clickOn, waitForDialog } from './dialogs.js';
import { openHub, readSurveyFile, removeE2eSurveys, surveyFile, surveyId, writeDraft } from './support.js';

beforeEach(async ({ player }) => {
    removeE2eSurveys();
    await player.makeOp();
});

const COLOUR_QUESTION = `
description: "Seeded by the editor spec."
anonymous: false
questions:
  - key: colour
    type: SINGLE
    text: "Favourite colour?"
    options:
      - { key: red, label: "Red" }
      - { key: blue, label: "Blue" }
`;

test('the create wizard makes a draft survey and writes its file', async ({ player }) => {
    const id = surveyId('wizard');

    player.chat('/pollster');
    await clickOn(player, 'Surveys', 'Create survey');
    await clickOn(player, 'Create survey: basics', 'Next',
        { survey_id: id, name: 'Wizard survey', description: 'Made in the wizard.' });
    await clickOn(player, 'Create survey: settings', 'Next', { anonymous: true, featured: false });
    const confirm = await waitForDialog(player, 'Create survey: confirm');
    expect(bodyText(confirm)).toContain(`Id: ${id}`);
    expect(bodyText(confirm)).toContain('Anonymous: yes');
    click(player, confirm, 'Create');

    const hub = await waitForDialog(player, 'Survey: Wizard survey', 8000);
    expect(bodyText(hub)).toContain('Status: DRAFT');
    expect(bodyText(hub)).toContain('Questions: 0');
    expect(readSurveyFile(id)).toContain('Wizard survey');
});

test('the create wizard refuses an invalid id and keeps what was typed', async ({ player }) => {

    player.chat('/pollster');
    await clickOn(player, 'Surveys', 'Create survey');
    await clickOn(player, 'Create survey: basics', 'Next', { survey_id: 'Bad Id!', name: 'Kept name' });

    const again = await waitForDialog(player, { title: 'Create survey: basics', body: 'Survey id must be' });
    expect(again.inputs.find(i => i.key === 'name')?.initial).toBe('Kept name');
});

test('a question built in the editor is saved to the survey file', async ({ player, server }) => {
    const id = surveyId('build');
    await writeDraft(server, id, 'Build survey', 'questions: []');

    click(player, await openHub(player, id, 'Build survey'), 'Questions');
    await clickOn(player, 'Questions: Build survey', 'Add question');
    // A choice question with no options goes straight to its option list on Save.
    await clickOn(player, 'Question:', 'Save', { key: 'drink', text: 'Tea or coffee?', type: 'SINGLE', required: true });
    await clickOn(player, 'Options: Tea or coffee?', 'Bulk add');
    await clickOn(player, 'Bulk add options', 'Add options', { options: 'tea: Tea\ncoffee: Coffee' });
    const options = await waitForDialog(player, 'Options: Tea or coffee?');
    const labels = options.buttons.map(b => b.label);
    expect(labels).toContain('tea: Tea');
    expect(labels).toContain('coffee: Coffee');
    click(player, options, 'Save question');

    const list = await waitForDialog(player, 'Questions: Build survey', 8000);
    expect(list.buttons.map(b => b.label)).toContain('1. [SINGLE] Tea or coffee?');
    const file = readSurveyFile(id);
    expect(file).toContain('key: drink');
    expect(file).toContain('type: SINGLE');
    expect(file).toMatch(/key: tea/);
    expect(file).toMatch(/key: coffee/);
});

test('opening a survey locks its questions', async ({ player, server }) => {
    const id = surveyId('lock');
    await writeDraft(server, id, 'Lock survey', COLOUR_QUESTION);

    click(player, await openHub(player, id, 'Lock survey'), 'Open survey');
    const warning = await waitForDialog(player, 'Open survey?');
    expect(bodyText(warning)).toContain('Opening this survey locks its questions');
    click(player, warning, 'Confirm');
    const hub = await waitForDialog(player, 'Survey: Lock survey', 8000);
    expect(bodyText(hub)).toContain('Status: OPEN');
    expect(readSurveyFile(id)).toContain('status: OPEN');

    click(player, hub, 'Questions');
    const list = await waitForDialog(player, 'Questions: Lock survey');
    expect(bodyText(list)).toContain('Questions are locked once a survey has been opened.');
    expect(list.buttons.map(b => b.label)).not.toContain('Add question');
});

test('duplicate copies a survey into a new draft under the id typed', async ({ player, server }) => {
    const id = surveyId('original');
    const copyId = surveyId('copy');
    await writeDraft(server, id, 'Original survey', COLOUR_QUESTION);

    click(player, await openHub(player, id, 'Original survey'), 'Duplicate');
    await clickOn(player, 'Duplicate survey', 'Create', { survey_id: copyId });

    const copy = await waitForDialog(player, { title: 'Survey: Original survey', body: `Id: ${copyId}` }, 8000);
    expect(bodyText(copy)).toContain('Status: DRAFT');
    expect(bodyText(copy)).toContain('Questions: 1');
    expect(readSurveyFile(copyId)).toContain('key: colour');
});

test('deleting a draft removes its file', async ({ player, server }) => {
    const id = surveyId('delete');
    await writeDraft(server, id, 'Delete survey', COLOUR_QUESTION);

    click(player, await openHub(player, id, 'Delete survey'), 'Delete');
    const confirm = await waitForDialog(player, 'Delete survey?');
    expect(bodyText(confirm)).toContain(`Delete ${id}? This removes the survey file.`);
    click(player, confirm, 'Confirm');

    const list = await waitForDialog(player, 'Surveys', 8000);
    expect(list.buttons.some(b => b.label.includes(id))).toBe(false);
    expect(existsSync(surveyFile(id))).toBe(false);
});

test('preview runs a draft survey without saving a response', async ({ player, server }) => {
    const id = surveyId('preview');
    await writeDraft(server, id, 'Preview survey', COLOUR_QUESTION);

    click(player, await openHub(player, id, 'Preview survey'), 'Preview');
    const intro = await clickOn(player, 'Preview survey', 'Start');
    expect(intro.title).toContain('[PREVIEW]');
    await clickOn(player, { title: 'Preview survey', body: 'Favourite colour?' }, 'Red');
    await clickOn(player, { title: 'Preview survey', body: 'Check your answers' }, 'Submit');
    const thanks = await waitForDialog(player, { title: 'Preview survey', body: 'Thank you for completing' }, 8000);
    expect(bodyText(thanks)).toContain('This was a preview. Nothing was saved and no rewards were given.');

    const hub = await openHub(player, id, 'Preview survey');
    expect(bodyText(hub)).toContain('Responses: 0');
});
