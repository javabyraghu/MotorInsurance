package com.motorsure.coreapi.customer.domain;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private UUID id;
    @Column(name = "customer_number", nullable = false, unique = true, length = 36)
    private String customerNumber;
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CustomerStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected Customer() { }

    public static Customer register(UUID id, String firstName, String lastName, String email, String phoneNumber,
                                    LocalDate dateOfBirth, Clock clock) {
        Customer customer = new Customer();
        customer.id = id;
        customer.customerNumber = "CUS-" + id.toString().replace("-", "").toUpperCase(Locale.ROOT);
        customer.firstName = clean(firstName);
        customer.lastName = clean(lastName);
        customer.email = email.trim().toLowerCase(Locale.ROOT);
        customer.phoneNumber = cleanOptional(phoneNumber);
        customer.dateOfBirth = dateOfBirth;
        customer.status = CustomerStatus.ACTIVE;
        customer.createdAt = Instant.now(clock);
        customer.updatedAt = customer.createdAt;
        return customer;
    }

    public void updateProfile(String firstName, String lastName, String email, String phoneNumber,
                              LocalDate dateOfBirth, Clock clock) {
        this.firstName = clean(firstName);
        this.lastName = clean(lastName);
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.phoneNumber = cleanOptional(phoneNumber);
        this.dateOfBirth = dateOfBirth;
        this.updatedAt = Instant.now(clock);
    }

    public void changeStatus(CustomerStatus status, Clock clock) {
        if (this.status != status) {
            this.status = status;
            this.updatedAt = Instant.now(clock);
        }
    }

    private static String clean(String value) { return value.trim(); }
    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getCustomerNumber() { return customerNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public CustomerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
