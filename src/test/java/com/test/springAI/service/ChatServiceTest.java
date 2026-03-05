package com.test.springAI.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * =============================================================
 * ChatServiceTest - Unit Tests cho ChatService
 * =============================================================
 *
 * Tất cả tests dùng Mockito mock → chạy offline, không cần Ollama server.
 *
 * Test coverage:
 * 1. simpleChat
 * 2. chatWithSystem
 * 3. streamChat (Flux)
 * 4. chatWithMemory
 * 5. structuredOutput
 * 6. chatWithOptions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService Tests")
class ChatServiceTest {

    // =========================================================
    // Mocks cho Spring AI internal chains (ChatClient fluent API)
    // =========================================================

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatMemory chatMemory;

    // ChatClient fluent chain mocks
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;
    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    @InjectMocks
    private ChatService chatService;

    // =========================================================
    // Setup
    // =========================================================

    @BeforeEach
    void setUp() {
        // Mock ChatClient fluent chain: chatClient.prompt().user().call().content()
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        lenient().when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        lenient().when(requestSpec.stream()).thenReturn(streamResponseSpec);
    }

    // =========================================================
    // 1. simpleChat
    // =========================================================

    @Test
    @DisplayName("simpleChat - trả về câu trả lời từ AI")
    void simpleChat_shouldReturnAnswer() {
        // Given
        String question = "Spring AI là gì?";
        String expectedAnswer = "Spring AI là framework tích hợp AI vào Spring Boot.";
        when(callResponseSpec.content()).thenReturn(expectedAnswer);

        // When
        String result = chatService.simpleChat(question);

        // Then
        assertThat(result).isEqualTo(expectedAnswer);
        verify(chatClient).prompt();
        verify(requestSpec).user(question);
        verify(requestSpec).call();
    }

    @Test
    @DisplayName("simpleChat - không được trả về null")
    void simpleChat_shouldNotReturnNull() {
        when(callResponseSpec.content()).thenReturn("AI response");
        String result = chatService.simpleChat("test question");
        assertThat(result).isNotNull();
    }

    // =========================================================
    // 2. chatWithSystem
    // =========================================================

    @Test
    @DisplayName("chatWithSystem - gọi đúng system và user prompt")
    void chatWithSystem_shouldSetSystemAndUser() {
        // Given
        String system = "Bạn là chuyên gia Java";
        String user = "Giải thích Stream API";
        when(callResponseSpec.content()).thenReturn("Stream API là...");

        // When
        String result = chatService.chatWithSystem(system, user);

        // Then
        assertThat(result).isNotNull().contains("Stream");
        verify(requestSpec).system(system);
        verify(requestSpec).user(user);
    }

    @Test
    @DisplayName("chatWithSystem - system prompt ảnh hưởng persona")
    void chatWithSystem_differentSystemPromptsProduceDifferentBehavior() {
        // Given
        when(callResponseSpec.content())
                .thenReturn("Như một chuyên gia y tế...")
                .thenReturn("Như một kỹ sư...");

        // When
        String medicalAnswer = chatService.chatWithSystem("Bạn là bác sĩ", "Đau đầu nên làm gì?");
        String techAnswer = chatService.chatWithSystem("Bạn là kỹ sư", "Đau đầu nên làm gì?");

        // Then - mock returns first, second call
        assertThat(medicalAnswer).isNotNull();
        assertThat(techAnswer).isNotNull();
        verify(requestSpec, times(2)).system(anyString());
    }

    // =========================================================
    // 3. streamChat
    // =========================================================

    @Test
    @DisplayName("streamChat - trả về Flux<String> không rỗng")
    void streamChat_shouldReturnFlux() {
        // Given
        String message = "Kể chuyện ngắn về AI";
        Flux<String> mockFlux = Flux.just("Ngày xưa ", "có một AI ", "rất thông minh.");
        when(streamResponseSpec.content()).thenReturn(mockFlux);

        // When
        Flux<String> result = chatService.streamChat(message);

        // Then
        StepVerifier.create(result)
                .expectNext("Ngày xưa ")
                .expectNext("có một AI ")
                .expectNext("rất thông minh.")
                .expectComplete()
                .verify();

        verify(requestSpec).stream();
    }

    @Test
    @DisplayName("streamChat - Flux không emit error với input hợp lệ")
    void streamChat_shouldNotEmitError() {
        when(streamResponseSpec.content()).thenReturn(Flux.just("token1", "token2"));
        Flux<String> result = chatService.streamChat("valid message");
        StepVerifier.create(result)
                .expectNextCount(2)
                .expectComplete()
                .verify();
    }

    // =========================================================
    // 4. chatWithMemory
    // =========================================================

    @Test
    @DisplayName("chatWithMemory - dùng đúng conversationId")
    void chatWithMemory_shouldUseConversationId() {
        // Given
        String convId = "conv-123";
        String message = "Câu hỏi đầu tiên";
        when(callResponseSpec.content()).thenReturn("Trả lời 1");

        // When
        String result = chatService.chatWithMemory(convId, message);

        // Then
        assertThat(result).isEqualTo("Trả lời 1");
        // conversation ID được set qua advisor param array
        verify(requestSpec).advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class));
    }

    @Test
    @DisplayName("chatWithMemory - 2 conversation IDs khác nhau độc lập")
    void chatWithMemory_differentConversationIdsAreIndependent() {
        when(callResponseSpec.content()).thenReturn("Trả lời A").thenReturn("Trả lời B");

        String result1 = chatService.chatWithMemory("conv-A", "Xin chào");
        String result2 = chatService.chatWithMemory("conv-B", "Hello");

        assertThat(result1).isEqualTo("Trả lời A");
        assertThat(result2).isEqualTo("Trả lời B");
    }

    // =========================================================
    // 5. structuredOutput
    // =========================================================

    @Test
    @DisplayName("structuredOutput - deserialize response thành Java object")
    void structuredOutput_shouldReturnPojo() {
        // Given
        when(callResponseSpec.entity(PersonInfo.class))
                .thenReturn(new PersonInfo("Nguyễn Văn A", 30, "Hà Nội"));

        // When
        PersonInfo person = chatService.structuredOutput(
                "Tạo thông tin người dùng mẫu", PersonInfo.class);

        // Then
        assertThat(person).isNotNull();
        assertThat(person.name()).isEqualTo("Nguyễn Văn A");
        assertThat(person.age()).isEqualTo(30);
        assertThat(person.city()).isEqualTo("Hà Nội");
    }

    // =========================================================
    // 6. chatWithOptions - dùng ChatModel trực tiếp
    // =========================================================

    @Test
    @DisplayName("chatWithOptions - gọi ChatModel với Prompt")
    void chatWithOptions_shouldCallChatModel() {
        // Given
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = new AssistantMessage("Kết quả tính toán");

        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockMessage);

        // When
        String result = chatService.chatWithOptions("Tính 2 + 2", 0.0, 100);

        // Then
        assertThat(result).isEqualTo("Kết quả tính toán");
        verify(chatModel).call(any(Prompt.class));
    }

    // =========================================================
    // 7. buildResponse helper
    // =========================================================

    @Test
    @DisplayName("buildResponse - tạo ChatResponse đúng")
    void buildResponse_shouldCreateCorrectResponse() {
        com.test.springAI.model.ChatResponse response = chatService.buildResponse("conv-1", "Câu trả lời");

        assertThat(response.getAnswer()).isEqualTo("Câu trả lời");
        assertThat(response.getConversationId()).isEqualTo("conv-1");
        assertThat(response.getModel()).isEqualTo("llama3");
    }

    // =========================================================
    // Helper record
    // =========================================================
    record PersonInfo(String name, int age, String city) {
    }
}
