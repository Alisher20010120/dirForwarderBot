package com.example.dirforwarderbot.service;

import com.example.dirforwarderbot.entity.Role;
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

        boolean isAdmin = user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
        if (isAdmin && user.getPhoneNumber() != null) {
            user.setState(State.FREE);
            userRepository.save(user);
            return getMainUserMenu(user);
        }

        user.setState(State.WAITING_PHONE);
        userRepository.save(user);
        return askPhone(chatId);
    }

    public SendMessage handleContact(User user, Contact contact) {
        Long chatId = user.getChatId();
        String phone = contact.getPhoneNumber().replaceAll("[^0-9]", "");

        User dbUser = userRepository.findByPhoneNumber(phone).orElse(null);

        if (dbUser == null) {
            user.setState(State.FREE);
            userRepository.save(user);
            SendMessage sm = new SendMessage(chatId.toString(),
                    "❌ Kechirasiz, sizning raqamingiz tizimda topilmadi.\n\n" +
                            "📞 Iltimos, admin bilan bog'laning:\n@yordamchi10\n@ilmiy_konsultant1");
            sm.setReplyMarkup(new ReplyKeyboardRemove(true));
            return sm;
        }

        if (user.getId().equals(dbUser.getId())) {
            user.setChatId(chatId);
            user.setState(State.FREE);
            userRepository.save(user);
        } else {
            dbUser.setChatId(chatId);
            dbUser.setState(State.FREE);

            if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.ADMIN) {
                dbUser.setRole(user.getRole());
            }

            userRepository.save(dbUser);

            userRepository.delete(user);

            user = dbUser;
        }

        SendMessage sm = new SendMessage(chatId.toString(),
                "✅ Xush kelibsiz, <b>" + user.getFullName() + "</b>!\n" +
                        "🏢 Guruh: " + user.getSelectedGroup() + "\n" +
                        "📁 Yo'nalish: " + user.getSelectedDirection());
        sm.enableHtml(true);
        sm.setReplyMarkup(getMainUserMenu(user).getReplyMarkup());
        return sm;
    }

    public SendMessage handleText(User user, String text) {
        Long chatId = user.getChatId();

        if (user.getPhoneNumber() == null) {
            return handleStart(user);
        }

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
                    return new SendMessage(chatId.toString(),
                            "⚠️ Guruh yoki yo'nalish ma'lumotlaringiz topilmadi.\n" +
                                    "📞 Iltimos, admin bilan bog'laning:\n@yordamchi10\n@ilmiy_konsultant1");
                }

            case "🔑 Admin panel":
                if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN) {
                    user.setState(State.ADMIN_MENU);
                    userRepository.save(user);
                    return keyboardService.getAdminReplyMenu(user);
                }
                break;

            case "⬅️ Orqaga":
                return getMainUserMenu(user);

            default:
                return getMainUserMenu(user);
        }

        return getMainUserMenu(user);
    }

    public SendMessage getMainUserMenu(User user) {
        Long chatId = user.getChatId();
        SendMessage sm = new SendMessage(chatId.toString(), "📋 Menyuni tanlang:");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📤 Fayl yuborish"));
        row1.add(new KeyboardButton("📄 Namunalar"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("❓ Savol berish"));

        keyboard.add(row1);
        keyboard.add(row2);

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN) {
            KeyboardRow row3 = new KeyboardRow();
            row3.add(new KeyboardButton("🔑 Admin panel"));
            keyboard.add(row3);
        }

        markup.setKeyboard(keyboard);
        sm.setReplyMarkup(markup);
        return sm;
    }

    public SendMessage getMainUserMenu(Long chatId) {
        User user = userRepository.findByChatId(chatId).orElseGet(() -> {
            User dummy = new User();
            dummy.setChatId(chatId);
            dummy.setRole(Role.USER);
            return dummy;
        });
        return getMainUserMenu(user);
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
        SendMessage sm = new SendMessage(chatId.toString(),
                "👋 Assalomu alaykum!\n\n" +
                        "Botdan foydalanish uchun telefon raqamingizni ulashing 👇");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        KeyboardButton btn = new KeyboardButton("📱 Kontaktni ulash");
        btn.setRequestContact(true);
        markup.setKeyboard(List.of(new KeyboardRow(List.of(btn))));
        sm.setReplyMarkup(markup);
        return sm;
    }
}