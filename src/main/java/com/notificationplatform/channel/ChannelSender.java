package com.notificationplatform.channel;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Notification;

public interface ChannelSender {

    ChannelType channel();

    ChannelSendResult send(Notification notification);
}
