import { definePlugin } from '@plugwright/runner';
import type { DialogHolder } from '../tests/dialogs.js';

/**
 * Makes plugwright's bots work against the 26.3 test server, and lets specs read dialogs.
 *
 * Every bot joins as a Minecraft 1.21.11 client. ViaVersion and ViaBackwards on the server
 * translate (see build.gradle.kts). Translated movement packets get the bot kicked ("Invalid move
 * player packet received"), so bots do not send them. Pollster's specs never need a bot to move.
 *
 * mineflayer does not track dialogs. Each `show_dialog` packet is kept on the bot as
 * `lastDialog` (the decoded NBT) with its arrival time, and `clear_dialog` empties it, so specs can wait for a dialog
 * and click its buttons (see tests/dialogs.ts).
 */
const BOT_VERSION = '1.21.11';
const MOVEMENT_PACKETS = new Set(['position', 'position_look', 'look', 'flying']);

/**
 * minecraft-data describes custom_click_action's payload as a present flag plus NBT, but the game
 * reads a varint length followed by the tag, so the stock serializer gets the bot kicked with
 * "Failed to decode packet". This redeclares the payload as a length-prefixed buffer; dialogs.ts
 * fills it with anonymous NBT. Keyed by protocol major version, as minecraft-protocol merges it.
 */
const CUSTOM_PACKETS = {
    '1.21': {
        types: {
            packet_common_custom_click_action: ['container', [
                { name: 'id', type: 'string' },
                { name: 'nbt', type: ['buffer', { countType: 'varint' }] },
            ]],
        },
    },
};

export default definePlugin({
    name: 'test-server',

    setup({ session }) {
        const createBot = session.createBot.bind(session);
        session.createBot = (options) => {
            const bot = createBot({ ...options, version: BOT_VERSION });
            // Read each time the client changes protocol state, so it is in place before play.
            (bot._client as unknown as { customPackets: unknown }).customPackets = CUSTOM_PACKETS;
            const write = bot._client.write.bind(bot._client);
            bot._client.write = (name: string, params: unknown) => {
                if (!MOVEMENT_PACKETS.has(name)) {
                    write(name, params);
                }
            };
            const holder = bot as unknown as DialogHolder;
            holder.lastDialog = null;
            holder.lastDialogAt = 0;
            bot._client.on('show_dialog', (packet: { dialog: DialogHolder['lastDialog'] }) => {
                holder.lastDialog = packet.dialog;
                holder.lastDialogAt = Date.now();
            });
            bot._client.on('clear_dialog', () => {
                holder.lastDialog = null;
            });
            return bot;
        };
    },
});
