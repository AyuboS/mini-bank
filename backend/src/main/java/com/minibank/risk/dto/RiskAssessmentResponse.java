package com.minibank.risk.dto;

import com.minibank.risk.domain.RiskAssessment;
import com.minibank.risk.domain.RiskDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The risk verdict attached to a transfer. */
@Schema(name = "RiskAssessmentResponse")
public record RiskAssessmentResponse(
        @Schema(example = "REVIEW") RiskDecision decision,
        @Schema(example = "72") int riskScore,
        @Schema(example = "[\"Large transfer amount\", \"5 transfers started in the last 5 minutes\"]")
                List<String> reasons) {

    public static RiskAssessmentResponse from(RiskAssessment assessment) {
        return new RiskAssessmentResponse(
                assessment.decision(), assessment.riskScore(), assessment.reasons());
    }
}
