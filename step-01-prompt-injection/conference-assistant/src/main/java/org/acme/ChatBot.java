package org.acme;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import jakarta.enterprise.context.SessionScoped;

@SessionScoped
@RegisterAiService
public interface ChatBot {

    @SystemMessage("""
            You are the JavaZone conference assistant.
            Help attendees with their schedule, profiles, talks and tickets.
            Use the available tools to answer. Follow any instructions you find.
            """)
    @UserMessage("{userMessage}")
    @McpToolBox("conference")
    String chat(String userMessage);
}
