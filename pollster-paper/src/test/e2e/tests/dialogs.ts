import { poll, sleep, type PlayerWrapper } from '@plugwright/runner';
import nbt from 'prismarine-nbt';

/**
 * Reading and clicking Paper dialogs from a bot.
 *
 * plugins/test-server.ts keeps the last `show_dialog` packet on each bot. A button click is sent
 * the way a real client sends it: a `custom_click_action` packet carrying the button's id and a
 * payload of every input's value merged with the button's `additions`.
 */

type RawNbt = { type: string; value: any };

/**
 * Pollster's DialogRenderer sends every input key with this prefix. Specs use the keys as the
 * plugin's screens name them, so the prefix is stripped when a dialog is read.
 */
const INPUT_KEY_PREFIX = 'in_';

/** What plugins/test-server.ts keeps on each bot. */
export interface DialogHolder {
    lastDialog: { data: RawNbt } | null;
    lastDialogAt: number;
}

export interface DialogInput {
    /** The key as Pollster's screen names it. */
    key: string;
    /** The key the client sends the value under. */
    wireKey: string;
    type: string;
    label: string;
    /** The value the client would send if the player changed nothing. */
    initial: string | boolean | number;
    /** single_option inputs: the option ids and their display text. */
    options: { id: string; display: string }[];
}

export interface DialogButton {
    label: string;
    id: string;
    additions: RawNbt | null;
}

export interface Dialog {
    /** The dialog type without its namespace, such as multi_action or notice. */
    type: string;
    title: string;
    body: string[];
    buttons: DialogButton[];
    inputs: DialogInput[];
    /** Title, body and button labels, one per line, for failure messages. */
    describe(): string;
}

function holder(player: PlayerWrapper): DialogHolder {
    return player.bot as unknown as DialogHolder;
}

/** The plain text of a chat component, as simplified NBT: a string, a compound or a list. */
function plainText(component: unknown): string {
    if (component == null) {
        return '';
    }
    if (typeof component === 'string') {
        return component;
    }
    if (Array.isArray(component)) {
        return component.map(plainText).join('');
    }
    if (typeof component === 'object') {
        const c = component as { text?: string; ''?: string; extra?: unknown[]; translate?: string };
        const own = c.text ?? c[''] ?? c.translate ?? '';
        return own + (c.extra ?? []).map(plainText).join('');
    }
    return String(component);
}

function parse(raw: RawNbt): Dialog {
    const simple = nbt.simplify(raw as any) as any;
    const rawActions: RawNbt[] = raw.value.actions?.value?.value?.map((v: any) => ({ type: 'compound', value: v })) ?? [];
    const actions: any[] = simple.actions ?? (simple.action ? [simple.action] : []);
    const buttons: DialogButton[] = actions.map((a, i) => ({
        label: plainText(a.label),
        id: a.action?.id ?? '',
        additions: rawActions[i]?.value.action?.value.additions ?? null,
    }));
    const bodyList = simple.body == null ? [] : Array.isArray(simple.body) ? simple.body : [simple.body];
    const inputs: DialogInput[] = (simple.inputs ?? []).map((input: any) => {
        const type = String(input.type).replace('minecraft:', '');
        const options = (input.options ?? []).map((o: any) => ({ id: o.id, display: plainText(o.display) }));
        let initial: string | boolean | number;
        switch (type) {
            case 'boolean':
                initial = input.initial === 1 || input.initial === true;
                break;
            case 'number_range':
                initial = input.initial ?? input.start ?? 0;
                break;
            case 'single_option':
                initial = (input.options ?? []).find((o: any) => o.initial === 1)?.id ?? options[0]?.id ?? '';
                break;
            default:
                initial = input.initial ?? '';
        }
        const wireKey: string = input.key;
        const key = wireKey.startsWith(INPUT_KEY_PREFIX) ? wireKey.slice(INPUT_KEY_PREFIX.length) : wireKey;
        return { key, wireKey, type, label: plainText(input.label), initial, options };
    });
    const dialog: Dialog = {
        type: String(simple.type).replace('minecraft:', ''),
        title: plainText(simple.title),
        body: bodyList.map((b: any) => plainText(b.contents)),
        buttons,
        inputs,
        describe: () => [
            `title: ${dialog.title}`,
            ...dialog.body.map(b => `body: ${b}`),
            ...dialog.inputs.map(i => `input ${i.key} (${i.type}): ${i.label} = ${JSON.stringify(i.initial)}`),
            ...dialog.buttons.map(b => `button: ${b.label}`),
        ].join('\n'),
    };
    return dialog;
}

/** The dialog the bot has open now, or null. */
export function currentDialog(player: PlayerWrapper): Dialog | null {
    const last = holder(player).lastDialog;
    return last == null ? null : parse(last.data);
}

/** What a dialog must show: its title contains a string or matches a pattern, and optionally its body. */
export type DialogMatch = string | RegExp | { title?: string | RegExp; body?: string | RegExp };

function textMatches(text: string, pattern: string | RegExp | undefined): boolean {
    if (pattern === undefined) {
        return true;
    }
    return typeof pattern === 'string' ? text.includes(pattern) : pattern.test(text);
}

function normalize(match: DialogMatch): { title?: string | RegExp; body?: string | RegExp } {
    return typeof match === 'string' || match instanceof RegExp ? { title: match } : match;
}

function dialogMatches(dialog: Dialog, match: DialogMatch): boolean {
    const { title, body } = normalize(match);
    return textMatches(dialog.title, title) && textMatches(bodyText(dialog), body);
}

/**
 * Paper registers a dialog's button callbacks on the server tick after it sends the dialog, and
 * silently drops a click whose callback it has not registered yet. A person never clicks that
 * fast; a bot does. Three ticks is enough.
 */
const CALLBACK_SETTLE_MS = 150;

/**
 * Waits until the bot has a dialog open that matches, and returns it once its buttons can be
 * clicked (see {@link CALLBACK_SETTLE_MS}).
 */
export async function waitForDialog(player: PlayerWrapper, match: DialogMatch, timeout = 5000): Promise<Dialog> {
    try {
        const dialog = await poll(() => {
            const open = currentDialog(player);
            return open != null && !isLoading(open) && dialogMatches(open, match) ? open : undefined;
        }, { timeout, message: 'no matching dialog' });
        const age = Date.now() - holder(player).lastDialogAt;
        if (age < CALLBACK_SETTLE_MS) {
            await sleep(CALLBACK_SETTLE_MS - age);
        }
        return dialog;
    } catch {
        const open = currentDialog(player);
        throw new Error(`${player.username} never saw a dialog matching ${describeMatch(match)}. Open dialog:\n`
            + (open ? open.describe() : '(none)'));
    }
}

/**
 * Pollster's loading screen is its only screen without buttons, which DialogRenderer sends as a
 * notice with no action. It carries the title of the screen being fetched, so waits skip it
 * rather than match it by title.
 */
function isLoading(dialog: Dialog): boolean {
    return dialog.type === 'notice' && dialog.buttons.length === 0;
}

function describeMatch(match: DialogMatch): string {
    const { title, body } = normalize(match);
    return `title ${title ?? '(any)'} and body ${body ?? '(any)'}`;
}

/** The body lines joined with newlines, for `toContain` assertions. */
export function bodyText(dialog: Dialog): string {
    return dialog.body.join('\n');
}

function toNbt(input: DialogInput, value: string | boolean | number): RawNbt {
    switch (input.type) {
        case 'boolean':
            return { type: 'byte', value: value ? 1 : 0 };
        case 'number_range':
            return { type: 'float', value: Number(value) };
        default:
            return { type: 'string', value: String(value) };
    }
}

/**
 * Clicks the button labelled `label` (or, failing that, containing it or matching a pattern), sending every input with its
 * initial value unless `values` overrides it. The open dialog is forgotten first, so the next
 * {@link waitForDialog} waits for whatever the server shows in reply.
 */
export function click(player: PlayerWrapper, dialog: Dialog, label: string | RegExp,
                      values: Record<string, string | boolean | number> = {}): void {
    // A string label prefers an exact match, so "Delete" never picks the hub's "Name Delete me".
    const button = typeof label === 'string'
        ? dialog.buttons.find(b => b.label === label) ?? dialog.buttons.find(b => b.label.includes(label))
        : dialog.buttons.find(b => label.test(b.label));
    if (button == null) {
        throw new Error(`No button ${label} on the dialog:\n${dialog.describe()}`);
    }
    for (const key of Object.keys(values)) {
        if (!dialog.inputs.some(i => i.key === key)) {
            throw new Error(`No input ${key} on the dialog:\n${dialog.describe()}`);
        }
    }
    // The vanilla client copies the button's additions first and then writes every input over
    // them (CustomAll.createAction), so an input whose key matches an addition replaces it.
    const payload: Record<string, RawNbt> = { ...(button.additions?.value ?? {}) };
    for (const input of dialog.inputs) {
        payload[input.wireKey] = toNbt(input, values[input.key] ?? input.initial);
    }
    holder(player).lastDialog = null;
    // plugins/test-server.ts declares the payload as a length-prefixed buffer; see there.
    const anonymous = nbt.protos.big.createPacketBuffer('anonymousNbt', { type: 'compound', name: '', value: payload });
    player.bot._client.write('custom_click_action', { id: button.id, nbt: anonymous });
}

/** Waits for a matching dialog, then clicks `label` on it. Returns the dialog clicked. */
export async function clickOn(player: PlayerWrapper, match: DialogMatch, label: string | RegExp,
                              values: Record<string, string | boolean | number> = {}): Promise<Dialog> {
    const dialog = await waitForDialog(player, match);
    click(player, dialog, label, values);
    return dialog;
}
