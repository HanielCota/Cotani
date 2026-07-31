package com.cotani.cache.internal.caffeine;

record SaveOrder(long generation, long version) implements Comparable<SaveOrder> {
    static final SaveOrder NONE = new SaveOrder(Long.MIN_VALUE, Long.MIN_VALUE);

    @Override
    public int compareTo(SaveOrder other) {
        int generationComparison = Long.compare(generation, other.generation);
        return generationComparison != 0 ? generationComparison : Long.compare(version, other.version);
    }
}
