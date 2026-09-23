package com.sumit.movieticketbookingsystem.notification.internal;

import com.sumit.movieticketbookingsystem.shared.user.Recipient;

/** A way of reaching a customer. A new channel is one more implementation; nothing else changes. */
interface NotificationChannel {

    Channel channel();

    boolean canReach(Recipient recipient);

    void send(Recipient recipient, RenderedMessage message);
}
