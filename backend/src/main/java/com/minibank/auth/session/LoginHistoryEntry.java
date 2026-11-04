package com.minibank.auth.session;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One sign-in attempt, successful or not.
 *
 * <p>Read from the audit log rather than from a table of its own. The audit trail already records
 * every authentication event, and a second store would be one more thing that can disagree with it.
 */
@Schema(name = "LoginHistoryEntry")
public record LoginHistoryEntry(
        @Schema(example = "PASSWORD_LOGIN") String action,
        @Schema(description = "SUCCESS or FAILURE") String outcome,
        @Schema(example = "Chrome on macOS") String device,
        @Schema(example = "192.0.2.14") String ipAddress,
        Instant at) {}
