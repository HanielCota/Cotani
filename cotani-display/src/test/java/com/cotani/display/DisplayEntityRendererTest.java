package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cotani.display.api.TextLine;
import com.cotani.display.impl.DisplayEntityRenderer;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class DisplayEntityRendererTest {

    @Test
    void shouldReturnEmptyWhenWorldIsNull() {
        var renderer = new DisplayEntityRenderer();
        var location = mock(Location.class); // getWorld() returns null

        var result = renderer.spawn(location, List.of(), 0.28, false);
        assertTrue(result.lineEntityIds().isEmpty());
        assertNull(result.interactionEntityId());
        assertTrue(result.allEntityIds().isEmpty());
    }

    @Test
    void shouldBuildScaleTransformationProperly() {
        var transformation = DisplayEntityRenderer.scaleTransformation(1.5f);
        assertNotNull(transformation);
        assertEquals(1.5f, transformation.getScale().x, 0.001f);
        assertEquals(1.5f, transformation.getScale().y, 0.001f);
        assertEquals(1.5f, transformation.getScale().z, 0.001f);
    }

    @Test
    void shouldApplyTextDisplayPropertiesCorrectly() {
        var renderer = new DisplayEntityRenderer();
        var textDisplay = mock(org.bukkit.entity.TextDisplay.class);
        var textLine = TextLine.of(net.kyori.adventure.text.Component.text("Hello"))
                .withBillboard(com.cotani.display.api.DisplayBillboard.VERTICAL)
                .withOpacity((byte) 128)
                .withBackground(org.bukkit.Color.BLUE)
                .withScale(2.0f);

        renderer.applyLineProperties(textDisplay, textLine);

        org.mockito.Mockito.verify(textDisplay).text(textLine.text());
        org.mockito.Mockito.verify(textDisplay).setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
        org.mockito.Mockito.verify(textDisplay).setTextOpacity((byte) 128);
        org.mockito.Mockito.verify(textDisplay).setBackgroundColor(org.bukkit.Color.BLUE);
        org.mockito.Mockito.verify(textDisplay).setTransformation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldCheckEntityCompatibilityCorrectly() {
        var renderer = new DisplayEntityRenderer();
        var textDisplay = mock(org.bukkit.entity.TextDisplay.class);
        var itemDisplay = mock(org.bukkit.entity.ItemDisplay.class);
        var blockDisplay = mock(org.bukkit.entity.BlockDisplay.class);

        var textLine = TextLine.of(net.kyori.adventure.text.Component.text("Test"));
        var itemLine = com.cotani.display.api.ItemLine.of(mock(org.bukkit.inventory.ItemStack.class));
        var blockLine = com.cotani.display.api.BlockLine.of(mock(org.bukkit.block.data.BlockData.class));

        assertTrue(renderer.isEntityCompatible(textDisplay, textLine));
        org.junit.jupiter.api.Assertions.assertFalse(renderer.isEntityCompatible(textDisplay, itemLine));
        org.junit.jupiter.api.Assertions.assertFalse(renderer.isEntityCompatible(textDisplay, blockLine));

        assertTrue(renderer.isEntityCompatible(itemDisplay, itemLine));
        org.junit.jupiter.api.Assertions.assertFalse(renderer.isEntityCompatible(itemDisplay, textLine));

        assertTrue(renderer.isEntityCompatible(blockDisplay, blockLine));
        org.junit.jupiter.api.Assertions.assertFalse(renderer.isEntityCompatible(blockDisplay, textLine));
    }
}
