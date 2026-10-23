package com.motorsure.claims.api;

import com.motorsure.claims.domain.Claim;
import com.motorsure.claims.domain.ClaimStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimResponse(UUID id, String policyNumber, String vehicleRegistration, String incidentType,
                            LocalDate incidentDate, String description, ClaimStatus status, Instant createdAt) {
    public static ClaimResponse from(Claim claim) {
        return new ClaimResponse(claim.getId(), claim.getPolicyNumber(), claim.getVehicleRegistration(),
                claim.getIncidentType(), claim.getIncidentDate(), claim.getDescription(), claim.getStatus(), claim.getCreatedAt());
    }
}
