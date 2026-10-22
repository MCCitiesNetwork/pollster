package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.config.ConfigLoader;
import com.minecraftcitiesnetwork.pollster.config.Messages;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.logging.Logger;

/** The shipped {@code messages.yml}, so screen tests see the same wording a server owner sees. */
final class BundledMessages {

    private BundledMessages() {
    }

    static Messages load() {
        try {
            return new ConfigLoader(Files.createTempDirectory("pollster-messages"),
                    BundledMessages.class.getClassLoader(), Logger.getAnonymousLogger()).loadMessages();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
