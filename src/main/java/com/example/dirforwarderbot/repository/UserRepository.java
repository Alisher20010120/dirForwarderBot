package com.example.dirforwarderbot.repository;

import com.example.dirforwarderbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByChatId(Long chatId);

    Optional<User> findByPhoneNumber(String phone);

    Optional<User> findByFullName(String fullName);
}
