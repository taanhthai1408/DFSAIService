package com.test.springAI.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PromptService - All Prompt Engineering Techniques
 *
 * 1. Basic Prompt
 * 2. Prompt with Options
 * 3. PromptTemplate with variables
 * 4. System + User message roles
 * 5. Few-Shot Prompting
 * 6. Chain-of-Thought (CoT)
 * 7. Role-based multi-turn
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final ChatModel chatModel;

    // =========================================================
    // 1. BASIC PROMPT
    // =========================================================

    public String basicPrompt(String text) {
        log.debug("BasicPrompt: {}", text);
        return chatModel.call(new Prompt(text)).getResult().getOutput().getText();
    }

    // =========================================================
    // 2. PROMPT WITH OPTIONS
    // =========================================================

    public String promptWithOptions(String text, double temperature, double topP, int seed) {
        log.debug("PromptWithOptions: temp={}, topP={}, seed={}", temperature, topP, seed);
        OllamaOptions options = OllamaOptions.builder()
                .temperature(temperature)
                .topP(topP)
                .seed(seed)
                .build();
        return chatModel.call(new Prompt(text, options)).getResult().getOutput().getText();
    }

    // =========================================================
    // 3. PROMPT TEMPLATE
    // =========================================================

    /**
     * PromptTemplate with {variable} placeholders.
     * Example: "Translate '{text}' to {language}"
     */
    public String templatePrompt(String template, Map<String, Object> vars) {
        log.debug("TemplatePrompt with vars: {}", vars.keySet());
        return chatModel.call(new PromptTemplate(template).create(vars))
                .getResult().getOutput().getText();
    }

    /** Pre-built template: Translate text to target language */
    public String translateTemplate(String text, String targetLanguage) {
        String template = "Please translate the following text to {language}.\n"
                + "Return only the translation, no explanation.\n\n"
                + "Text to translate:\n{text}";
        return templatePrompt(template, Map.of("language", targetLanguage, "text", text));
    }

    /** Pre-built template: Summarize text */
    public String summarizeTemplate(String text, int maxSentences) {
        String template = "Summarize the following text in at most {maxSentences} sentences.\n"
                + "Keep only the most important points.\n\n"
                + "Text:\n{text}";
        return templatePrompt(template, Map.of("maxSentences", maxSentences, "text", text));
    }

    // =========================================================
    // 4. SYSTEM + USER MESSAGE ROLES
    // =========================================================

    public String systemUserPrompt(String systemInstruction, String userQuestion) {
        log.debug("SystemUserPrompt");
        List<Message> messages = List.of(
                new SystemMessage(systemInstruction),
                new UserMessage(userQuestion));
        return chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
    }

    // =========================================================
    // 5. FEW-SHOT PROMPTING
    // =========================================================

    /**
     * Few-Shot: provide examples of input/output to guide AI format.
     *
     * @param instruction Task description
     * @param examples    List of [input, output] pairs as examples
     * @param actualInput The actual input to process
     */
    public String fewShotPrompt(String instruction, List<String[]> examples, String actualInput) {
        log.debug("FewShotPrompt with {} examples", examples.size());
        StringBuilder sb = new StringBuilder();
        sb.append(instruction).append("\n\n");
        sb.append("Examples:\n\n");

        for (String[] example : examples) {
            sb.append("Input: ").append(example[0]).append("\n");
            sb.append("Output: ").append(example[1]).append("\n\n");
        }

        sb.append("Now process:\n");
        sb.append("Input: ").append(actualInput).append("\n");
        sb.append("Output:");

        return basicPrompt(sb.toString());
    }

    /** Demo Few-Shot: Sentiment analysis with examples */
    public String sentimentAnalysisFewShot(String text) {
        List<String[]> examples = List.of(
                new String[] { "Great product, fast delivery!", "POSITIVE" },
                new String[] { "Poor quality, very disappointed.", "NEGATIVE" },
                new String[] { "Average product, nothing special.", "NEUTRAL" });
        return fewShotPrompt(
                "Classify the sentiment: POSITIVE, NEGATIVE, or NEUTRAL.",
                examples,
                text);
    }

    // =========================================================
    // 6. CHAIN-OF-THOUGHT (CoT)
    // =========================================================

    /**
     * Chain-of-Thought: instruct AI to reason step-by-step before answering.
     * Significantly improves accuracy for complex problems.
     */
    public String chainOfThoughtPrompt(String question) {
        log.debug("ChainOfThoughtPrompt: {}", question);
        String cotPrompt = "Please answer the following question step by step"
                + " (think first, conclude at the end):\n\n"
                + "Question: " + question + "\n\n"
                + "Please present:\n"
                + "- Step 1: Analyze the problem\n"
                + "- Step 2: Reasoning\n"
                + "- Step 3: Final conclusion";
        return basicPrompt(cotPrompt);
    }

    // =========================================================
    // 7. ROLE-BASED MULTI-TURN
    // =========================================================

    /**
     * Simulate multi-turn conversation with pre-built context.
     *
     * @param systemInstruction   AI behavior instructions
     * @param conversationHistory List of [role, content] pairs
     *                            (role=user|assistant)
     * @param newUserMessage      Latest user message
     */
    public String roleBasedMultiTurn(
            String systemInstruction,
            List<String[]> conversationHistory,
            String newUserMessage) {

        log.debug("RoleBasedMultiTurn: {} turns in history", conversationHistory.size());
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemInstruction));

        for (String[] turn : conversationHistory) {
            String role = turn[0];
            String content = turn[1];
            if ("user".equalsIgnoreCase(role)) {
                messages.add(new UserMessage(content));
            } else if ("assistant".equalsIgnoreCase(role)) {
                messages.add(new AssistantMessage(content));
            }
        }
        messages.add(new UserMessage(newUserMessage));

        return chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
    }
}
