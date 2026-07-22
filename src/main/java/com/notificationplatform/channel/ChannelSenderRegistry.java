package com.notificationplatform.channel;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.exception.ChannelDeliveryException;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ChannelSenderRegistry {

    private final Map<ChannelType, ChannelSender> senders = new EnumMap<>(ChannelType.class);

    public ChannelSenderRegistry(Collection<ChannelSender> channelSenders) {
        Objects.requireNonNull(channelSenders, "channelSenders");
        for (ChannelSender sender : channelSenders) {
            ChannelSender previous = senders.put(sender.channel(), sender);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate channel sender for " + sender.channel());
            }
        }
    }

    public ChannelSender get(ChannelType channelType) {
        ChannelSender sender = senders.get(channelType);
        if (sender == null) {
            throw new ChannelDeliveryException("No channel sender registered for " + channelType);
        }
        return sender;
    }

    public boolean supports(ChannelType channelType) {
        return senders.containsKey(channelType);
    }
}
