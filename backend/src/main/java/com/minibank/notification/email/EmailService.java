package com.minibank.notification.email;

/**
 * Sends an email.
 *
 * <p>Same contract as {@link com.minibank.notification.sms.SmsService} and for the same reason: the
 * verification flow should not know or care whether the message leaves through SMTP, a hosted API,
 * or nothing at all. Which of those it is stays a configuration question.
 */
public interface EmailService {

    /**
     * Delivers a message.
     *
     * @param to recipient address
     * @param subject the subject line
     * @param textBody plain text alternative, for clients that will not render HTML
     * @param htmlBody the rendered version
     * @throws EmailDeliveryException if the message could not be handed off
     */
    void send(String to, String subject, String textBody, String htmlBody);

    String providerName();

    /** True when nothing is actually delivered. */
    default boolean isSimulated() {
        return false;
    }

    /** Raised when a message cannot be handed to the provider. */
    class EmailDeliveryException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
