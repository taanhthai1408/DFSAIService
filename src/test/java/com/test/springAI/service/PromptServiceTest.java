package com.test.springAI.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PromptServiceTest - Unit tests for PromptService
 * Uses any(Prompt.class) matcher to avoid ambiguous overload issues.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromptService Tests")
class PromptServiceTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private PromptService promptService;

    private ChatResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = new AssistantMessage("AI response");

        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockMessage);
    }

    // =========================================================
    // 1. basicPrompt
    // =========================================================

    @Test
    @DisplayName("basicPrompt - returns AI response")
    void basicPrompt_shouldReturnResponse() {
        String result = promptService.basicPrompt("Hello");
        assertThat(result).isEqualTo("AI response");
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("basicPrompt - calls ChatModel exactly once")
    void basicPrompt_callsModelOnce() {
        promptService.basicPrompt("Test message");
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    // =========================================================
    // 2. promptWithOptions
    // =========================================================

    @Test
    @DisplayName("promptWithOptions - calls ChatModel with Prompt")
    void promptWithOptions_shouldCallModel() {
        String result = promptService.promptWithOptions("test", 0.5, 0.9, 42);
        assertThat(result).isEqualTo("AI response");
        verify(chatModel).call(any(Prompt.class));
    }

    // =========================================================
    // 3. templatePrompt
    // =========================================================

    @Test
    @DisplayName("templatePrompt - fills template variables and calls model")
    void templatePrompt_shouldFillTemplateAndCallModel() {
        String template = "Translate {text} to {language}";
        Map<String, Object> vars = Map.of("text", "Hello", "language", "Vietnamese");

        String result = promptService.templatePrompt(template, vars);

        assertThat(result).isEqualTo("AI response");
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("translateTemplate - uses translate template")
    void translateTemplate_shouldWork() {
        String result = promptService.translateTemplate("Hello world", "Vietnamese");
        assertThat(result).isNotNull();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("summarizeTemplate - uses summarize template")
    void summarizeTemplate_shouldWork() {
        String result = promptService.summarizeTemplate("Long text content here...", 3);
        assertThat(result).isNotNull();
        verify(chatModel).call(any(Prompt.class));
    }

    // =========================================================
    // 4. systemUserPrompt
    // =========================================================

    @Test
    @DisplayName("systemUserPrompt - builds system+user message prompt")
    void systemUserPrompt_shouldBuildCorrectPrompt() {
        String result = promptService.systemUserPrompt(
                "You are a helpful assistant.",
                "What is 2+2?");
        assertThat(result).isEqualTo("AI response");
        verify(chatModel).call(argThat((Prompt p) -> p.getInstructions().size() == 2));
    }

    // =========================================================
    // 5. fewShotPrompt
    // =========================================================

    @Test
    @DisplayName("fewShotPrompt - builds prompt with examples")
    void fewShotPrompt_shouldBuildPromptWithExamples() {
        List<String[]> examples = List.of(
                new String[] { "Input A", "Output A" },
                new String[] { "Input B", "Output B" });
        String result = promptService.fewShotPrompt("Instruction", examples, "Actual Input");
        assertThat(result).isEqualTo("AI response");
        verify(chatModel).call(argThat((Prompt p) -> p.getContents().contains("Input A")
                && p.getContents().contains("Output A")
                && p.getContents().contains("Actual Input")));
    }

    @Test
    @DisplayName("sentimentAnalysisFewShot - includes POSITIVE/NEGATIVE/NEUTRAL examples")
    void sentimentAnalysisFewShot_shouldIncludeExamples() {
        promptService.sentimentAnalysisFewShot("This product is amazing!");
        verify(chatModel).call(argThat((Prompt p) -> p.getContents().contains("POSITIVE")
                && p.getContents().contains("NEGATIVE")));
    }

    // =========================================================
    // 6. chainOfThoughtPrompt
    // =========================================================

    @Test
    @DisplayName("chainOfThoughtPrompt - adds step-by-step instructions")
    void chainOfThoughtPrompt_shouldAddStepsInstructions() {
        promptService.chainOfThoughtPrompt("Is 17 a prime number?");
        verify(chatModel).call(argThat((Prompt p) -> p.getContents().contains("17")
                && p.getContents().contains("Step 1")));
    }

    // =========================================================
    // 7. roleBasedMultiTurn
    // =========================================================

    @Test
    @DisplayName("roleBasedMultiTurn - includes system + history + new message")
    void roleBasedMultiTurn_shouldIncludeAllMessages() {
        List<String[]> history = List.of(
                new String[] { "user", "Hi!" },
                new String[] { "assistant", "Hello! How can I help?" });

        promptService.roleBasedMultiTurn("Be helpful.", history, "What is Spring AI?");

        // System + 2 history + 1 new = 4 messages total
        verify(chatModel).call(argThat((Prompt p) -> p.getInstructions().size() == 4));
    }

    @Test
    @DisplayName("roleBasedMultiTurn - empty history = only system + new message")
    void roleBasedMultiTurn_emptyHistory_shouldReturn2Messages() {
        promptService.roleBasedMultiTurn("Be an expert.", List.of(), "Hello?");
        verify(chatModel).call(argThat((Prompt p) -> p.getInstructions().size() == 2));
    }
}
