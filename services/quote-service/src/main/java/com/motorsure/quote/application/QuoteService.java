package com.motorsure.quote.application;

import com.motorsure.quote.api.QuoteRequest;
import com.motorsure.quote.api.QuoteResponse;
import com.motorsure.quote.domain.Quote;
import com.motorsure.quote.domain.QuoteNotFoundException;
import com.motorsure.quote.infrastructure.messaging.QuoteEventPublisher;
import com.motorsure.quote.infrastructure.persistence.QuoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class QuoteService {
    private final QuoteRepository quotes;
    private final QuoteEventPublisher eventPublisher;

    public QuoteService(QuoteRepository quotes, QuoteEventPublisher eventPublisher) {
        this.quotes = quotes;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<QuoteResponse> list(Pageable pageable) {
        return quotes.findAll(pageable).map(QuoteResponse::from);
    }

    @Transactional(readOnly = true)
    public QuoteResponse get(UUID id) {
        return quotes.findById(id).map(QuoteResponse::from).orElseThrow(() -> new QuoteNotFoundException(id));
    }

    @Transactional
    public QuoteResponse create(QuoteRequest request) {
        String coverType = request.coverType() == null ? "Comprehensive" : normalizeCover(request.coverType());
        String make = request.vehicleMake() == null || request.vehicleMake().isBlank() ? "Other" : request.vehicleMake().trim();
        // Placeholder tariff only: replace with a versioned actuarial/rating model before real policies are sold.
        BigDecimal premium = coverType.equals("Third party") ? new BigDecimal("7200.00") : new BigDecimal("14680.00");
        Quote quote = quotes.save(new Quote(request.customerName().trim(), request.vehicleRegistration().trim().toUpperCase(), make, coverType, premium));
        eventPublisher.recordCreated(quote);
        return QuoteResponse.from(quote);
    }

    private String normalizeCover(String coverType) {
        return coverType.equalsIgnoreCase("third party") ? "Third party" : "Comprehensive";
    }
}
