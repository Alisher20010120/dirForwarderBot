package com.example.dirforwarderbot.entity;

public enum Role {
    USER,
    MODERATOR,  // Faqat xabarlarni ko'ra oladi
    ADMIN,      // Yo'nalish qo'sha oladi
    SUPER_ADMIN // Hamma narsani (admin qo'shishni ham) qila oladi
}