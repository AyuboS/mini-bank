package com.minibank.verification.service;

/**
 * The words MBank sends to customers.
 *
 * <p>Kept together and away from the flow logic, because these strings are the product's voice and
 * they get reviewed, translated and rewritten on a completely different schedule from the code that
 * decides when to send them.
 *
 * <p>Two rules hold throughout: a message never contains a password, and it never contains any
 * personal detail beyond the one the recipient just typed in.
 *
 * <p>Substitution is by named placeholder rather than {@code String.format}. Two reasons, and the
 * first is not cosmetic: a format string's {@code %n} emits the platform's line separator, which
 * on Windows is CRLF, and these bodies go onto a wire where the line endings are part of the
 * payload. Literal {@code \n} in a text block is the same everywhere. The second is that {@code %s}
 * in a hundred-line HTML template is unreadable, and the HTML no longer has to double every
 * percent sign it contains.
 */
public final class VerificationMessages {

    private VerificationMessages() {}

    /** The SMS body. Brand first, so it is recognisable in a notification preview. */
    public static String sms(String code, long ttlMinutes) {
        return """
                MBank verification code: {code}

                This code expires in {minutes} minutes.
                Do not share this code with anyone."""
                .replace("{code}", code)
                .replace("{minutes}", String.valueOf(ttlMinutes));
    }

    public static String emailSubject() {
        return "Verify your MBank email";
    }

    public static String emailText(String firstName, String link, long ttlHours) {
        return """
                Welcome to MBank{name}.

                Please verify your email address to continue:

                {link}

                This link expires in {hours} hours. If you did not create an MBank account,
                you can ignore this message and nothing will happen.

                — MBank"""
                .replace("{name}", firstName == null || firstName.isBlank() ? "" : ", " + firstName)
                .replace("{link}", link)
                .replace("{hours}", String.valueOf(ttlHours));
    }

    /**
     * The HTML alternative.
     *
     * <p>Table-based and inline-styled on purpose. Email clients are not browsers: Outlook renders
     * through Word, Gmail strips {@code <style>} blocks, and flexbox is not reliably supported
     * anywhere. This is the layout that survives all of them.
     */
    public static String emailHtml(String firstName, String link, long ttlHours) {
        String greeting = firstName == null || firstName.isBlank() ? "Welcome to MBank." : "Welcome, " + firstName + ".";
        return """
                <!doctype html>
                <html><body style="margin:0;padding:0;background:#F3F2ED;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                         style="background:#F3F2ED;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                             style="max-width:520px;background:#FFFFFF;border-radius:20px;
                                    border:1px solid #E4E2D9;padding:36px;">
                        <tr><td style="font:700 22px/1.2 -apple-system,Segoe UI,Roboto,sans-serif;
                                       color:#12361F;padding-bottom:20px;letter-spacing:-0.02em;">
                          MBank
                        </td></tr>
                        <tr><td style="font:600 20px/1.35 -apple-system,Segoe UI,Roboto,sans-serif;
                                       color:#14171A;padding-bottom:10px;">{greeting}</td></tr>
                        <tr><td style="font:400 15px/1.6 -apple-system,Segoe UI,Roboto,sans-serif;
                                       color:#565C63;padding-bottom:28px;">
                          Please verify your email address to continue.
                        </td></tr>
                        <tr><td style="padding-bottom:28px;">
                          <a href="{link}"
                             style="display:inline-block;background:#12361F;color:#F4F6F1;
                                    text-decoration:none;font:600 15px -apple-system,Segoe UI,Roboto,sans-serif;
                                    padding:14px 28px;border-radius:999px;">Verify email</a>
                        </td></tr>
                        <tr><td style="font:400 13px/1.6 -apple-system,Segoe UI,Roboto,sans-serif;
                                       color:#82888F;border-top:1px solid #E4E2D9;padding-top:20px;">
                          This link expires in {hours} hours. If you did not create an MBank account,
                          you can ignore this message.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>"""
                .replace("{greeting}", greeting)
                .replace("{link}", link)
                .replace("{hours}", String.valueOf(ttlHours));
    }
}
