package com.cotani.config.contract;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.config.ConfigBinderView;
import com.cotani.config.ConfigReader;
import com.cotani.config.ConfigValidationView;
import com.cotani.config.ConfigWriter;
import com.cotani.config.CotaniConfig;
import com.cotani.config.ReloadableConfig;
import com.cotani.config.binder.ConfigBinder;
import com.cotani.config.impl.DefaultCotaniConfig;
import com.cotani.config.impl.DefaultCotaniConfigs;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.source.ConfigSource;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChainFactory;
import java.nio.file.Path;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConfigCapabilityInterfacesTest {

    @Test
    void defaultConfigPreservesBehaviorThroughNarrowCapabilities() {
        ConfigSource source = Mockito.mock(ConfigSource.class);
        when(source.path()).thenReturn(Path.of("config.yml"));
        when(source.contains("feature.enabled")).thenReturn(true);
        ConfigSerializerRegistry serializers = Mockito.mock(ConfigSerializerRegistry.class);
        when(serializers.serialize(true)).thenReturn(true);
        ConfigBinder binder = Mockito.mock(ConfigBinder.class);
        TaskChainFactory chains = Mockito.mock(TaskChainFactory.class);
        CotaniConfig config = DefaultCotaniConfig.create("config.yml", source, serializers, binder, chains);

        ConfigReader reader = config;
        ConfigWriter writer = config;
        ConfigBinderView binderView = config;
        ConfigValidationView validationView = config;
        ReloadableConfig reloadable = config;

        assertTrue(reader.contains("feature.enabled"));
        writer.set("feature.enabled", true);
        reloadable.reload();
        assertSame(config, binderView);
        assertSame(config, validationView);
        verify(source).set("feature.enabled", true);
        verify(source).load();
    }

    @Test
    void legacySchedulerFactoryMethodRemainsAvailable() throws NoSuchMethodException {
        assertNotNull(DefaultCotaniConfig.class.getMethod(
                "create",
                String.class,
                ConfigSource.class,
                ConfigSerializerRegistry.class,
                ConfigBinder.class,
                PaperTaskScheduler.class));
        assertNotNull(DefaultCotaniConfigs.class.getMethod(
                "create",
                Plugin.class,
                Path.class,
                PaperTaskScheduler.class,
                ConfigSerializerRegistry.class,
                boolean.class,
                boolean.class));
    }
}
