package com.minibank.notification.sms;

import com.minibank.notification.config.NotificationProperties;
import com.minibank.verification.service.PhoneNumbers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends SMS through Twilio's REST API.
 *
 * <p>Deliberately built on {@link RestClient} and the documented HTTP endpoint rather than the
 * vendor SDK. The call is one form POST; pulling in an SDK to make it would add a dependency whose
 * transitive tree is larger than this entire module, and it would put a vendor type into the
 * application's classpath that the {@link SmsService} interface exists to keep out.
 *
 * <p>Active only when {@code minibank.notifications.sms.provider=twilio}. Credentials come from the
 * environment; none of them appear in this repository.
 */
@Service
@ConditionalOnProperty(name = "minibank.notifications.sms.provider", havingValue = "twilio")
public final class TwilioSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsService.class);
    private static final String API_ROOT = "https://api.twilio.com/2010-04-01";

    private final RestClient restClient;
    private final NotificationProperties properties;

    public TwilioSmsService(NotificationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;

        NotificationProperties.Sms sms = properties.sms();
        if (sms.accountSid().isBlank() || sms.authToken().isBlank()) {
            throw new IllegalStateException(
                    "minibank.notifications.sms.provider is 'twilio' but the account SID or auth token is "
                            + "missing. Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN, or switch the provider "
                            + "back to 'dev'.");
        }

        String credentials = Base64.getEncoder()
                .encodeToString("%s:%s".formatted(sms.accountSid(), sms.authToken()).getBytes(StandardCharsets.UTF_8));
        this.restClient = restClientBuilder
                .baseUrl("%s/Accounts/%s".formatted(API_ROOT, sms.accountSid()))
                .defaultHeader("Authorization", "Basic " + credentials)
                .build();
    }

    @Override
    public void send(String toE164, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164);
        form.add("From", properties.sms().from());
        form.add("Body", message);

        try {
            restClient
                    .post()
                    .uri("/Messages.json")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            // The destination is masked and the body is omitted: this line exists to prove delivery
            // was attempted, not to reproduce the code in the log.
            log.info("SMS accepted by Twilio for {}", PhoneNumbers.mask(toE164));
        } catch (RestClientException ex) {
            // The provider's own message can echo the request back, body included, so it is not
            // propagated into ours.
            log.error("Twilio rejected a message for {}", PhoneNumbers.mask(toE164));
            throw new SmsDeliveryException("The SMS provider did not accept the message", ex);
        }
    }

    @Override
    public String providerName() {
        return "twilio";
    }
}
