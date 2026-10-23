package com.motorsure.claims.infrastructure.persistence;

import com.motorsure.claims.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {}
