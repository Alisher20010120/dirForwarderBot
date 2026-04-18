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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
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

            // Guruh xabarlarini qayta ishlash (Admin javobi uchun)
            if (update.hasMessage() && (update.getMessage().isGroupMessage() || update.getMessage().isSuperGroupMessage())) {
                if (update.getMessage().getReplyToMessage() != null) {
                    handleAdminReply(update);
                }
                return;
            }

            User user = userRepository.findByChatId(chatId).orElseGet(() -> createNewUser(update, chatId));

            // Callback query (Inline tugmalar)
            if (update.hasCallbackQuery()) {
                if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN) {
                    send(adminService.handleCallback(user, update.getCallbackQuery().getData()));
                }
                return;
            }

            if (update.hasMessage()) {
                // MATNLI XABARLAR
                if (update.getMessage().hasText()) {
                    String text = update.getMessage().getText();

                    if (text.equals("/start")) {
                        send(userService.handleStart(user));
                    }
                    else if (text.equals("/admin")) {
                        handleAdminCommand(user, chatId);
                    }
                    else {
                        // Namunalar ko'rayotgan bo'lsa
                        if (user.getState() == State.VIEWING_SAMPLES) {
                            handleSampleRequest(user, text);
                        }
                        // Savol yuborayotgan bo'lsa
                        else if (user.getState() == State.WAITING_QUESTION) {
                            handleQuestionToAdmin(update, user);
                        }
                        // Admin xizmatlari
                        else if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN || user.getState() == State.WAITING_PASSWORD) {
                            send(adminService.handleText(user, text));
                        }
                        // Oddiy foydalanuvchi xizmatlari
                        else {
                            send(userService.handleText(user, text));
                        }
                    }
                }
                // KONTAKT YUBORILSA
                else if (update.getMessage().hasContact() && user.getState() == State.WAITING_PHONE) {
                    send(userService.handleContact(user, update.getMessage().getContact()));
                }
                // FAYL (DOCUMENT) YUBORILSA
                else if (update.getMessage().hasDocument()) {
                    if (user.getState() == State.WAITING_SAMPLE_FILE) {
                        saveSampleFile(update, user);
                    }
                    else if (user.getState() == State.WAITING_FILE) {
                        handleFileForwarding(update, user);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Namunani foydalanuvchiga yuborish
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
                    try { execute(sd); } catch (Exception e) { e.printStackTrace(); }
                },
                () -> send(new SendMessage(user.getChatId().toString(), "⚠️ Fayl topilmadi."))
        );
    }

    // Admin buyrug'ini boshqarish
    private void handleAdminCommand(User user, Long chatId) {
        if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.ADMIN) {
            user.setState(State.ADMIN_MENU);
            userRepository.save(user);
            send(keyboardService.getAdminReplyMenu(user));
        } else {
            send(new SendMessage(chatId.toString(), "🔐 Admin parolini kiriting:"));
            user.setState(State.WAITING_PASSWORD);
            userRepository.save(user);
        }
    }

    // Admin yangi namuna yuklaganda saqlash
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

    // Faylni guruhga yo'naltirish mantiqi (O'zgarmadi)
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Admin javobini foydalanuvchiga yuborish
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Savolni guruhga yuborish
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
        } catch (Exception e) { e.printStackTrace(); }
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
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public String getBotUsername() { return "SIZNING_BOT_USERNAMINGIZ"; }
}