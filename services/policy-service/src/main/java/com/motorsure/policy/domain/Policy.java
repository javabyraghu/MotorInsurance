package com.motorsure.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "policies")
public class Policy {
    @Id private UUID id;
    @Column(name = "customer_name", nullable = false, length = 160) private String customerName;
    @Column(name = "vehicle_registration", nullable = false, length = 16) private String vehicleRegistration;
    @Column(name = "vehicle_make", nullable = false, length = 80) private String vehicleMake;
    @Column(name = "cover_type", nullable = false, length = 32) private String coverType;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(nullable = false) private long version;

    protected Policy() {}
    public Policy(String customerName, String vehicleRegistration, String vehicleMake, String coverType) {
        this.id = UUID.randomUUID();
        this.customerName = customerName;
        this.vehicleRegistration = vehicleRegistration;
        this.vehicleMake = vehicleMake;
        this.coverType = coverType;
        this.status = "ACTIVE";
        this.startDate = LocalDate.now();
        this.endDate = startDate.plusYears(1);
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getVehicleRegistration() { return vehicleRegistration; }
    public String getVehicleMake() { return vehicleMake; }
    public String getCoverType() { return coverType; }
    public String getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Instant getCreatedAt() { return createdAt; }
}
