package com.minibank.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Turns the current HTTP request into the two facts a session list needs: a device label and an IP.
 *
 * <h2>Why the user agent is reduced</h2>
 *
 * <p>The raw {@code User-Agent} header is a long, high-entropy fingerprinting surface, and storing
 * it verbatim would mean every session row carries a string that can be used to correlate the
 * customer across services. What a customer actually needs from the security page is "Chrome on
 * macOS" — enough to recognise their own laptop and spot one that is not theirs. That is what is
 * kept, and the rest is discarded at the door rather than stored and regretted.
 *
 * <p>The match is deliberately coarse and ordered: Edge and Opera both claim to be Chrome, and
 * Chrome claims to be Safari, so the more specific names are tested first.
 */
public final class DeviceFingerprint {

    /** Column width in the migration. Labels are short by construction, but never trust that. */
    private static final int MAX_LABEL = 80;

    private DeviceFingerprint() {}

    /** A label such as {@code "Chrome on macOS"}, or null when there is no request to read. */
    public static String currentDeviceLabel() {
        return currentRequest()
                .map(request -> request.getHeader("User-Agent"))
                .map(DeviceFingerprint::describe)
                .orElse(null);
    }

    /**
     * The client's address.
     *
     * <p>The socket address, not {@code X-Forwarded-For}: a client can set that header to anything,
     * and a session list that can be made to show a chosen city is worse than one showing none.
     * Behind a trusted proxy this should be resolved by a {@code ForwardedHeaderFilter} configured
     * with that proxy, which is a deployment decision rather than something to guess at here.
     */
    public static String currentIpAddress() {
        return currentRequest().map(HttpServletRequest::getRemoteAddr).orElse(null);
    }

    /** Reduces a user agent string to browser plus platform. Package-visible for testing. */
    static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        String browser = browserOf(agent);
        String platform = platformOf(agent);

        String label;
        if (browser == null && platform == null) {
            label = "Unknown device";
        } else if (browser == null) {
            label = platform;
        } else if (platform == null) {
            label = browser;
        } else {
            label = browser + " on " + platform;
        }
        return label.length() <= MAX_LABEL ? label : label.substring(0, MAX_LABEL);
    }

    private static String browserOf(String agent) {
        // Order matters: Edge and Opera also say "chrome", and Chrome also says "safari".
        if (agent.contains("edg/") || agent.contains("edge")) {
            return "Edge";
        }
        if (agent.contains("opr/") || agent.contains("opera")) {
            return "Opera";
        }
        if (agent.contains("firefox")) {
            return "Firefox";
        }
        if (agent.contains("chrome") || agent.contains("crios")) {
            return "Chrome";
        }
        if (agent.contains("safari")) {
            return "Safari";
        }
        return null;
    }

    private static String platformOf(String agent) {
        // iPhone and iPad before "mac", because mobile Safari's agent contains "like Mac OS X".
        if (agent.contains("iphone")) {
            return "iPhone";
        }
        if (agent.contains("ipad")) {
            return "iPad";
        }
        if (agent.contains("android")) {
            return "Android";
        }
        if (agent.contains("windows")) {
            return "Windows";
        }
        if (agent.contains("mac os") || agent.contains("macintosh")) {
            return "macOS";
        }
        if (agent.contains("linux")) {
            return "Linux";
        }
        return null;
    }

    private static Optional<HttpServletRequest> currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? Optional.of(attributes.getRequest())
                : Optional.empty();
    }
}
