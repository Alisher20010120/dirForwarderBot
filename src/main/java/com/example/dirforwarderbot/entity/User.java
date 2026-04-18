package com.example.dirforwarderbot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User extends BaseEntity {
    private Long chatId;
    private String fullName;
    private String phoneNumber;
    private String selectedDirection;
    private String selectedGroup;
    private String username;
    private String tempData;

    @Enumerated(EnumType.STRING)
    private State state = State.FREE;
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    public boolean isRegistered() {
        return fullName != null && phoneNumber != null && selectedDirection != null;
    }
}