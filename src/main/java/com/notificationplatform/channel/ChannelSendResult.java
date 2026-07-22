package com.notificationplatform.channel;

import java.util.Objects;

public final class ChannelSendResult {

    private final boolean success;
    private final String providerMessageId;
    private final String errorMessage;

    private ChannelSendResult(boolean success, String providerMessageId, String errorMessage) {
        this.success = success;
        this.providerMessageId = providerMessageId;
        this.errorMessage = errorMessage;
    }

    public static ChannelSendResult success(String providerMessageId) {
        return new ChannelSendResult(true, Objects.requireNonNull(providerMessageId, "providerMessageId"), null);
    }

    public static ChannelSendResult failure(String errorMessage) {
        return new ChannelSendResult(false, null, Objects.requireNonNull(errorMessage, "errorMessage"));
    }

    public boolean success() {
        return success;
    }

    public String providerMessageId() {
        return providerMessageId;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
