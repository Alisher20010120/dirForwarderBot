package com.example.dirforwarderbot.service;

import com.example.dirforwarderbot.entity.*;
import com.example.dirforwarderbot.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.io.FileInputStream;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final KeyboardService keyboardService;
    private final SampleRepository sampleRepository;
    private final UserService userService;

    public SendMessage handleCallback(User user, String data) {
        Long chatId = user.getChatId();

        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) return null;

        if (data.equals("view_groups")) return keyboardService.getGroupList(chatId);
        if (data.equals("view_samples")) return keyboardService.getSampleList(chatId);
        
        // Super Admin uchun adminlarni ko'rish
        if (data.equals("view_admins") && user.getRole() == Role.SUPER_ADMIN) {
            return keyboardService.getAdminList(chatId);
        }
        
        if (data.equals("settings")) return keyboardService.getSettingsMenu(user);

        if (data.startsWith("edit_group_id_")) {
            String groupName = data.replace("edit_group_id_", "");
            user.setTempData(groupName);
            user.setState(State.WAITING_TARGET_CHAT_ID);
            userRepository.save(user);
            return new SendMessage(chatId.toString(),
                    "🆔 **" + groupName + "** guruhi uchun yangi Target Chat ID yuboring:");
        }
        
        if (data.startsWith("del_sample_")) {
            Long id = Long.parseLong(data.replace("del_sample_", ""));
            sampleRepository.deleteById(id);
            return keyboardService.getSampleList(chatId);
        }

        // Super Admin uchun adminni vazifasidan bo'shatish
        if (data.startsWith("demote_admin_") && user.getRole() == Role.SUPER_ADMIN) {
            Long adminId = Long.parseLong(data.replace("demote_admin_", ""));
            userRepository.findById(adminId).ifPresent(target -> {
                target.setRole(Role.USER);
                userRepository.save(target);
            });
            return keyboardService.getAdminList(chatId);
        }

        return null;
    }

    public SendMessage handleText(User user, String text) {
        Long chatId = user.getChatId();

        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) return null;

        // Super Admin tomonidan admin qo'shish
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
                    SendMessage sm = new SendMessage(chatId.toString(),
                            "✅ <b>" + targetUser.getFullName() + "</b> muvaffaqiyatli ADMIN qilindi!");
                    sm.enableHtml(true);
                    return sm;
                } else {
                    return new SendMessage(chatId.toString(), "❌ Xato: Bu Chat ID bazada topilmadi.");
                }
            } catch (NumberFormatException e) {
                return new SendMessage(chatId.toString(), "⚠️ Iltimos, faqat raqamlardan iborat Chat ID yuboring.");
            }
        }

        // Guruh tanlanganda targetChatId so'rash
        if (user.getState() == State.WAITING_GROUP_SELECT) {
            String cleanGroupName = text.replace(" ✅", "").replace(" ⚠️", "").trim();
            user.setTempData(cleanGroupName);
            user.setState(State.WAITING_TARGET_CHAT_ID);
            userRepository.save(user);
            return new SendMessage(chatId.toString(),
                    "🆔 \"" + cleanGroupName + "\" guruhi uchun Target Chat ID ni yuboring:");
        }

        switch (text) {
            case "👥 Guruh qo'shish":
                user.setState(State.WAITING_GROUP_SELECT);
                userRepository.save(user);
                return keyboardService.getGroupSelectMenu(chatId);

            case "⚙️ Sozlamalar":
                return keyboardService.getSettingsMenu(user);

            case "➕ Admin qo'shish":
                if (user.getRole() == Role.SUPER_ADMIN) {
                    user.setState(State.WAITING_ADMIN_ID);
                    userRepository.save(user);
                    return new SendMessage(chatId.toString(),
                            "👤 Admin qilmoqchi bo'lgan foydalanuvchining Chat ID raqamini kiriting:");
                }
                break;

            case "📂 Namuna qo'shish":
                user.setState(State.WAITING_SAMPLE_NAME);
                userRepository.save(user);
                return new SendMessage(chatId.toString(), "📝 Namuna nomini kiriting:");

            case "📥 Excel orqali User qo'shish":
                user.setState(State.WAITING_EXCEL_FILE);
                userRepository.save(user);
                return new SendMessage(chatId.toString(),
                        "Iltimos, foydalanuvchilar ro'yxati (.xlsx) faylini yuboring.");

            case "🔝 Chiqish":
                user.setState(State.FREE);
                userRepository.save(user);
                return userService.handleStart(user);
        }

        if (user.getState() == State.WAITING_SAMPLE_NAME) {
            user.setTempData(text.trim());
            user.setState(State.WAITING_SAMPLE_FILE);
            userRepository.save(user);
            return new SendMessage(chatId.toString(), "📥 Endi ushbu namuna uchun faylni yuboring:");
        }

        // targetChatId keldi — guruhni yangilaymiz
        if (user.getState() == State.WAITING_TARGET_CHAT_ID) {
            String groupName = user.getTempData();
            Group group = groupRepository.findByName(groupName).orElse(new Group());
            group.setName(groupName);
            group.setTargetChatId(text.trim());
            groupRepository.save(group);

            user.setState(State.ADMIN_MENU);
            user.setTempData(null);
            userRepository.save(user);
            return keyboardService.getAdminReplyMenu(user);
        }

        return null;
    }

    public void importUsersFromExcel(java.io.File file) {
        Set<String> excelPhones = new HashSet<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String fullName = getCellValue(row.getCell(0)).trim();
                String direction = getCellValue(row.getCell(1)).trim();
                String groupName = getCellValue(row.getCell(2)).trim();
                String rawPhone = getCellValue(row.getCell(3));
                String phone = rawPhone.replaceAll("[^0-9]", "");

                if (phone.isEmpty() || fullName.isEmpty()) continue;
                excelPhones.add(phone);

                // Dublikatni oldini olish: Avval telefon, keyin ism bo'yicha qidiramiz
                User user = userRepository.findByPhoneNumber(phone)
                        .orElseGet(() -> userRepository.findByFullName(fullName).orElse(null));

                if (user == null) {
                    user = new User();
                    user.setRole(Role.USER);
                    user.setState(State.FREE);
                }

                user.setFullName(fullName);
                user.setPhoneNumber(phone);
                user.setSelectedDirection(direction);
                user.setSelectedGroup(groupName);
                userRepository.save(user);

                // Guruhni avtomatik Group jadvaliga qo'shish
                if (!groupName.isEmpty()) {
                    if (groupRepository.findByName(groupName).isEmpty()) {
                        Group g = new Group();
                        g.setName(groupName);
                        groupRepository.save(g);
                    }
                }
            }

            // SINXRONIZATSIYA: Excelda yo'q USERlarni bazadan o'chirish
            List<User> dbUsers = userRepository.findAll();
            for (User dbUser : dbUsers) {
                if (dbUser.getRole() == Role.USER) {
                    if (!excelPhones.contains(dbUser.getPhoneNumber())) {
                        userRepository.delete(dbUser);
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Excelni qayta ishlashda xato: " + e.getMessage());
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            default: return "";
        }
    }
}