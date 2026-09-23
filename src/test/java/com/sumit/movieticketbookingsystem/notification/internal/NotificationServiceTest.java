package com.sumit.movieticketbookingsystem.notification.internal;

import com.sumit.movieticketbookingsystem.shared.user.Recipient;
import com.sumit.movieticketbookingsystem.shared.user.UserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID BOOKING = UUID.randomUUID();
    private static final Map<String, Object> MODEL = Map.of("bookingRef", "BK000001");

    private final UserDirectory users = mock(UserDirectory.class);
    private final TemplateRenderer renderer = mock(TemplateRenderer.class);
    private final NotificationLogRepository logs = mock(NotificationLogRepository.class);
    private final FakeChannel email = new FakeChannel(Channel.EMAIL);
    private final FakeChannel sms = new FakeChannel(Channel.SMS);

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(users, List.of(email, sms), renderer, logs);
        when(users.recipient(USER)).thenReturn(Optional.of(new Recipient("Asha", "asha@example.com", "+91980")));
        when(renderer.render(any(), any(), any())).thenReturn(new RenderedMessage("subject", "body"));
    }

    @Test
    void aChannelThatAlreadyWentOutIsSkipped() {
        when(logs.claim(BOOKING, NotificationType.BOOKING_CONFIRMED, "", Channel.EMAIL)).thenReturn(Optional.empty());
        when(logs.claim(BOOKING, NotificationType.BOOKING_CONFIRMED, "", Channel.SMS)).thenReturn(Optional.of(7L));

        service.notify(NotificationType.BOOKING_CONFIRMED, BOOKING, "", USER, MODEL);

        assertThat(email.sent).isEmpty();
        assertThat(sms.sent).hasSize(1);
        verify(logs).markSent(7L);
    }

    @Test
    void aFailedSendIsRecordedAndRethrownSoTheEventIsRetried() {
        when(logs.claim(eq(BOOKING), any(), anyString(), eq(Channel.EMAIL))).thenReturn(Optional.of(3L));
        email.failWith = new MailSendException("connection refused");

        assertThatThrownBy(() -> service.notify(NotificationType.BOOKING_CONFIRMED, BOOKING, "", USER, MODEL))
                .isSameAs(email.failWith);
        verify(logs).markFailed(3L, email.failWith);
        verify(logs, never()).markSent(3L);
    }

    @Test
    void aUserWhoNeverCalledTheApiGetsNothing() {
        when(users.recipient(USER)).thenReturn(Optional.empty());

        service.notify(NotificationType.BOOKING_CONFIRMED, BOOKING, "", USER, MODEL);

        verify(logs, never()).claim(any(), any(), any(), any());
    }

    private static final class FakeChannel implements NotificationChannel {

        private final Channel channel;
        private final List<RenderedMessage> sent = new ArrayList<>();
        private RuntimeException failWith;

        private FakeChannel(Channel channel) {
            this.channel = channel;
        }

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public boolean canReach(Recipient recipient) {
            return true;
        }

        @Override
        public void send(Recipient recipient, RenderedMessage message) {
            if (failWith != null) {
                throw failWith;
            }
            sent.add(message);
        }
    }
}
