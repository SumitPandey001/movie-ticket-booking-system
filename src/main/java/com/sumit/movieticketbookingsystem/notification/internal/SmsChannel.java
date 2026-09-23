package com.sumit.movieticketbookingsystem.notification.internal;

import com.sumit.movieticketbookingsystem.shared.user.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Stands in for an SMS gateway: the text is only logged. */
@Component
@Order(2)
class SmsChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public boolean canReach(Recipient recipient) {
        return recipient.phone() != null;
    }

    @Override
    public void send(Recipient recipient, RenderedMessage message) {
        log.info("SMS to {}: {}", recipient.phone(), message.body());
    }
}
