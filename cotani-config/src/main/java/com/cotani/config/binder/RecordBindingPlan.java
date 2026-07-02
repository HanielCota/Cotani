package com.cotani.config.binder;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.List;

record RecordBindingPlan<T>(Class<T> type, Constructor<T> constructor, List<RecordComponent> components) {}
