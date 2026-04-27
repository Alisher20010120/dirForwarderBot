package com.example.dirforwarderbot.service;

import com.example.dirforwarderbot.entity.*;
import com.example.dirforwarderbot.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.io.FileInputStream;
import java.io.File;
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

    /**
     * Inline tugmalar bosilganda ishlovchi metod
     */
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

        // Guruhning Target Chat ID sini o'zgartirishni boshlash
        if (data.startsWith("edit_group_id_")) {
            String groupName = data.replace("edit_group_id_", "");
            user.setTempData(groupName);
            user.setState(State.WAITING_TARGET_CHAT_ID);
            userRepository.save(user);
            return keyboardService.getBackMenu(chatId,
                    "🆔 **" + groupName + "** guruhi uchun yangi Target Chat ID yuboring:");
        }

        // Namunani o'chirish
        if (data.startsWith("del_sample_")) {
            Long id = Long.parseLong(data.replace("del_sample_", ""));
            sampleRepository.deleteById(id);
            return keyboardService.getSampleList(chatId);
        }

        // Adminni vazifasidan bo'shatish (Super Admin)
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

    /**
     * Admin paneldagi matnli xabarlarga ishlovchi metod
     */
    public SendMessage handleText(User user, String text) {
        Long chatId = user.getChatId();

        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) return null;

        // 1. "Orqaga" tugmasi bosilganda har doim asosiy admin menyusiga qaytarish
        if (text.equals("⬅️ Orqaga")) {
            user.setState(State.ADMIN_MENU);
            user.setTempData(null);
            userRepository.save(user);
            return keyboardService.getAdminReplyMenu(user);
        }

        // 2. Admin qo'shish jarayoni (Chat ID kutish)
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
                    return keyboardService.getAdminReplyMenu(user);
                }
                return keyboardService.getBackMenu(chatId, "❌ Chat ID topilmadi. Qayta kiriting:");
            } catch (Exception e) {
                return keyboardService.getBackMenu(chatId, "⚠️ Faqat raqamli Chat ID kiriting:");
            }
        }

        // 3. ASOSIY SWITCH - Tugmalar bosilganda ishlaydi
        switch (text) {
            case "📢 Xabarnoma yuborish":
                user.setState(State.WAITING_BROADCAST);
                userRepository.save(user);
                // MUHIM: Bu yerda albatta getBackMenu qaytarish kerak, shunda "Orqaga" tugmasi chiqadi
                return keyboardService.getBackMenu(chatId, "📢 Barcha foydalanuvchilarga yuboriladigan xabarnoma matnini kiriting:");

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
                    return keyboardService.getBackMenu(chatId, "👤 Admin Chat ID sini kiriting:");
                }
                break;

            case "📂 Namuna qo'shish":
                user.setState(State.WAITING_SAMPLE_NAME);
                userRepository.save(user);
                return keyboardService.getBackMenu(chatId, "📝 Namuna nomini kiriting:");

            case "📥 Excel orqali User qo'shish":
                user.setState(State.WAITING_EXCEL_FILE);
                userRepository.save(user);
                return keyboardService.getBackMenu(chatId, "📁 Excel (.xlsx) faylini yuboring:");

            case "🔝 Chiqish":
                user.setState(State.FREE);
                userRepository.save(user);
                return userService.handleStart(user);
        }

        // 4. Boshqa holatlar (Namuna nomi, Guruh tanlash va h.k.)
        if (user.getState() == State.WAITING_SAMPLE_NAME) {
            user.setTempData(text.trim());
            user.setState(State.WAITING_SAMPLE_FILE);
            userRepository.save(user);
            return keyboardService.getBackMenu(chatId, "📥 Endi ushbu namuna uchun faylni yuboring:");
        }

        if (user.getState() == State.WAITING_GROUP_SELECT) {
            String cleanName = text.replace(" ✅", "").replace(" ⚠️", "").trim();
            user.setTempData(cleanName);
            user.setState(State.WAITING_TARGET_CHAT_ID);
            userRepository.save(user);
            return keyboardService.getBackMenu(chatId, "🆔 \"" + cleanName + "\" uchun Target Chat ID kiriting:");
        }

        if (user.getState() == State.WAITING_TARGET_CHAT_ID) {
            Group group = groupRepository.findByName(user.getTempData()).orElse(new Group());
            group.setName(user.getTempData());
            group.setTargetChatId(text.trim());
            groupRepository.save(group);
            user.setState(State.ADMIN_MENU);
            user.setTempData(null);
            userRepository.save(user);
            return keyboardService.getAdminReplyMenu(user);
        }

        return null;
    }

    /**
     * Excel fayldan userlarni sinxronizatsiya qilib yuklash
     */
    public void importUsersFromExcel(File file) {
        Set<String> excelPhones = new HashSet<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String fullName = getCellValue(row.getCell(0)).trim();
                String direction = getCellValue(row.getCell(1)).trim();
                String groupName = getCellValue(row.getCell(2)).trim();
                String phone = getCellValue(row.getCell(3)).replaceAll("[^0-9]", "");

                if (phone.isEmpty() || fullName.isEmpty()) continue;
                excelPhones.add(phone);

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

                if (!groupName.isEmpty() && groupRepository.findByName(groupName).isEmpty()) {
                    Group g = new Group();
                    g.setName(groupName);
                    groupRepository.save(g);
                }
            }

            // Excelda yo'q USERlarni o'chirish (Sinxronizatsiya)
            userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.USER && !excelPhones.contains(u.getPhoneNumber()))
                    .forEach(userRepository::delete);

        } catch (Exception e) {
            throw new RuntimeException("Excel xatosi: " + e.getMessage());
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return cell.getStringCellValue().trim();
    }
}