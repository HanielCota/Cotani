package com.cotani.task.api;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public sealed interface ExecutionTarget
        permits ExecutionTarget.Async, ExecutionTarget.Global, ExecutionTarget.Region, ExecutionTarget.EntityTarget {

    Async ASYNC = new Async();
    Global GLOBAL = new Global();

    record Async() implements ExecutionTarget {}

    record Global() implements ExecutionTarget {}

    record Region(Location location) implements ExecutionTarget {
        public Region {
            Objects.requireNonNull(location, "location");
        }
    }

    record EntityTarget(Entity entity) implements ExecutionTarget {
        public EntityTarget {
            Objects.requireNonNull(entity, "entity");
        }
    }

    static ExecutionTarget async() {
        return ASYNC;
    }

    static ExecutionTarget global() {
        return GLOBAL;
    }

    static ExecutionTarget region(Location location) {
        return new Region(location);
    }

    static ExecutionTarget entity(Entity entity) {
        return new EntityTarget(entity);
    }
}
