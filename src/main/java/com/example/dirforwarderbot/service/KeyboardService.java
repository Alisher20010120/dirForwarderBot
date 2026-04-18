package com.example.dirforwarderbot.service;

import com.example.dirforwarderbot.entity.Role;
import com.example.dirforwarderbot.entity.Sample;
import com.example.dirforwarderbot.entity.User;
import com.example.dirforwarderbot.repository.DirectionRepository;
import com.example.dirforwarderbot.repository.GroupRepository;
import com.example.dirforwarderbot.repository.SampleRepository;
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
    private final SampleRepository sampleRepository;

    // Foydalanuvchi uchun namunalar tugmalari
    public SendMessage getSamplesReplyMenu(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "📄 Kerakli namunani yuklab olish uchun tanlang:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();
        List<Sample> allSamples = sampleRepository.findAll();

        // Namunalar ro'yxatini 2 tadan qilib guruhlash
        for (int i = 0; i < allSamples.size(); i += 2) {
            KeyboardRow row = new KeyboardRow();

            // Birinchi ustun tugmasi
            row.add(new KeyboardButton(allSamples.get(i).getDisplayName()));

            // Ikkinchi ustun tugmasi (agar mavjud bo'lsa)
            if (i + 1 < allSamples.size()) {
                row.add(new KeyboardButton(allSamples.get(i + 1).getDisplayName()));
            }

            keyboard.add(row);
        }

        // "Orqaga" tugmasini alohida pastki qatorda chiqarish
        KeyboardRow backRow = new KeyboardRow();
        backRow.add(new KeyboardButton("⬅️ Orqaga"));
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    // Admin uchun Reply Menyu
    public SendMessage getAdminReplyMenu(User user) {
        SendMessage sm = new SendMessage(user.getChatId().toString(), "🛠 Admin paneli:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("➕ Yo'nalish qo'shish"));
        row1.add(new KeyboardButton("👥 Guruh qo'shish"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("⚙️ Sozlamalar"));
        row2.add(new KeyboardButton("📂 Namuna qo'shish"));

        if (user.getRole() == Role.SUPER_ADMIN) {
            row2.add(new KeyboardButton("➕ Admin qo'shish"));
        }

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(new KeyboardRow(List.of(new KeyboardButton("🔝 Chiqish"))));

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    // Admin Sozlamalar (Inline)
    public SendMessage getSettingsMenu(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "⚙️ **Sozlamalar bo'limi:**");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(createInlineBtn("📁 Yo'nalishlarni boshqarish", "view_dirs")),
                List.of(createInlineBtn("👥 Guruhlarni boshqarish", "view_groups")),
                List.of(createInlineBtn("📄 Namunalarni boshqarish", "view_samples"))
        ));
        sm.setReplyMarkup(markup);
        return sm;
    }

    // Ro'yxatlarni Inline ko'rinishida chiqarish (O'chirish tugmasi bilan)
    public SendMessage getDirectionList(Long chatId) {
        return getGenericList(chatId, "📁 **Yo'nalishlar ro'yxati:**", directionRepository.findAll(), "del_dir_");
    }

    public SendMessage getGroupList(Long chatId) {
        return getGenericList(chatId, "👥 **Guruhlar ro'yxati:**", groupRepository.findAll(), "del_group_");
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

    private SendMessage getGenericList(Long chatId, String title, Iterable<?> items, String prefix) {
        SendMessage sm = new SendMessage(chatId.toString(), title);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        items.forEach(item -> {
            try {
                String name = (String) item.getClass().getMethod("getName").invoke(item);
                Long id = (Long) item.getClass().getMethod("getId").invoke(item);
                rows.add(List.of(createInlineBtn(name, "none"), createInlineBtn("❌ O'chirish", prefix + id)));
            } catch (Exception ignored) {}
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