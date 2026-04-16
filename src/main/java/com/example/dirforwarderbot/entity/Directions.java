package com.example.dirforwarderbot.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Directions extends BaseEntity {
    private String name;
    private String targetChatId;
}
