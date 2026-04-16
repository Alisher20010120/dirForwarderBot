package com.example.dirforwarderbot.repository;

import com.example.dirforwarderbot.entity.Directions;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectionRepository extends JpaRepository<Directions, Long> {
}
