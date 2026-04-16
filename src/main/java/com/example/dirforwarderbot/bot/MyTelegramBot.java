package com.example.dirforwarderbot.bot;

import com.example.dirforwarderbot.entity.Role;
import com.example.dirforwarderbot.entity.State;
import com.example.dirforwarderbot.entity.User;
import com.example.dirforwarderbot.entity.Group;
import com.example.dirforwarderbot.repository.GroupRepository;
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

    public MyTelegramBot(@Value("${bot.token}") String token,
                         UserRepository userRepository,
                         AdminService adminService,
                         UserService userService, GroupRepository groupRepository, KeyboardService keyboardService) {
        super(token);
        this.userRepository = userRepository;
        this.adminService = adminService;
        this.userService = userService;
        this.groupRepository = groupRepository;
        this.keyboardService = keyboardService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            Long chatId = extractChatId(update);
            if (chatId == null) return;

            // --- 1. GURUHDA ISHLASH MANTIQI ---
            if (update.hasMessage() && (update.getMessage().isGroupMessage() || update.getMessage().isSuperGroupMessage())) {
                if (update.getMessage().getReplyToMessage() != null) {
                    Long senderId = update.getMessage().getFrom().getId();
                    userRepository.findByChatId(senderId).ifPresent(u -> {
                        // Admin YOKI Super Admin reply qilsa javob yuborilsin
                        if (u.getRole() == Role.ADMIN || u.getRole() == Role.SUPER_ADMIN) {
                            handleAdminReply(update);
                        }
                    });
                }
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
                if (update.getMessage().hasText()) {
                    String text = update.getMessage().getText();


                    if (text.equals("/start")) {
                        send(userService.handleStart(user));
                    }
                    else if (text.equals("/admin")) {
                        if (user.getRole() == Role.SUPER_ADMIN) {
                            user.setState(State.ADMIN_MENU);
                            userRepository.save(user);
                            send(keyboardService.getAdminReplyMenu(user));
                        } else if (user.getRole() == Role.ADMIN) {
                            user.setState(State.ADMIN_MENU);
                            userRepository.save(user);
                            send(keyboardService.getAdminReplyMenu(user));
                        } else {
                            send(new SendMessage(chatId.toString(), "🔐 Admin parolini kiriting:"));
                            user.setState(State.WAITING_PASSWORD);
                            userRepository.save(user);
                        }
                    }
                    else if (text.equals("❓ Savol berish")) {
                        user.setState(State.WAITING_QUESTION);
                        userRepository.save(user);
                        send(new SendMessage(chatId.toString(), "✍️ Marhamat, savolingizni yozing. Adminlarimizga yetkazamiz:"));
                    }
                    else {
                        if (user.getState() == State.WAITING_QUESTION) {
                            handleQuestionToAdmin(update, user);
                        }
                        else if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN || user.getState() == State.WAITING_PASSWORD) {
                            send(adminService.handleText(user, text));
                        }
                        else {
                            send(userService.handleText(user, text));
                        }
                    }
                }
                else if (update.getMessage().hasContact() && user.getState() == State.WAITING_PHONE) {
                    send(userService.handleContact(user, update.getMessage().getContact()));
                }
                else if (update.getMessage().hasDocument() && user.getState() == State.WAITING_FILE) {
                    handleFileForwarding(update, user);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleFileForwarding(Update update, User user) {
        try {
            Optional<Group> groupOpt = groupRepository.findByName(user.getSelectedGroup());
            if (groupOpt.isEmpty()) {
                send(new SendMessage(user.getChatId().toString(), "❌ Guruh topilmadi!"));
                return;
            }

            String targetChatId = groupOpt.get().getTargetChatId();

            SendDocument sd = new SendDocument();
            sd.setChatId(targetChatId);
            sd.setDocument(new InputFile(update.getMessage().getDocument().getFileId()));

            String caption = "<b>📄 Yangi topshiriq!</b>\n\n" +
                    "👤 <b>Talaba:</b> " + user.getFullName() + "\n" +
                    "📁 <b>Yo'nalish:</b> " + user.getSelectedDirection() + "\n" +
                    "📞 <b>Tel:</b> " + user.getPhoneNumber() + "\n\n" +
                    "<code>#user_" + user.getChatId() + "</code>";

            sd.setCaption(caption);
            sd.setParseMode("HTML");
            execute(sd);

            SendMessage confirmation = new SendMessage();
            confirmation.setChatId(user.getChatId().toString());
            confirmation.setText("✅ <b>Faylingiz adminga yuborildi!</b>\n\nTez orada ko'rib chiqiladi va sizga javob qaytariladi.");
            confirmation.setParseMode("HTML");
            execute(confirmation);

            user.setState(State.FREE);
            userRepository.save(user);
            send(userService.getMainUserMenu(user.getChatId()));

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleAdminReply(Update update) {
        try {
            var replyTo = update.getMessage().getReplyToMessage();
            String caption = replyTo.getCaption();
            
            if (caption == null && replyTo.hasText()) caption = replyTo.getText();

            if (caption != null && caption.contains("#user_")) {
                int start = caption.indexOf("#user_") + 6;
                String idStr = caption.substring(start).replaceAll("[^0-9]", "");
                Long targetUserId = Long.parseLong(idStr);

                SendMessage sm = new SendMessage();
                sm.setChatId(targetUserId.toString());
                sm.setText("<b>👨‍🏫 Admin javobi:</b>\n\n" + update.getMessage().getText() +
                        "\n\n<i>Tushunarsiz joyi bo'lsa, quyidagi tugmani bosib savol yuborishingiz mumkin:</i>");
                sm.setParseMode("HTML");

                ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
                markup.setResizeKeyboard(true);
                markup.setOneTimeKeyboard(true);
                markup.setKeyboard(List.of(new KeyboardRow(List.of(new KeyboardButton("❓ Savol berish")))));
                sm.setReplyMarkup(markup);

                execute(sm);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleQuestionToAdmin(Update update, User user) {
        try {
            Optional<Group> groupOpt = groupRepository.findByName(user.getSelectedGroup());
            if (groupOpt.isPresent()) {
                SendMessage sm = new SendMessage();
                sm.setChatId(groupOpt.get().getTargetChatId());
                sm.setText("<b>❓ Yangi savol keldi!</b>\n\n" +
                        "👤 <b>Talaba:</b> " + user.getFullName() + "\n" +
                        "💬 <b>Savol:</b> " + update.getMessage().getText() + "\n\n" +
                        "<code>#user_" + user.getChatId() + "</code>");
                sm.setParseMode("HTML");
                execute(sm);

                SendMessage confirmation = new SendMessage(user.getChatId().toString(), "✅ Savolingiz yuborildi. Adminlarimiz javob berishini kuting.");
                confirmation.setParseMode("HTML");
                send(confirmation);

                user.setState(State.FREE);
                userRepository.save(user);
                send(userService.getMainUserMenu(user.getChatId()));
            }
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
        if (chatId.equals(913491692L)||chatId.equals(7705709414L)) {
            n.setRole(Role.SUPER_ADMIN);
        } else {
            n.setRole(Role.USER);
        }
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