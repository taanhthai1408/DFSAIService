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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MultiModalServiceTest - Unit Tests for MultiModalService
 *
 * Note: Spring AI 1.0.0 ChatModel.call(Prompt) is the primary method.
 * Tests use simple any(Prompt.class) matcher to avoid type ambiguity.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiModalService Tests")
class MultiModalServiceTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private MultiModalService multiModalService;

    private ChatResponse mockChatResponse;

    @BeforeEach
    void setUp() {
        mockChatResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = new AssistantMessage("AI vision analysis result");

        lenient().when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);
        lenient().when(mockChatResponse.getResult()).thenReturn(mockGeneration);
        lenient().when(mockGeneration.getOutput()).thenReturn(mockMessage);
    }

    // =========================================================
    // 1. analyzeImageUrl - valid URL
    // =========================================================

    @Test
    @DisplayName("analyzeImageUrl - valid URL calls ChatModel")
    void analyzeImageUrl_validUrl_shouldCallChatModel() {
        String imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/13/Sustainable-development-goals.jpg/320px-Sustainable-development-goals.jpg";
        String question = "Describe this image";

        String result = multiModalService.analyzeImageUrl(imageUrl, question);

        assertThat(result).isEqualTo("AI vision analysis result");
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("analyzeImageUrl - calls ChatModel with a Prompt")
    void analyzeImageUrl_shouldPassPromptToChatModel() {
        String imageUrl = "https://example.com/image.jpg";
        multiModalService.analyzeImageUrl(imageUrl, "How many people?");
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    // =========================================================
    // 2. analyzeImageUrl - invalid URL
    // =========================================================

    @Test
    @DisplayName("analyzeImageUrl - invalid URL throws IllegalArgumentException")
    void analyzeImageUrl_invalidUrl_shouldThrowException() {
        assertThatThrownBy(() -> multiModalService.analyzeImageUrl("not-a-valid-url", "Describe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid image URL");
    }

    // =========================================================
    // 3. analyzeImageBytes
    // =========================================================

    @Test
    @DisplayName("analyzeImageBytes - bytes input calls ChatModel")
    void analyzeImageBytes_shouldCallChatModel() {
        byte[] fakeImageData = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
        String result = multiModalService.analyzeImageBytes(fakeImageData, "image/jpeg", "What is this?");

        assertThat(result).isNotNull();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("analyzeImageBytes - PNG format works")
    void analyzeImageBytes_pngFormat_shouldWork() {
        byte[] fakePng = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 };
        multiModalService.analyzeImageBytes(fakePng, "image/png", "Analyze PNG");
        verify(chatModel).call(any(Prompt.class));
    }

    // =========================================================
    // 4. extractTextFromImage (OCR)
    // =========================================================

    @Test
    @DisplayName("extractTextFromImage - calls analyzeImageUrl")
    void extractTextFromImage_shouldCallAnalyzeImageUrl() {
        multiModalService.extractTextFromImage("https://example.com/text.jpg");
        verify(chatModel).call(any(Prompt.class));
    }

    // =========================================================
    // 5. describeImage
    // =========================================================

    @Test
    @DisplayName("describeImage - calls analyzeImageUrl")
    void describeImage_shouldCallAnalyzeImageUrl() {
        multiModalService.describeImage("https://example.com/photo.jpg");
        verify(chatModel).call(any(Prompt.class));
    }

    // =========================================================
    // 6. classifyImage
    // =========================================================

    @Test
    @DisplayName("classifyImage - calls analyzeImageUrl")
    void classifyImage_shouldCallAnalyzeImageUrl() {
        multiModalService.classifyImage("https://example.com/cat.jpg");
        verify(chatModel).call(any(Prompt.class));
    }

    // =========================================================
    // 7. Return value not null
    // =========================================================

    @Test
    @DisplayName("All vision methods return non-null result")
    void allVisionMethods_shouldReturnNonNull() {
        assertThat(multiModalService.describeImage("https://example.com/a.jpg")).isNotNull();
        assertThat(multiModalService.classifyImage("https://example.com/b.jpg")).isNotNull();
        assertThat(multiModalService.extractTextFromImage("https://example.com/c.jpg")).isNotNull();
    }
}
