package com.sumit.movieticketbookingsystem.notification.internal;

import com.sumit.movieticketbookingsystem.shared.user.Recipient;
import com.sumit.movieticketbookingsystem.shared.user.UserDirectory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
class NotificationService {

    private final UserDirectory users;
    private final List<NotificationChannel> channels;
    private final TemplateRenderer renderer;
    private final NotificationLogRepository logs;

    NotificationService(UserDirectory users, List<NotificationChannel> channels, TemplateRenderer renderer,
            NotificationLogRepository logs) {
        this.users = users;
        this.channels = channels;
        this.renderer = renderer;
        this.logs = logs;
    }

    /**
     * Sends the message on every channel that can reach the user, once per channel however often it's called.
     * A failed send is rethrown so the event stays incomplete and is delivered again; channels that already went
     * out are skipped on that retry. referenceId tells apart messages of the same type on one booking (the refund
     * id) and is empty when there's only ever one.
     */
    void notify(NotificationType type, UUID bookingId, String referenceId, UUID userId, Map<String, Object> model) {
        Optional<Recipient> found = users.recipient(userId);
        if (found.isEmpty()) {
            return;                                                // never called the API, so nowhere to send
        }
        Recipient recipient = found.get();
        Map<String, Object> withName = new HashMap<>(model);
        withName.put("name", recipient.name());

        for (NotificationChannel channel : channels) {
            if (!channel.canReach(recipient)) {
                continue;
            }
            Optional<Long> claim = logs.claim(bookingId, type, referenceId, channel.channel());
            if (claim.isEmpty()) {
                continue;                                          // already sent: a duplicate event
            }
            try {
                channel.send(recipient, renderer.render(type, channel.channel(), withName));
                logs.markSent(claim.get());
            } catch (RuntimeException e) {
                logs.markFailed(claim.get(), e);
                throw e;
            }
        }
    }
}
