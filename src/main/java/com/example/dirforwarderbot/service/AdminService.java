package com.example.dirforwarderbot.service;

import com.example.dirforwarderbot.entity.*;
import com.example.dirforwarderbot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;


import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final DirectionRepository directionRepository;
    private final GroupRepository groupRepository;
    private final KeyboardService keyboardService;

    public SendMessage handleCallback(User user, String data) {
        Long chatId = user.getChatId();

        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) return null;

        if (data.equals("view_dirs")) return keyboardService.getDirectionList(chatId);
        if (data.equals("view_groups")) return keyboardService.getGroupList(chatId);
        if (data.equals("settings")) return keyboardService.getSettingsMenu(chatId);

        if (data.startsWith("del_dir_")) {
            Long id = Long.parseLong(data.replace("del_dir_", ""));
            directionRepository.deleteById(id);
            return keyboardService.getDirectionList(chatId);
        }

        if (data.startsWith("del_group_")) {
            Long id = Long.parseLong(data.replace("del_group_", ""));
            groupRepository.deleteById(id);
            return keyboardService.getGroupList(chatId);
        }

        return null;
    }

    public SendMessage handleText(User user, String text) {
        Long chatId = user.getChatId();

        if (user.getState() == State.WAITING_PASSWORD) {
            if (text.equals("123")) {
                user.setRole(Role.ADMIN);
                user.setState(State.ADMIN_MENU);
                userRepository.save(user);
                return keyboardService.getAdminReplyMenu(user);
            }
            user.setState(State.FREE);
            userRepository.save(user);
            return new SendMessage(chatId.toString(), "❌ Parol noto'g'ri!");
        }

        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) return null;

        if (user.getState() == State.WAITING_ADMIN_ID && user.getRole() == Role.SUPER_ADMIN) {
            try {
                Long targetChatId = Long.parseLong(text.trim());
                Optional<User> targetUserOpt = userRepository.findByChatId(targetChatId);

                if (targetUserOpt.isPresent()) {
                    User targetUser = targetUserOpt.get();
                    targetUser.setRole(Role.ADMIN);
                    userRepository.save(targetUser);

                    user.setState(State.ADMIN_MENU);
                    userRepository.save(user);

                    return new SendMessage(chatId.toString(), "✅ <b>" + targetUser.getFullName() + "</b> muvaffaqiyatli ADMIN qilindi!");
                } else {
                    return new SendMessage(chatId.toString(), "❌ Xato: Bu Chat ID bazada topilmadi. Avval u odam botga start bosgan bo'lishi kerak.");
                }
            } catch (NumberFormatException e) {
                return new SendMessage(chatId.toString(), "⚠️ Iltimos, faqat raqamlardan iborat Chat ID yuboring.");
            }
        }

        switch (text) {
            case "➕ Yo'nalish qo'shish":
                user.setState(State.WAITING_DIR_NAME);
                userRepository.save(user);
                return new SendMessage(chatId.toString(), "📝 Yangi yo'nalish nomini kiriting:");
            case "👥 Guruh qo'shish":
                user.setState(State.WAITING_GROUP_NAME);
                userRepository.save(user);
                return new SendMessage(chatId.toString(), "👥 Yangi guruh nomini kiriting:");
            case "⚙️ Sozlamalar":
                return keyboardService.getSettingsMenu(chatId);
            case "➕ Admin qo'shish":
                if (user.getRole() == Role.SUPER_ADMIN) {
                    user.setState(State.WAITING_ADMIN_ID);
                    userRepository.save(user);
                    return new SendMessage(chatId.toString(), "👤 Admin qilmoqchi bo'lgan foydalanuvchining Chat ID raqamini kiriting:");
                }
                break;
            case "🔝 Chiqish":
                user.setState(State.FREE);
                if (user.getRole() == Role.ADMIN) user.setRole(Role.USER);
                userRepository.save(user);
                return new SendMessage(chatId.toString(), "Siz admin paneldan chiqdingiz.");
        }

        if (user.getState() == State.WAITING_DIR_NAME) {
            Directions dir = new Directions();
            dir.setName(text);
            directionRepository.save(dir);
            user.setState(State.ADMIN_MENU);
            userRepository.save(user);
            return keyboardService.getAdminReplyMenu(user);
        }

        if (user.getState() == State.WAITING_GROUP_NAME) {
            user.setSelectedGroup(text);
            user.setState(State.WAITING_TARGET_CHAT_ID);
            userRepository.save(user);
            return new SendMessage(chatId.toString(), "🆔 \"" + text + "\" guruhi uchun Target Chat ID ni yuboring:");
        }

        if (user.getState() == State.WAITING_TARGET_CHAT_ID) {
            Group group = new Group();
            group.setName(user.getSelectedGroup());
            group.setTargetChatId(text);
            groupRepository.save(group);
            user.setState(State.ADMIN_MENU);
            user.setSelectedGroup(null);
            userRepository.save(user);
            return keyboardService.getAdminReplyMenu(user);
        }

        return null;
    }
}