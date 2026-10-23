package com.motorsure.coreapi.customer.infrastructure.persistence;

import com.motorsure.coreapi.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> { }
