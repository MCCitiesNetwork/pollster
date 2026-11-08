package com.minecraftcitiesnetwork.pollster.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DialogRendererTest {

    @Test
    void dialogKey_neverEqualsThePayloadKeyOfPaperClickCallbacks() {
        // Paper keeps each button's callback id under "id"; an input sent as "id" would overwrite it.
        assertNotEquals("id", DialogRenderer.dialogKey("id"));
    }
}
