package com.notificationplatform.exception;

public class ChannelDeliveryException extends NotificationException {

    public ChannelDeliveryException(String message) {
        super(message);
    }

    public ChannelDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
