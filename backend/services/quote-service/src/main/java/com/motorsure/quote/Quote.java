package com.motorsure.quote;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quotes")
public class Quote {
    @Id
    private UUID id;

    @Column(name = "customer_name", nullable = false, length = 160)
    private String customerName;

    @Column(name = "vehicle_registration", nullable = false, length = 16)
    private String vehicleRegistration;

    @Column(name = "vehicle_make", nullable = false, length = 80)
    private String vehicleMake;

    @Column(name = "cover_type", nullable = false, length = 32)
    private String coverType;

    @Column(name = "annual_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal annualPremium;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Quote() {}

    public Quote(String customerName, String vehicleRegistration, String vehicleMake,
                 String coverType, BigDecimal annualPremium) {
        this.id = UUID.randomUUID();
        this.customerName = customerName;
        this.vehicleRegistration = vehicleRegistration;
        this.vehicleMake = vehicleMake;
        this.coverType = coverType;
        this.annualPremium = annualPremium;
        this.status = "READY";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getVehicleRegistration() { return vehicleRegistration; }
    public String getVehicleMake() { return vehicleMake; }
    public String getCoverType() { return coverType; }
    public BigDecimal getAnnualPremium() { return annualPremium; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
