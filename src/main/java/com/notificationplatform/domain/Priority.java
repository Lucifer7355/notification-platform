package com.notificationplatform.domain;

public enum Priority {
    CRITICAL(100),
    HIGH(75),
    NORMAL(50),
    LOW(25);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
