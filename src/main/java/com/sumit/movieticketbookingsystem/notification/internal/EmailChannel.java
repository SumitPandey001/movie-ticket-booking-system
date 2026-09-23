package com.sumit.movieticketbookingsystem.notification.internal;

import com.sumit.movieticketbookingsystem.shared.user.Recipient;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.annotation.Order;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@Order(1)
class EmailChannel implements NotificationChannel {

    private static final String FROM = "tickets@moviebooking.local";

    private final JavaMailSender mailSender;

    EmailChannel(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public boolean canReach(Recipient recipient) {
        return recipient.email() != null;
    }

    @Override
    public void send(Recipient recipient, RenderedMessage message) {
        MimeMessage mail = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mail, "UTF-8");
            helper.setFrom(FROM);
            helper.setTo(recipient.email());
            helper.setSubject(message.subject());
            helper.setText(message.body(), true);
        } catch (MessagingException e) {
            throw new MailSendException("Couldn't build the email to " + recipient.email(), e);
        }
        mailSender.send(mail);
    }
}
