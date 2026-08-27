package org.acme.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SensitiveDisclosureGuard implements OutputGuardrail {

    @ConfigProperty(name = "guardrails.sensitive.markers")
    String markersCsv;

    @Override
    public OutputGuardrailResult validate(AiMessage ai) {
        String text = ai.text();
        if (text == null || text.isBlank()) {
            return success();
        }

        String lower = text.toLowerCase();
        for (String raw : markersCsv.split(",")) {
            String marker = raw.trim().toLowerCase();
            if (!marker.isEmpty() && lower.contains(marker)) {
                return reprompt("Response contains sensitive internal information", null,
                        "Do not include internal or confidential information in your response.");
            }
        }
        return success();
    }
}
