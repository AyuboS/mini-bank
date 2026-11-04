package com.minibank.notification.sms;

/**
 * Sends an SMS.
 *
 * <p>The interface exists so that no caller anywhere knows which provider is in use. Swapping
 * Twilio for another vendor, or for the development stub, is a configuration change and a new
 * implementation of this one method — not an edit to the verification flow.
 *
 * <p>Implementations must never log the message body: for this application every SMS it sends
 * contains a one-time code.
 */
public interface SmsService {

    /**
     * Delivers a message.
     *
     * @param toE164 destination in E.164 form
     * @param message the body; contains a secret and must not be logged
     * @throws SmsDeliveryException if the provider rejected or could not accept the message
     */
    void send(String toE164, String message);

    /** Which provider this is, for logs and for the health of an operator's understanding. */
    String providerName();

    /**
     * True when messages are not actually delivered anywhere.
     *
     * <p>The verification flow reads this to decide whether it may hand the code back through the
     * API, which is only ever safe when nothing was really sent.
     */
    default boolean isSimulated() {
        return false;
    }

    /** Raised when a provider will not take the message. */
    class SmsDeliveryException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SmsDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }

        public SmsDeliveryException(String message) {
            super(message);
        }
    }
}
