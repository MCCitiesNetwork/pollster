import { waitUntil, type PlayerWrapper, type ServerWrapper, type TestContext } from '@plugwright/runner';
import { mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { click, clickOn, waitForDialog, type Dialog, type DialogMatch } from './dialogs.js';

/**
 * Seeding and cleaning up surveys for the specs.
 *
 * Every survey a spec makes has an id starting with `e2e-` plus a per-run suffix, so responses
 * left in the dev database by earlier runs never collide. Each spec's `beforeEach` deletes the
 * files earlier tests left with {@link removeE2eSurveys}; the next reload drops them.
 */

/** dist/tests/support.js -> src/test/e2e/generated/local/run */
const RUN_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'generated', 'local', 'run');
const SURVEYS_DIR = join(RUN_DIR, 'plugins', 'Pollster', 'surveys');
export const EXPORTS_DIR = join(RUN_DIR, 'plugins', 'Pollster', 'exports');

const RUN_SUFFIX = Date.now().toString(36);
let counter = 0;

/** A survey id unique to this run: e2e-<name>-<run>-<n>. */
export function surveyId(name: string): string {
    counter++;
    return `e2e-${name}-${RUN_SUFFIX}-${counter}`;
}

export function surveyFile(id: string): string {
    return join(SURVEYS_DIR, `${id}.yml`);
}

export function readSurveyFile(id: string): string {
    return readFileSync(surveyFile(id), 'utf8');
}

/**
 * Reloads Pollster and waits until it has (or, with `loaded` false, no longer has) survey `id`.
 * The console's reload returns before the reload finishes, but `/pollster report <id>` answers
 * at once and says whether the survey exists. A reload replaces every survey at once, so one id
 * tells for all of them.
 */
async function reloadUntil(server: ServerWrapper, id: string, loaded: boolean): Promise<void> {
    await server.execute('pollster reload');
    await waitUntil(async () => {
        const reply = await server.execute(`pollster report ${id}`);
        return reply.includes(`There is no survey with the id ${id}`) !== loaded;
    }, { timeout: 5000, interval: 100, message: `survey ${id} never ${loaded ? 'loaded' : 'unloaded'}` });
}

/** Deletes every e2e survey file. The next reload drops them from Pollster. */
export function removeE2eSurveys(): void {
    mkdirSync(SURVEYS_DIR, { recursive: true });
    for (const file of readdirSync(SURVEYS_DIR)) {
        if (file.startsWith('e2e-') && file.endsWith('.yml')) {
            rmSync(join(SURVEYS_DIR, file), { force: true });
        }
    }
}

/** Deletes survey `id`'s file and reloads until Pollster no longer has it. */
export async function unloadSurvey(server: ServerWrapper, id: string): Promise<void> {
    rmSync(surveyFile(id), { force: true });
    await reloadUntil(server, id, false);
}

/** Writes a DRAFT survey file and reloads. `body` is YAML for everything after `status`. */
export async function writeDraft(server: ServerWrapper, id: string, name: string, body: string): Promise<void> {
    mkdirSync(SURVEYS_DIR, { recursive: true });
    writeFileSync(surveyFile(id), `name: "${name}"\nstatus: DRAFT\n${body.trim()}\n`);
    await reloadUntil(server, id, true);
}

/** Opens the editor at the hub of survey `id`. */
export async function openHub(admin: PlayerWrapper, id: string, name: string): Promise<Dialog> {
    admin.chat('/pollster');
    await clickOn(admin, 'Surveys', `${id} ·`);
    return waitForDialog(admin, `Survey: ${name}`);
}

/**
 * Writes a survey file, then opens it through the editor with a fresh admin bot, as an admin
 * would: hub, Open survey, Confirm. A file written as OPEN would not do: Pollster refuses
 * answers to a survey it never opened itself. Returns the survey id and the admin.
 */
export async function seedOpenSurvey({ server, createPlayer }: TestContext, slug: string, name: string,
                                     body: string): Promise<{ id: string; admin: PlayerWrapper }> {
    const id = surveyId(slug);
    await writeDraft(server, id, name, body);
    const admin = await createPlayer();
    await admin.makeOp();
    click(admin, await openHub(admin, id, name), 'Open survey');
    await clickOn(admin, 'Open survey?', 'Confirm');
    await clickOn(admin, { title: `Survey: ${name}`, body: 'Status: OPEN' }, 'Back');
    return { id, admin };
}

/** Matches a screen of survey `name` by a line of its body: `const at = screenOf('X'); at('Question 1')`. */
export function screenOf(name: string): (body: string | RegExp) => DialogMatch {
    return body => ({ title: name, body });
}

/** Opens /surveys, picks the survey labelled `label` and presses Start. Returns the intro dialog. */
export async function startSurvey(player: PlayerWrapper, name: string, label: string = name): Promise<Dialog> {
    player.chat('/surveys');
    await clickOn(player, 'Surveys', label);
    return clickOn(player, screenOf(name)('Questions:'), 'Start');
}
