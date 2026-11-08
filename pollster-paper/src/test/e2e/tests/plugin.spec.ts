import { expect, test, waitForStable } from '@plugwright/runner';
import { currentDialog, waitForDialog } from './dialogs.js';

test('Pollster enables on Paper 26.3', async ({ server }) => {
    const plugins = await server.execute('plugins');
    expect(plugins).toContain('Pollster');
});

test('/pollster reload reports how many surveys it loaded', async ({ player }) => {
    await player.makeOp();
    player.chat('/pollster reload');
    await expect(player).toHaveReceivedMessage(/\[Pollster\] Reloaded \d+ surveys\./);
});

test('/surveys from the console says surveys open as a dialog', async ({ server }) => {
    const reply = await server.execute('surveys');
    expect(reply).toContain('Surveys open as a dialog, so only players can use this command.');
});

test('/surveys opens the survey list for any player', async ({ player }) => {
    player.chat('/surveys');
    const list = await waitForDialog(player, 'Surveys');
    expect(list.buttons.map(b => b.label)).toContain('Close');
});

test('a player without pollster.admin cannot open the editor', async ({ player }) => {
    await player.deOp();
    player.chat('/pollster');
    await waitForStable(() => currentDialog(player) == null, { duration: 1000 });
});
