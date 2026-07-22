package com.notificationplatform.util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class IdGenerator {

    private final Supplier<String> supplier;

    public IdGenerator() {
        this(() -> UUID.randomUUID().toString());
    }

    public IdGenerator(Supplier<String> supplier) {
        this.supplier = supplier;
    }

    public String nextId() {
        return supplier.get();
    }

    public static IdGenerator sequential(String prefix) {
        AtomicLong counter = new AtomicLong(1);
        return new IdGenerator(() -> prefix + "-" + counter.getAndIncrement());
    }
}
