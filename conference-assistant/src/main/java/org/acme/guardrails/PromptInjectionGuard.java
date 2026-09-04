package org.acme.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PromptInjectionGuard implements InputGuardrail {

    @ConfigProperty(name = "guardrails.injection.phrases")
    String suspiciousCsv;

    @Override
    public InputGuardrailResult validate(UserMessage um) {
        String text = um.singleText();
        if (text == null || text.isBlank()) {
            return success();
        }

        String lower = text.toLowerCase();
        for (String raw : suspiciousCsv.split(",")) {
            String phrase = raw.trim().toLowerCase();
            if (!phrase.isEmpty() && lower.contains(phrase)) {
                return fatal("Your message contains a restricted phrase and cannot be processed.");
            }
        }
        return success();
    }
}
