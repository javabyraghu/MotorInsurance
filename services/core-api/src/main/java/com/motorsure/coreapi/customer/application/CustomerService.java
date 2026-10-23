package com.motorsure.coreapi.customer.application;

import com.motorsure.coreapi.customer.domain.Customer;
import com.motorsure.coreapi.customer.domain.CustomerStatus;
import com.motorsure.coreapi.customer.infrastructure.persistence.CustomerRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {
    private final CustomerRepository repository;
    private final Clock clock;
    public CustomerService(CustomerRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    public Customer create(String first, String last, String email, String phone, LocalDate dob) {
        return repository.save(Customer.register(UUID.randomUUID(), first, last, email, phone, dob, clock));
    }
    @Transactional(readOnly = true)
    public boolean exists(UUID id) { return repository.existsById(id); }
    @Transactional(readOnly = true)
    public Customer get(UUID id) { return repository.findById(id).orElseThrow(CustomerService::notFound); }
    @Transactional(readOnly = true)
    public Page<Customer> list(int page, int size, String sort, Sort.Direction direction) {
        Sort safeSort = Sort.by(direction, sort).and(Sort.by(Sort.Direction.ASC, "id"));
        return repository.findAll(PageRequest.of(page, size, safeSort));
    }
    public Customer update(UUID id, String first, String last, String email, String phone, LocalDate dob) {
        Customer customer = get(id); customer.updateProfile(first, last, email, phone, dob, clock); return customer;
    }
    public Customer changeStatus(UUID id, CustomerStatus status) {
        Customer customer = get(id); customer.changeStatus(status, clock); return customer;
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
    }
}
