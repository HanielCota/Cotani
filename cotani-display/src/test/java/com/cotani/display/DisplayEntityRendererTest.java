package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
}
