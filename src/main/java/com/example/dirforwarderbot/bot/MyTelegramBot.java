package com.example.dirforwarderbot.bot;

import com.example.dirforwarderbot.entity.*;
import com.example.dirforwarderbot.repository.GroupRepository;
import com.example.dirforwarderbot.repository.SampleRepository;
import com.example.dirforwarderbot.repository.UserRepository;
import com.example.dirforwarderbot.service.AdminService;
import com.example.dirforwarderbot.service.KeyboardService;
import com.example.dirforwarderbot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;


import java.util.Optional;

@Component
public class MyTelegramBot extends TelegramLongPollingBot {

    private final UserRepository userRepository;
    private final AdminService adminService;
    private final UserService userService;
    private final GroupRepository groupRepository;
    private final KeyboardService keyboardService;
    private final SampleRepository sampleRepository;

    public MyTelegramBot(@Value("${bot.token}") String token,
                         UserRepository userRepository,
                         AdminService adminService,
                         UserService userService,
                         GroupRepository groupRepository,
                         KeyboardService keyboardService,
                         SampleRepository sampleRepository) {
        super(token);
        this.userRepository = userRepository;
        this.adminService = adminService;
        this.userService = userService;
        this.groupRepository = groupRepository;
        this.keyboardService = keyboardService;
        this.sampleRepository = sampleRepository;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            Long chatId = extractChatId(update);
            if (chatId == null) return;

            if (update.hasMessage() && (update.getMessage().isGroupMessage() || update.getMessage().isSuperGroupMessage())) {
                if (update.getMessage().getReplyToMessage() != null) handleAdminReply(update);
                return;
            }

            User user = userRepository.findByChatId(chatId).orElseGet(() -> createNewUser(update, chatId));

            if (update.hasCallbackQuery()) {
                if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN) {
                    send(adminService.handleCallback(user, update.getCallbackQuery().getData()));
                }
                return;
            }

            if (update.hasMessage()) {

                if (update.getMessage().hasContact() && user.getState() == State.WAITING_PHONE) {
                    send(userService.handleContact(user, update.getMessage().getContact()));
                    return;
                }

                if (update.getMessage().hasDocument()) {
                    if (user.getState() == State.WAITING_SAMPLE_FILE) {
                        saveSampleFile(update, user);
                    } else if (user.getState() == State.WAITING_FILE) {
                        handleFileForwarding(update, user);
                    }
                    else if (user.getState() == State.WAITING_EXCEL_FILE &&
                            (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN)) {
                        handleExcelImport(update, user);
                    }
                    return;
                }

                if (update.getMessage().hasText()) {
                    String text = update.getMessage().getText();

                    if (text.equals("/start")) {
                        send(userService.handleStart(user));
                        return;
                    }

                    if (text.equals("⬅️ Orqaga")) {
                        user.setState(State.FREE);
                        userRepository.save(user);
                        send(userService.getMainUserMenu(chatId));
                        return;
                    }

                    if (text.equals("/admin")) {
                        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN) {
                            user.setState(State.ADMIN_MENU);
                            userRepository.save(user);
                            send(keyboardService.getAdminReplyMenu(user));
                        } else {
                            send(new SendMessage(chatId.toString(), "❌ Kechirasiz, sizda admin huquqi yo'q!"));
                        }
                        return;
                    }


                    if (user.getState() == State.WAITING_FULL_NAME ||
                            user.getState() == State.WAITING_SELECT_GROUP ||
                            user.getState() == State.WAITING_SELECT_DIR) {
                        send(userService.handleText(user, text));
                        return;
                    }

                    if (user.getState() == State.WAITING_QUESTION) {
                        handleQuestionToAdmin(update, user);
                        return;
                    }

                    if (user.getState() == State.VIEWING_SAMPLES) {
                        handleSampleRequest(user, text);
                        return;
                    }

                    if ((user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN) && user.getState() != State.FREE) {
                        send(adminService.handleText(user, text));
                        return;
                    }

                    send(userService.handleText(user, text));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleExcelImport(Update update, User user) {
        try {
            String fileId = update.getMessage().getDocument().getFileId();
            String fileName = update.getMessage().getDocument().getFileName();

            if (fileName != null && !fileName.endsWith(".xlsx")) {
                send(new SendMessage(user.getChatId().toString(), "⚠️ Iltimos, faqat Excel (.xlsx) fayl yuboring."));
                return;
            }

            org.telegram.telegrambots.meta.api.methods.GetFile getFile = new org.telegram.telegrambots.meta.api.methods.GetFile();
            getFile.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);

            java.io.File downloadedFile = downloadFile(file);

            adminService.importUsersFromExcel(downloadedFile);

            user.setState(State.ADMIN_MENU);
            userRepository.save(user);

            send(new SendMessage(user.getChatId().toString(), "✅ Exceldagi foydalanuvchilar muvaffaqiyatli bazaga yuklandi!"));
            send(keyboardService.getAdminReplyMenu(user));

        } catch (Exception e) {
            e.printStackTrace();
            send(new SendMessage(user.getChatId().toString(), "❌ Xatolik yuz berdi: " + e.getMessage()));
        }
    }

    private void handleSampleRequest(User user, String text) {
        if (text.equals("⬅️ Orqaga")) {
            user.setState(State.FREE);
            userRepository.save(user);
            send(userService.getMainUserMenu(user.getChatId()));
            return;
        }

        sampleRepository.findByDisplayName(text).ifPresentOrElse(
                sample -> {
                    SendDocument sd = new SendDocument();
                    sd.setChatId(user.getChatId().toString());
                    sd.setDocument(new InputFile(sample.getFileId()));
                    sd.setCaption("📄 " + sample.getDisplayName());
                    try {
                        execute(sd);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                () -> send(new SendMessage(user.getChatId().toString(), "⚠️ Fayl topilmadi."))
        );
    }


    private void saveSampleFile(Update update, User user) {
        String fileId = update.getMessage().getDocument().getFileId();
        Sample sample = new Sample();
        sample.setDisplayName(user.getTempData());
        sample.setFileId(fileId);
        sampleRepository.save(sample);

        user.setState(State.ADMIN_MENU);
        user.setTempData(null);
        userRepository.save(user);

        send(new SendMessage(user.getChatId().toString(), "✅ Yangi namuna muvaffaqiyatli saqlandi!"));
        send(keyboardService.getAdminReplyMenu(user));
    }

    private void handleFileForwarding(Update update, User user) {
        try {
            Optional<Group> groupOpt = groupRepository.findByName(user.getSelectedGroup());
            if (groupOpt.isEmpty()) {
                send(new SendMessage(user.getChatId().toString(), "❌ Guruh topilmadi!"));
                return;
            }

            SendDocument sd = new SendDocument();
            sd.setChatId(groupOpt.get().getTargetChatId());
            sd.setDocument(new InputFile(update.getMessage().getDocument().getFileId()));
            sd.setCaption("<b>📄 Yangi topshiriq!</b>\n\n👤 Talaba: " + user.getFullName() +
                    "\n📁 Yo'nalish: " + user.getSelectedDirection() +
                    "\n📞 Tel: " + user.getPhoneNumber() +
                    "\n\n<code>#user_" + user.getChatId() + "</code>");
            sd.setParseMode("HTML");
            execute(sd);

            send(new SendMessage(user.getChatId().toString(), "✅ Faylingiz adminga yuborildi!"));
            user.setState(State.FREE);
            userRepository.save(user);
            send(userService.getMainUserMenu(user.getChatId()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleAdminReply(Update update) {
        try {
            var replyTo = update.getMessage().getReplyToMessage();
            String content = replyTo.getCaption() != null ? replyTo.getCaption() : replyTo.getText();
            if (content != null && content.contains("#user_")) {
                int start = content.indexOf("#user_") + 6;
                String idStr = content.substring(start).replaceAll("[^0-9]", "");
                Long targetUserId = Long.parseLong(idStr);

                SendMessage sm = new SendMessage();
                sm.setChatId(targetUserId.toString());
                sm.setText("<b>👨‍🏫 Admin javobi:</b>\n\n" + update.getMessage().getText());
                sm.setParseMode("HTML");
                execute(sm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleQuestionToAdmin(Update update, User user) {
        try {
            Optional<Group> groupOpt = groupRepository.findByName(user.getSelectedGroup());
            String targetId = groupOpt.isPresent() ? groupOpt.get().getTargetChatId() : "ADMIN_CHAT_ID"; // Default admin chat

            SendMessage sm = new SendMessage();
            sm.setChatId(targetId);
            sm.setText("<b>❓ Yangi savol!</b>\n\n👤 Talaba: " + user.getFullName() +
                    "\n💬 Savol: " + update.getMessage().getText() +
                    "\n\n<code>#user_" + user.getChatId() + "</code>");
            sm.setParseMode("HTML");
            execute(sm);

            send(new SendMessage(user.getChatId().toString(), "✅ Savolingiz yuborildi."));
            user.setState(State.FREE);
            userRepository.save(user);
            send(userService.getMainUserMenu(user.getChatId()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Long extractChatId(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getMessage().getChatId();
        return null;
    }

    private User createNewUser(Update update, Long chatId) {
        User n = new User();
        n.setChatId(chatId);
        n.setRole(chatId.equals(913491692L) ? Role.SUPER_ADMIN : Role.USER);
        n.setState(State.FREE);
        n.setUsername(update.getMessage().getFrom().getUserName());
        return userRepository.save(n);
    }

    public void send(SendMessage sm) {
        if (sm == null) return;
        try {
            execute(sm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "SIZNING_BOT_USERNAMINGIZ";
    }
}