package com.motorsure.coreapi.customer.api;

import com.motorsure.coreapi.customer.domain.Customer;
import com.motorsure.coreapi.customer.domain.CustomerStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CustomerResponses {
    private CustomerResponses() { }
    public record CustomerView(UUID id, String customerNumber, String firstName, String lastName,
                               String email, String phoneNumber, LocalDate dateOfBirth,
                               CustomerStatus status, Instant createdAt, Instant updatedAt) {
        public static CustomerView from(Customer c) {
            return new CustomerView(c.getId(), c.getCustomerNumber(), c.getFirstName(), c.getLastName(),
                    c.getEmail(), c.getPhoneNumber(), c.getDateOfBirth(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }
    public record CustomerPage(List<CustomerView> items, int page, int size, long totalElements, int totalPages) { }
}
