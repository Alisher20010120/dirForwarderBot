package com.example.dirforwarderbot.command;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Barcha komandalar (Start, Admin, h.k.) uchun umumiy shablon.
 * SOLID ning Interface Segregation tamoyiliga asoslangan.
 */
public interface BotCommand {
    
    /**
     * Komandani bajarish mantiqi
     * @param update Telegramdan kelgan ma'lumot
     * @return Bot foydalanuvchiga qaytarishi kerak bo'lgan xabar
     */
    SendMessage execute(Update update);

    /**
     * Komandaning nomi (masalan: /start, /admin)
     * Bu nom orqali bot qaysi komandani ishlatishni aniqlaydi
     */
    String getCommandName();
}