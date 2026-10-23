package com.motorsure.claims;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class Claim {
    @Id private UUID id;
    @Column(name = "policy_number", nullable = false, length = 64) private String policyNumber;
    @Column(name = "vehicle_registration", nullable = false, length = 16) private String vehicleRegistration;
    @Column(name = "incident_type", nullable = false, length = 64) private String incidentType;
    @Column(name = "incident_date", nullable = false) private LocalDate incidentDate;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(nullable = false) private long version;

    protected Claim() {}
    public Claim(String policyNumber, String vehicleRegistration, String incidentType, LocalDate incidentDate, String description) {
        this.id = UUID.randomUUID();
        this.policyNumber = policyNumber;
        this.vehicleRegistration = vehicleRegistration;
        this.incidentType = incidentType;
        this.incidentDate = incidentDate;
        this.description = description;
        this.status = "SUBMITTED";
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public String getVehicleRegistration() { return vehicleRegistration; }
    public String getIncidentType() { return incidentType; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
