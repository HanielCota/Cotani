package com.cotani.storage.serializer;

public interface ValueSerializer<T> {

    Class<T> type();

    Object serialize(T value);

    T deserialize(Object value);
}
