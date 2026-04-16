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

        if (user.getState() == State.WAITING_FULL_NAME) {
            user.setFullName(text);
            user.setState(State.WAITING_PHONE);
            userRepository.save(user);
            return askPhone(chatId);
        }


        if (text.equals("❓ Savol berish")) {
            user.setState(State.WAITING_QUESTION);
            userRepository.save(user);
            return new SendMessage(user.getChatId().toString(), "Marhamat, savolingizni yozing. Adminlarimizga yetkazamiz:");
        }

        if (user.getState() == State.WAITING_QUESTION) {
            // Bu yerda foydalanuvchi yozgan savolni guruhga yuborish mantiqi ishlaydi
            // Bot klassidagi metodni chaqirish yoki shunchaki tasdiq qaytarish
            return null; // Bot klassida handleQuestionForwarding qilsak ma'qul
        }
        if (text.equals("📤 Fayl yuborish")) {
            user.setState(State.WAITING_SELECT_GROUP);
            userRepository.save(user);
            return showGroups(chatId);
        }

        if (user.getState() == State.WAITING_SELECT_GROUP) {
            user.setSelectedGroup(text);
            user.setState(State.WAITING_SELECT_DIR);
            userRepository.save(user);
            return showDirections(chatId);
        }

        if (user.getState() == State.WAITING_SELECT_DIR) {
            user.setSelectedDirection(text);
            user.setState(State.WAITING_FILE);
            userRepository.save(user);
            return new SendMessage(chatId.toString(), "✅ Guruh: " + user.getSelectedGroup() + 
                    "\n✅ Yo'nalish: " + text + "\n\n📥 Endi faylni yuboring:");
        }

        if (text.equals("🔄 Ma'lumotlarni yangilash")) {
            user.setState(State.WAITING_FULL_NAME);
            userRepository.save(user);
            return new SendMessage(chatId.toString(), "Ism va familiyangizni qayta kiriting:");
        }

        return getMainUserMenu(chatId);
    }

    public SendMessage handleContact(User user, Contact contact) {
        user.setPhoneNumber(contact.getPhoneNumber());
        user.setState(State.WAITING_SELECT_GROUP); 
        userRepository.save(user);
        return showGroups(user.getChatId());
    }

    public SendMessage showGroups(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "🏢 Guruhni tanlang:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of());
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

    public SendMessage getMainUserMenu(Long chatId) {
        SendMessage sm = new SendMessage(chatId.toString(), "Asosiy menyu:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("📤 Fayl yuborish"));
        row.add(new KeyboardButton("🔄 Ma'lumotlarni yangilash"));
        markup.setKeyboard(List.of(row));
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