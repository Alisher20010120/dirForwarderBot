package com.example.dirforwarderbot.command;

import com.example.dirforwarderbot.command.BotCommand;
import com.example.dirforwarderbot.entity.Role;
import com.example.dirforwarderbot.entity.State;
import com.example.dirforwarderbot.entity.User;
import com.example.dirforwarderbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class AdminCommand implements BotCommand {

    private final UserRepository userRepository;
    
    private final Long SUPER_ADMIN_ID = 913491692L;

    @Override
    public SendMessage execute(Update update) {
        Long chatId = update.getMessage().getChatId();

        User user = userRepository.findByChatId(chatId).orElseGet(() -> {
            User newUser = new User();
            newUser.setChatId(chatId);
            newUser.setRole(Role.USER);
            return userRepository.save(newUser);
        });

        if (chatId.equals(SUPER_ADMIN_ID) || user.getRole() == Role.ADMIN) {
            
            user.setState(State.WAITING_PASSWORD);
            userRepository.save(user);

            return new SendMessage(chatId.toString(), "🔐 Admin panelga xush kelibsiz! Parolni kiriting:");
        } else {
            return new SendMessage(chatId.toString(), "Sizda ushbu komandadan foydalanish huquqi yo'q! 🛑");
        }
    }

    @Override
    public String getCommandName() {
        return "/admin";
    }
}