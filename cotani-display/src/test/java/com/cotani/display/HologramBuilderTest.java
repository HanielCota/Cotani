package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cotani.display.api.DisplayBillboard;
import com.cotani.display.api.TextLine;
import com.cotani.display.impl.DefaultHologramService;
import com.cotani.task.api.PaperTaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class HologramBuilderTest {

    @Test
    void shouldBuildHologramWithMultipleLines() {
        var scheduler = mock(PaperTaskScheduler.class);
        var service = new DefaultHologramService(scheduler);

        var hologram = service.builder("spawn_holo")
                .billboard(DisplayBillboard.VERTICAL)
                .lineSpacing(0.35)
                .addLine(Component.text("Line 1"))
                .addLine("<green>Line 2</green>")
                .clickable(true)
                .onClick((player, h, click) -> {})
                .build();

        assertNotNull(hologram.id());
        assertEquals("spawn_holo", hologram.name().orElseThrow());
        assertEquals(2, hologram.lineCount());
        assertTrue(hologram.clickHandler().isPresent());

        var firstLine = (TextLine) hologram.lines().get(0);
        assertEquals(Component.text("Line 1"), firstLine.text());

        // Verify registration in service
        assertTrue(service.find("spawn_holo").isPresent());
        assertTrue(service.find(hologram.id()).isPresent());
    }
}
