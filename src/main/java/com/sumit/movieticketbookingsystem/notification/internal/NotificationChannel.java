package com.sumit.movieticketbookingsystem.notification.internal;

import com.sumit.movieticketbookingsystem.shared.user.Recipient;

/** A way of reaching a customer, such as email or SMS. */
interface NotificationChannel {

    Channel channel();

    boolean canReach(Recipient recipient);

    void send(Recipient recipient, RenderedMessage message);
}
