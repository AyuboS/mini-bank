package com.minibank.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reply on an existing ticket. */
@Schema(name = "SupportMessageRequest")
public record SupportMessageRequest(@NotBlank @Size(max = 4000) String body) {}
