package com.example.dirforwarderbot.repository;

import com.example.dirforwarderbot.entity.Sample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SampleRepository extends JpaRepository<Sample, Long> {
    Optional<Sample> findByDisplayName(String displayName);
}