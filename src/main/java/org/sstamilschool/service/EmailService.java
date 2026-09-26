package org.sstamilschool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String baseName;
    private final String from;
    private final boolean enabled;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.base-url:http://localhost:8081}") String baseName,
                        @Value("${app.mail.from:support@sstschool.org}") String from,
                        @Value("${app.mail.enabled:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.baseName = baseName;
        this.from = from;
        this.enabled = enabled;
    }

    public void sendContactMessage(String name, String fromEmail, String subject, String messageBody) {
        String recipient = "contact@sstamilschool.org";
        String fullSubject = "SSTS website contact: " + (subject == null || subject.isBlank() ? "(no subject)" : subject);
        String html = "<p><b>Name:</b> " + escape(name) + "</p>"
            + "<p><b>Email:</b> " + escape(fromEmail) + "</p>"
            + "<p><b>Subject:</b> " + escape(subject == null ? "" : subject) + "</p>"
            + "<p><b>Message:</b></p><p>" + escape(messageBody).replace("\n", "<br/>") + "</p>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setReplyTo(fromEmail);
            helper.setSubject(fullSubject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Contact form message from {} forwarded to {}.", fromEmail, recipient);
        } catch (Exception e) {
            log.warn("Failed to send contact form message to {}: {}", recipient, e.getMessage());
            throw new IllegalStateException("Contact email delivery failed", e);
        }
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void sendPasswordResetLink(String toEmail, String token) {
        if (!enabled) {
            log.info("Mail disabled; password reset link for {} not delivered (token issued).", toEmail);
            return;
        }

        String link = baseName + "/reset-password?token=" + token;
        String subject = "SSTS password reset";
        String html = "<p>Hello,</p>"
            + "<p>You requested a password reset for your Sandy Springs Tamil School account.</p>"
            + "<p><a href=\"" + link + "\">Click here to reset your password</a> (this link expires in 1 hour).</p>"
            + "<p>If you did not request this, you can safely ignore this email.</p>"
            + "<p>— Sandy Springs Tamil School</p>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Password reset link sent to {}.", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send password reset link to {}: {}", toEmail, e.getMessage());
        }
    }
}
