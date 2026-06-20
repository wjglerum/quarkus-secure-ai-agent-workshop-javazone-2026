package org.acme;

import dev.langchain4j.guardrail.GuardrailException;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.faulttolerance.api.RateLimitException;
import jakarta.inject.Inject;
import org.acme.ratelimit.ConsumptionGuard;

@Authenticated
@WebSocket(path = "/chat-bot")
public class ChatBotWebSocket {

    @Inject
    SecurityIdentity identity;

    @Inject
    ConsumptionGuard consumptionGuard;

    private final ChatBot chatBot;

    public ChatBotWebSocket(ChatBot chatBot) {
        this.chatBot = chatBot;
    }

    @OnOpen
    public String onOpen() {
        return "Hi " + identity.getPrincipal().getName() + "! Welcome to the JavaZone conference assistant. What can I help you with?";
    }

    @OnTextMessage
    public String onTextMessage(String message) {
        try {
            consumptionGuard.enforce();
            return chatBot.chat(message);
        } catch (RateLimitException e) {
            return "You are sending messages too quickly. Please wait a moment and try again.";
        } catch (GuardrailException e) {
            String msg = e.getMessage();
            int idx = msg.indexOf("failed with this message: ");
            return idx >= 0 ? msg.substring(idx + "failed with this message: ".length()) : msg;
        }
    }
}
