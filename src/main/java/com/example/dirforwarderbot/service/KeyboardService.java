package com.example.dirforwarderbot.service;

import com.example.dirforwarderbot.entity.Role;
import com.example.dirforwarderbot.entity.User;
import com.example.dirforwarderbot.repository.DirectionRepository;
import com.example.dirforwarderbot.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeyboardService {

    private final DirectionRepository directionRepository;
    private final GroupRepository groupRepository;

    public SendMessage getAdminReplyMenu(User user) {
        SendMessage sm = new SendMessage();
        sm.setChatId(user.getChatId().toString());
        sm.setText("🛠 Admin paneli:");

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("➕ Yo'nalish qo'shish"));
        row1.add(new KeyboardButton("👥 Guruh qo'shish"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("⚙️ Sozlamalar"));

        // AGAR SUPER ADMIN BO'LSA TUGMA QO'SHILADI
        if (user.getRole() == Role.SUPER_ADMIN) {
            row2.add(new KeyboardButton("➕ Admin qo'shish"));
        }
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🔝 Chiqish"));
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage getSettingsMenu(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "⚙️ **Sozlamalar bo'limi:**");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
            List.of(createInlineBtn("📁 Yo'nalishlarni boshqarish", "view_dirs")),
            List.of(createInlineBtn("👥 Guruhlarni boshqarish", "view_groups"))
        ));
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage getDirectionList(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "📁 **Yo'nalishlar ro'yxati:**");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        directionRepository.findAll().forEach(dir -> {
            rows.add(List.of(
                createInlineBtn(dir.getName(), "none"),
                createInlineBtn("❌ O'chirish", "del_dir_" + dir.getId()) // dir.getId() ishlatilishi shart
            ));
        });
        
        rows.add(List.of(createInlineBtn("⬅️ Orqaga", "settings")));
        markup.setKeyboard(rows);
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage getGroupList(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "👥 **Guruhlar ro'yxati:**");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        groupRepository.findAll().forEach(group -> {
            rows.add(List.of(
                createInlineBtn(group.getName(), "none"),
                createInlineBtn("❌ O'chirish", "del_group_" + group.getId())
            ));
        });

        rows.add(List.of(createInlineBtn("⬅️ Orqaga", "settings")));
        markup.setKeyboard(rows);
        sm.setReplyMarkup(markup);
        return sm;
    }

    private InlineKeyboardButton createInlineBtn(String text, String callback) {
        InlineKeyboardButton btn = new InlineKeyboardButton(text);
        btn.setCallbackData(callback);
        return btn;
    }
}