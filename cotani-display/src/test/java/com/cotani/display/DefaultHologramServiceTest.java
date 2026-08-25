package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cotani.display.internal.DefaultHologramService;
import com.cotani.task.api.PaperTaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class DefaultHologramServiceTest {

    @Test
    void shouldRegisterAndRemoveHolograms() {
        var scheduler = mock(PaperTaskScheduler.class);
        var service = new DefaultHologramService(scheduler);

        var holo1 = service.builder("holo_one").addLine(Component.text("First")).build();

        var holo2 =
                service.builder("holo_two").addLine(Component.text("Second")).build();

        assertEquals(2, service.all().size());
        assertTrue(service.find("HOLO_ONE").isPresent());
        assertTrue(service.find(holo1.id()).isPresent());
        assertTrue(service.find("holo_two").isPresent());
        assertTrue(service.find(holo2.id()).isPresent());

        service.removeAsync(holo1.id()).toCompletableFuture().join();
        assertEquals(1, service.all().size());
        assertFalse(service.find("holo_one").isPresent());

        service.clearAsync().toCompletableFuture().join();
        assertTrue(service.all().isEmpty());
    }
}
