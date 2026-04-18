package com.example.dirforwarderbot.service;

import com.example.dirforwarderbot.entity.State;
import com.example.dirforwarderbot.entity.User;
import com.example.dirforwarderbot.repository.DirectionRepository;
import com.example.dirforwarderbot.repository.GroupRepository;
import com.example.dirforwarderbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DirectionRepository directionRepository;
    private final GroupRepository groupRepository;
    private final KeyboardService keyboardService;

    public SendMessage handleStart(User user) {
        if (user.getFullName() != null && user.getPhoneNumber() != null) {
            return getMainUserMenu(user.getChatId());
        }
        user.setState(State.WAITING_FULL_NAME);
        userRepository.save(user);
        return new SendMessage(user.getChatId().toString(), "Assalomu alaykum! Ism va familiyangizni kiriting:");
    }

    public SendMessage handleText(User user, String text) {
        Long chatId = user.getChatId();

        // 1. Ism kiritish
        if (user.getState() == State.WAITING_FULL_NAME) {
            user.setFullName(text);
            user.setState(State.WAITING_PHONE);
            userRepository.save(user);
            return askPhone(chatId);
        }

        // 2. Namunalar
        if (text.equals("📄 Namunalar")) {
            user.setState(State.VIEWING_SAMPLES);
            userRepository.save(user);
            return keyboardService.getSamplesReplyMenu(chatId);
        }

        // 3. Savol berish
        if (text.equals("❓ Savol berish")) {
            user.setState(State.WAITING_QUESTION);
            userRepository.save(user);
            return new SendMessage(chatId.toString(), "✍️ Marhamat, savolingizni yozing:");
        }

        // 4. FAYL YUBORISH
        if (text.equals("📤 Fayl yuborish")) {
            if (user.getSelectedGroup() != null && user.getSelectedDirection() != null) {
                user.setState(State.WAITING_FILE);
                userRepository.save(user);
                return new SendMessage(chatId.toString(),
                        "✅ Ma'lumotlaringiz tasdiqlangan:\n" +
                                "🏢 Guruh: " + user.getSelectedGroup() + "\n" +
                                "📁 Yo'nalish: " + user.getSelectedDirection() + "\n\n" +
                                "📥 Marhamat, topshiriq faylini (Document) yuboring:");
            } else {
                user.setState(State.WAITING_SELECT_GROUP);
                userRepository.save(user);
                return showGroups(chatId);
            }
        }

        // 5. Guruh tanlash
        if (user.getState() == State.WAITING_SELECT_GROUP) {
            user.setSelectedGroup(text);
            user.setState(State.WAITING_SELECT_DIR);
            userRepository.save(user);
            return showDirections(chatId);
        }

        // 6. Yo'nalish tanlash (Tuzatildi: Fayl so'ramaydi, asosiy menyuga qaytadi)
        if (user.getState() == State.WAITING_SELECT_DIR) {
            user.setSelectedDirection(text);
            user.setState(State.FREE);
            userRepository.save(user);

            SendMessage sm = new SendMessage(chatId.toString(), "✅ Ma'lumotlaringiz muvaffaqiyatli saqlandi.");
            sm.setReplyMarkup(getMainUserMenu(chatId).getReplyMarkup());
            return sm;
        }

        // 7. Ma'lumotlarni yangilash (Tuzatildi: Ismdan boshlanadi)
        if (text.equals("🔄 Ma'lumotlarni yangilash")) {
            user.setState(State.WAITING_FULL_NAME);
            userRepository.save(user);
            return new SendMessage(chatId.toString(), "📝 Yangi ism va familiyangizni kiriting:");
        }

        return getMainUserMenu(chatId);
    }

    // Telefon yuborilgach guruh tanlashga o'tish
    public SendMessage handleContact(User user, Contact contact) {
        user.setPhoneNumber(contact.getPhoneNumber());
        user.setState(State.WAITING_SELECT_GROUP);
        userRepository.save(user);
        return showGroups(user.getChatId());
    }

    public SendMessage getMainUserMenu(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "Asosiy menyu:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📤 Fayl yuborish"));
        row1.add(new KeyboardButton("📄 Namunalar"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🔄 Ma'lumotlarni yangilash"));
        row2.add(new KeyboardButton("❓ Savol berish"));

        keyboard.add(row1); keyboard.add(row2);
        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage showGroups(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "🏢 Guruhni tanlang:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        groupRepository.findAll().forEach(g -> {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(g.getName()));
            keyboard.add(row);
        });
        if (keyboard.isEmpty()) return new SendMessage(chatId.toString(), "Hozircha guruhlar yo'q.");
        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage showDirections(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "📁 Yo'nalishni tanlang:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        directionRepository.findAll().forEach(dir -> {
            KeyboardRow row = new KeyboardRow();
            row.add(new KeyboardButton(dir.getName()));
            keyboard.add(row);
        });
        if (keyboard.isEmpty()) return new SendMessage(chatId.toString(), "Hozircha yo'nalishlar yo'q.");
        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    private SendMessage askPhone(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "Telefon raqamingizni yuboring:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        KeyboardButton btn = new KeyboardButton("📱 Kontaktni ulash");
        btn.setRequestContact(true);
        markup.setKeyboard(List.of(new KeyboardRow(List.of(btn))));
        sm.setReplyMarkup(markup);
        return sm;
    }
}