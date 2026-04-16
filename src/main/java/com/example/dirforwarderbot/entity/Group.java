package com.example.dirforwarderbot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "groups")
@Getter
@Setter
public class Group extends BaseEntity {
    private String name;
    private String targetChatId; // Guruhning Telegram ID si (masalan: -1001234567)
}