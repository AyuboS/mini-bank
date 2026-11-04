package com.minibank.support;

import com.minibank.notification.email.EmailService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Captures outgoing email so a test can follow a verification link.
 *
 * <p>The token only ever exists in the message body — the database stores a hash of it — so
 * intercepting the send is the only way to exercise the flow the way a customer experiences it.
 * Reaching into the repository and forging a token would test the assertion, not the feature.
 *
 * <p>{@code @Primary} so it wins over {@code DevEmailService}, which is also present under the test
 * profile. Being a plain component in the shared test context means it costs no extra Spring
 * context.
 */
@Component
@Primary
public class RecordingEmailService implements EmailService {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("verify-email\\?token=([A-Za-z0-9_%-]+)");

    private final List<SentEmail> sent = new ArrayList<>();

    @Override
    public synchronized void send(String to, String subject, String textBody, String htmlBody) {
        sent.add(new SentEmail(to, subject, textBody, htmlBody));
    }

    @Override
    public String providerName() {
        return "recording";
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    public synchronized List<SentEmail> sent() {
        return List.copyOf(sent);
    }

    public synchronized Optional<SentEmail> lastTo(String recipient) {
        return sent.stream().filter(email -> email.to().equalsIgnoreCase(recipient)).reduce((a, b) -> b);
    }

    /** The token out of the most recent verification link sent to an address. */
    public Optional<String> lastTokenFor(String recipient) {
        return lastTo(recipient).flatMap(email -> {
            Matcher matcher = TOKEN_IN_LINK.matcher(email.textBody());
            return matcher.find()
                    ? Optional.of(java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8))
                    : Optional.empty();
        });
    }

    public synchronized void clear() {
        sent.clear();
    }

    public record SentEmail(String to, String subject, String textBody, String htmlBody) {}
}
