package com.motorsure.policy.api;

import com.motorsure.policy.domain.Policy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyResponse(UUID id, String customerName, String vehicleRegistration, String vehicleMake,
                             String coverType, String status, LocalDate startDate, LocalDate endDate,
                             Instant createdAt) {
    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(policy.getId(), policy.getCustomerName(), policy.getVehicleRegistration(),
                policy.getVehicleMake(), policy.getCoverType(), policy.getStatus(), policy.getStartDate(),
                policy.getEndDate(), policy.getCreatedAt());
    }
}
