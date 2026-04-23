package com.example.dirforwarderbot.service;

import com.example.dirforwarderbot.entity.Group;
import com.example.dirforwarderbot.entity.Role;
import com.example.dirforwarderbot.entity.Sample;
import com.example.dirforwarderbot.entity.User;
import com.example.dirforwarderbot.repository.GroupRepository;
import com.example.dirforwarderbot.repository.SampleRepository;
import com.example.dirforwarderbot.repository.UserRepository;
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

    private final GroupRepository groupRepository;
    private final SampleRepository sampleRepository;
    private final UserRepository userRepository;

    // Foydalanuvchi uchun namunalar tugmalari
    public SendMessage getSamplesReplyMenu(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "📄 Kerakli namunani yuklab olish uchun tanlang:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        List<Sample> allSamples = sampleRepository.findAll();

        for (int i = 0; i < allSamples.size(); i += 2) {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(allSamples.get(i).getDisplayName()));
            if (i + 1 < allSamples.size()) {
                row.add(new KeyboardButton(allSamples.get(i + 1).getDisplayName()));
            }
            keyboard.add(row);
        }

        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("⬅️ Orqaga"));
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    // Admin uchun Reply Menyu (Asosiy panel)
    public SendMessage getAdminReplyMenu(User user) {
        SendMessage sm = new SendMessage(user.getChatId().toString(), "🛠 Admin paneli:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("👥 Guruh qo'shish"));
        row1.add(new KeyboardButton("📥 Excel orqali User qo'shish"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("⚙️ Sozlamalar"));
        row2.add(new KeyboardButton("📂 Namuna qo'shish"));

        keyboard.add(row1);
        keyboard.add(row2);

        if (user.getRole() == Role.SUPER_ADMIN) {
            KeyboardRow row3 = new KeyboardRow();
            row3.add(new KeyboardButton("➕ Admin qo'shish"));
            keyboard.add(row3);
        }

        keyboard.add(new KeyboardRow(List.of(new KeyboardButton("🔝 Chiqish"))));

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    // Admin Sozlamalar (Inline)
    public SendMessage getSettingsMenu(User user) {
        SendMessage sm = new SendMessage(user.getChatId().toString(), "⚙️ **Sozlamalar bo'limi:**");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createInlineBtn("👥 Guruhlarni boshqarish", "view_groups")));
        rows.add(List.of(createInlineBtn("📄 Namunalarni boshqarish", "view_samples")));

        // Faqat Super Admin uchun Adminlarni boshqarish tugmasi
        if (user.getRole() == Role.SUPER_ADMIN) {
            rows.add(List.of(createInlineBtn("👮‍♂️ Adminlarni boshqarish", "view_admins")));
        }

        markup.setKeyboard(rows);
        sm.setReplyMarkup(markup);
        return sm;
    }

    // Adminlar ro'yxati (Faqat Super Admin uchun)
    public SendMessage getAdminList(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "👮‍♂️ **Bot adminlari ro'yxati:**");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .forEach(admin -> {
                    String name = admin.getFullName() != null ? admin.getFullName() : admin.getChatId().toString();
                    rows.add(List.of(
                            createInlineBtn(name, "none"),
                            createInlineBtn("❌ O'chirish", "demote_admin_" + admin.getId())
                    ));
                });

        rows.add(List.of(createInlineBtn("⬅️ Orqaga", "settings")));
        markup.setKeyboard(rows);
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage getGroupList(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "👥 **Guruhlar manzillarini tahrirlash:**");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        groupRepository.findAll().forEach(g -> {
            rows.add(List.of(
                    createInlineBtn(g.getName(), "none"),
                    createInlineBtn("⚙️ ID ni o'zgartirish", "edit_group_id_" + g.getName())
            ));
        });

        rows.add(List.of(createInlineBtn("⬅️ Orqaga", "settings")));
        markup.setKeyboard(rows);
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage getSampleList(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "📄 **Namunalar ro'yxati:**");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        sampleRepository.findAll().forEach(s -> {
            rows.add(List.of(
                    createInlineBtn(s.getDisplayName(), "none"),
                    createInlineBtn("❌ O'chirish", "del_sample_" + s.getId())
            ));
        });
        rows.add(List.of(createInlineBtn("⬅️ Orqaga", "settings")));
        markup.setKeyboard(rows);
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage getGroupSelectMenu(Long chatId) {
        List<Group> allGroups = groupRepository.findAll();
        if (allGroups.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠️ Guruhlar yo'q. Excel yuklang.");
        }

        SendMessage sm = new SendMessage(chatId.toString(), "Guruhni tanlang (Target Chat ID uchun):");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        for (Group group : allGroups) {
            KeyboardRow row = new KeyboardRow();
            String label = group.getName() + (group.getTargetChatId() == null || group.getTargetChatId().isEmpty() ? " ⚠️" : " ✅");
            row.add(new KeyboardButton(label));
            keyboard.add(row);
        }

        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("⬅️ Orqaga"));
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    private InlineKeyboardButton createInlineBtn(String text, String callback) {
        InlineKeyboardButton btn = new InlineKeyboardButton(text);
        btn.setCallbackData(callback);
        return btn;
    }

    public SendMessage getBackMenu(Long chatId, String text) {
        SendMessage sm = new SendMessage(chatId.toString(), text);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new KeyboardRow(List.of(new KeyboardButton("⬅️ Orqaga"))));
        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }
}