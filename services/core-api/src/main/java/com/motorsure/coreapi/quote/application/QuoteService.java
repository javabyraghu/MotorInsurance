package com.motorsure.coreapi.quote.application;

import com.motorsure.coreapi.customer.application.CustomerService;
import com.motorsure.coreapi.product.application.ProductQuoteConfiguration;
import com.motorsure.coreapi.product.application.ProductService;
import com.motorsure.coreapi.quote.api.QuoteRequests;
import com.motorsure.coreapi.quote.api.QuoteResponses;
import com.motorsure.coreapi.quote.domain.*;
import com.motorsure.coreapi.quote.infrastructure.persistence.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class QuoteService {
    private final QuoteRepository quotes;
    private final QuoteVehicleRiskRepository vehicles;
    private final QuoteCoverageSnapshotRepository snapshots;
    private final CustomerService customers;
    private final ProductService products;
    private final Clock clock;

    public QuoteService(QuoteRepository quotes, QuoteVehicleRiskRepository vehicles,
            QuoteCoverageSnapshotRepository snapshots, CustomerService customers, ProductService products,
            Clock clock) {
        this.quotes = quotes; this.vehicles = vehicles; this.snapshots = snapshots;
        this.customers = customers; this.products = products; this.clock = clock;
    }

    public QuoteResponses.View create(QuoteRequests.Create request) {
        if (!customers.exists(request.customerId())) throw notFound("Customer resource not found");
        Instant effectiveAt = request.coverageEffectiveAt() == null ? Instant.now(clock) : request.coverageEffectiveAt();
        ProductQuoteConfiguration productConfiguration = products.quoteConfiguration(request.productId(),
                request.productVersionId(), effectiveAt);
        if (request.vehicle().manufactureYear() > java.time.Year.now(clock).getValue())
            throw badRequest("Manufacture year cannot be in the future");

        Instant now = Instant.now(clock);
        Quote quote = quotes.save(Quote.draft(UUID.randomUUID(), request.customerId(), request.productId(),
                request.productVersionId(), effectiveAt, clock));
        QuoteRequests.Vehicle vehicle = request.vehicle();
        vehicles.save(QuoteVehicleRisk.create(quote, vehicle.vehicleCategory(), vehicle.manufacturer(), vehicle.model(),
                vehicle.manufactureYear(), vehicle.registrationNumber(), vehicle.fuelType(), vehicle.usageCategory(),
                vehicle.declaredVehicleValue(), vehicle.declaredVehicleCurrency()));
        snapshots.saveAll(productConfiguration.coverages().stream()
                .map(c -> QuoteCoverageSnapshot.from(quote.getId(), productConfiguration.productVersionId(), c.code(),
                        c.displayName(), c.description(), c.type().name(), c.mandatory(), c.optional(), c.available(), now))
                .toList());
        return view(quote, vehicles.findById(quote.getId()).orElseThrow(), snapshots.findAllByQuoteIdOrderByCoverageCode(quote.getId()));
    }

    @Transactional(readOnly = true)
    public QuoteResponses.View get(UUID id) {
        Quote quote = quotes.findById(id).orElseThrow(() -> notFound("Quote resource not found"));
        return view(quote, vehicles.findById(id).orElseThrow(), snapshots.findAllByQuoteIdOrderByCoverageCode(id));
    }

    @Transactional(readOnly = true)
    public Page<QuoteResponses.View> list(UUID customerId, int page, int size, String sortBy, Sort.Direction direction) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "id")));
        Page<Quote> result = customerId == null ? quotes.findAll(pageable) : quotes.findAllByCustomerId(customerId, pageable);
        List<UUID> ids = result.getContent().stream().map(Quote::getId).toList();
        Map<UUID, QuoteVehicleRisk> riskById = vehicles.findAllById(ids).stream().collect(Collectors.toMap(QuoteVehicleRisk::getQuoteId, v -> v));
        Map<UUID, List<QuoteCoverageSnapshot>> snapshotById = snapshots.findAllByQuoteIdInOrderByQuoteIdAscCoverageCodeAsc(ids)
                .stream().collect(Collectors.groupingBy(QuoteCoverageSnapshot::getQuoteId));
        return result.map(q -> view(q, riskById.get(q.getId()), snapshotById.getOrDefault(q.getId(), List.of())));
    }

    public QuoteResponses.View submit(UUID id) { return transition(id, true); }
    public QuoteResponses.View cancel(UUID id) { return transition(id, false); }

    private QuoteResponses.View transition(UUID id, boolean submit) {
        Quote quote = quotes.findById(id).orElseThrow(() -> notFound("Quote resource not found"));
        try { if (submit) quote.submit(clock); else quote.cancel(clock); }
        catch (IllegalStateException ex) { throw conflict(ex.getMessage()); }
        quotes.flush();
        return view(quote, vehicles.findById(id).orElseThrow(), snapshots.findAllByQuoteIdOrderByCoverageCode(id));
    }

    private static QuoteResponses.View view(Quote q, QuoteVehicleRisk vehicle, List<QuoteCoverageSnapshot> coverage) {
        return new QuoteResponses.View(q.getId(), q.getQuoteNumber(), q.getCustomerId(), q.getProductId(), q.getProductVersionId(),
                q.getStatus(), q.getPricingStatus(), q.getCoverageEffectiveAt(), q.getExpiresAt(), q.getCreatedAt(), q.getUpdatedAt(),
                QuoteResponses.VehicleView.from(vehicle), coverage.stream().map(QuoteResponses.CoverageSnapshot::from).toList());
    }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
