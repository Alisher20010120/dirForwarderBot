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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
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
        Long chatId = user.getChatId();

        // 1. Ism familiya tekshiruvi
        if (user.getFullName() == null) {
            user.setState(State.WAITING_FULL_NAME);
            userRepository.save(user);
            SendMessage sm = new SendMessage(chatId.toString(), "Assalomu alaykum! Botdan foydalanish uchun ism va familiyangizni kiriting:");

            // MUHIM: Bu yerda eski (Admin) menyuni o'chirib tashlaymiz
            sm.setReplyMarkup(new ReplyKeyboardRemove(true));
            return sm;
        }

        // 2. Telefon raqami tekshiruvi
        if (user.getPhoneNumber() == null) {
            user.setState(State.WAITING_PHONE);
            userRepository.save(user);
            return askPhone(chatId);
        }

        // 3. Guruh tekshiruvi
        if (user.getSelectedGroup() == null) {
            user.setState(State.WAITING_SELECT_GROUP);
            userRepository.save(user);
            return showGroups(chatId);
        }

        // 4. Yo'nalish tekshiruvi
        if (user.getSelectedDirection() == null) {
            user.setState(State.WAITING_SELECT_DIR);
            userRepository.save(user);
            return showDirections(chatId);
        }

        user.setState(State.FREE);
        userRepository.save(user);

        SendMessage sm = new SendMessage(chatId.toString(), "Asosiy menyuga xush kelibsiz!");
        sm.setReplyMarkup(getMainUserMenu(chatId).getReplyMarkup());
        return sm;
    }

    public SendMessage handleText(User user, String text) {
        Long chatId = user.getChatId();

        // Ism kiritilganda -> darhol telefon so'rash
        if (user.getState() == State.WAITING_FULL_NAME) {
            user.setFullName(text);
            user.setState(State.WAITING_PHONE);
            userRepository.save(user);
            return askPhone(chatId);
        }

        // Guruh tanlanganda -> darhol yo'nalish so'rash
        if (user.getState() == State.WAITING_SELECT_GROUP) {
            user.setSelectedGroup(text);
            user.setState(State.WAITING_SELECT_DIR);
            userRepository.save(user);
            return showDirections(chatId);
        }

        // Yo'nalish tanlanganda -> Asosiy menyu
        if (user.getState() == State.WAITING_SELECT_DIR) {
            user.setSelectedDirection(text);
            user.setState(State.FREE);
            userRepository.save(user);
            SendMessage sm = new SendMessage(chatId.toString(), "✅ Ma'lumotlaringiz muvaffaqiyatli saqlandi.");
            sm.setReplyMarkup(getMainUserMenu(chatId).getReplyMarkup());
            return sm;
        }

        // --- ASOSIY MENYU TUGMALARI ---
        switch (text) {
            case "📄 Namunalar":
                user.setState(State.VIEWING_SAMPLES);
                userRepository.save(user);
                return keyboardService.getSamplesReplyMenu(chatId);

            case "❓ Savol berish":
                user.setState(State.WAITING_QUESTION);
                userRepository.save(user);
                return keyboardService.getBackMenu(chatId, "✍️ Marhamat, savolingizni yozing:");

            case "📤 Fayl yuborish":
                if (user.getSelectedGroup() != null && user.getSelectedDirection() != null) {
                    user.setState(State.WAITING_FILE);
                    userRepository.save(user);
                    String msg = "✅ Ma'lumotlaringiz:\n" +
                            "🏢 Guruh: " + user.getSelectedGroup() + "\n" +
                            "📁 Yo'nalish: " + user.getSelectedDirection() + "\n\n" +
                            "📥 Marhamat, faylni (Document) yuboring:";
                    return keyboardService.getBackMenu(chatId, msg);
                } else {
                    return handleStart(user);
                }

            case "🔄 Ma'lumotlarni yangilash":
                user.setState(State.WAITING_FULL_NAME);
                userRepository.save(user);
                return keyboardService.getBackMenu(chatId, "🔄 Ma'lumotlarni yangilash uchun yangi ism-familiyangizni kiriting:");
            case "⬆️ Chiqish": // "Chiqish" tugmasi bosilganda ham shu mantiq ishlaydi
                user.setFullName(null);
                user.setPhoneNumber(null);
                user.setSelectedGroup(null);
                user.setSelectedDirection(null);
                userRepository.save(user); // Ma'lumotlarni bazada o'chirish
                return handleStart(user);
        }

        return getMainUserMenu(chatId);
    }

    public SendMessage handleContact(User user, Contact contact) {
        user.setPhoneNumber(contact.getPhoneNumber());
        user.setState(State.WAITING_SELECT_GROUP);
        userRepository.save(user);
        return showGroups(user.getChatId());
    }

    public SendMessage getMainUserMenu(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "Menyuni tanlang:");
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
        SendMessage sm = new SendMessage(chatId.toString(), "📱 Telefon raqamingizni yuboring:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        KeyboardButton btn = new KeyboardButton("📱 Kontaktni ulash");
        btn.setRequestContact(true);
        markup.setKeyboard(List.of(new KeyboardRow(List.of(btn))));
        sm.setReplyMarkup(markup);
        return sm;
    }
}