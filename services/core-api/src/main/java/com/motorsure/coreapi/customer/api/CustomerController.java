package com.motorsure.coreapi.customer.api;

import com.motorsure.coreapi.customer.application.CustomerService;
import com.motorsure.coreapi.customer.domain.Customer;
import com.motorsure.coreapi.customer.domain.CustomerStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private static final Set<String> SORT_FIELDS = Set.of("customerNumber", "firstName", "lastName", "createdAt", "updatedAt");
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CustomerResponses.CustomerView> create(@Valid @RequestBody CustomerRequests.Create request) {
        Customer created = service.create(request.firstName(), request.lastName(), request.email(), request.phoneNumber(), request.dateOfBirth());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(CustomerResponses.CustomerView.from(created));
    }
    @GetMapping("/{id}")
    public CustomerResponses.CustomerView get(@PathVariable UUID id) { return CustomerResponses.CustomerView.from(service.get(id)); }
    @GetMapping
    public CustomerResponses.CustomerPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        if (!SORT_FIELDS.contains(sortBy)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported sort field");
        var result = service.list(page, size, sortBy, direction);
        return new CustomerResponses.CustomerPage(result.getContent().stream().map(CustomerResponses.CustomerView::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @PutMapping("/{id}")
    public CustomerResponses.CustomerView update(@PathVariable UUID id, @Valid @RequestBody CustomerRequests.Update request) {
        return CustomerResponses.CustomerView.from(service.update(id, request.firstName(), request.lastName(), request.email(),
                request.phoneNumber(), request.dateOfBirth()));
    }
    @PatchMapping("/{id}/status")
    public CustomerResponses.CustomerView changeStatus(@PathVariable UUID id, @Valid @RequestBody CustomerRequests.Status request) {
        return CustomerResponses.CustomerView.from(service.changeStatus(id, request.status()));
    }
}
