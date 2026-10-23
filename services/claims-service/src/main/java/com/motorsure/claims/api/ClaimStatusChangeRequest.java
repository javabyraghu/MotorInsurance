package com.motorsure.claims.api;

import com.motorsure.claims.domain.ClaimStatus;
import jakarta.validation.constraints.NotNull;

public record ClaimStatusChangeRequest(@NotNull ClaimStatus status) {}
