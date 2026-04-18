package com.example.dirforwarderbot.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Sample extends BaseEntity {
    private String displayName; // Tugmada ko'rinadigan nomi
    private String fileId;      // Telegramdagi File ID
    private String caption;     // Fayl ostidagi izoh
}