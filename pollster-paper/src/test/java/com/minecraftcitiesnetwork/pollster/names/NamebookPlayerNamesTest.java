package com.minecraftcitiesnetwork.pollster.names;

import com.minecraftcitiesnetwork.namebook.api.Namebook;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NamebookPlayerNamesTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void namebook_namesPassThrough() {
        Namebook namebook = mock(Namebook.class);
        Map<UUID, Optional<String>> answer = Map.of(this.a, Optional.of("Alice"), this.b, Optional.empty());
        when(namebook.namesOf(any())).thenReturn(CompletableFuture.completedFuture(answer));

        assertEquals(answer, new NamebookPlayerNames(namebook).namesOf(List.of(this.a, this.b)).join());
    }

    @Test
    void namebook_failureBecomesEmpty() {
        Namebook namebook = mock(Namebook.class);
        when(namebook.namesOf(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));

        assertEquals(Map.of(this.a, Optional.empty(), this.b, Optional.empty()),
                new NamebookPlayerNames(namebook).namesOf(List.of(this.a, this.b)).join());
    }
}
