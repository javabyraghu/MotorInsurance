package com.motorsure.policy.infrastructure.persistence;

import com.motorsure.policy.domain.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {}
