package com.test.springAI.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * =============================================================
 * FunctionCallServiceTest - Unit Tests (Spring AI 1.0.0 @Tool)
 * =============================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FunctionCallService Tests")
class FunctionCallServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private FunctionCallService functionCallService;

    @BeforeEach
    void setUp() {
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.tools(any(Object.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    // =========================================================
    // 1. Test Tool: getWeather
    // =========================================================

    @Test
    @DisplayName("getWeather - Ha Noi co du lieu")
    void getWeather_haNoi_shouldReturnData() {
        FunctionCallService.WeatherResponse response = functionCallService.getWeather("Ha Noi");

        assertThat(response).isNotNull();
        assertThat(response.city()).isEqualTo("Ha Noi");
        assertThat(response.temperature()).isEqualTo(25);
        assertThat(response.condition()).isEqualTo("Co may");
    }

    @Test
    @DisplayName("getWeather - TP.HCM co du lieu")
    void getWeather_hcm_shouldReturnData() {
        FunctionCallService.WeatherResponse response = functionCallService.getWeather("TP.HCM");
        assertThat(response.city()).isEqualTo("TP.HCM");
        assertThat(response.temperature()).isEqualTo(32);
    }

    @Test
    @DisplayName("getWeather - thanh pho khong biet tra ve default")
    void getWeather_unknownCity_shouldReturnDefault() {
        FunctionCallService.WeatherResponse response = functionCallService.getWeather("Unknown City");
        assertThat(response.city()).isEqualTo("Unknown City");
        assertThat(response.temperature()).isEqualTo(20);
    }

    // =========================================================
    // 2. Test Tool: getCurrentTime
    // =========================================================

    @Test
    @DisplayName("getCurrentTime - tra ve datetime khong null")
    void getCurrentTime_shouldReturnNonNull() {
        FunctionCallService.TimeResponse response = functionCallService.getCurrentTime();

        assertThat(response).isNotNull();
        assertThat(response.datetime()).isNotBlank();
        assertThat(response.timezone()).isEqualTo("UTC+7");
        assertThat(response.country()).isEqualTo("Viet Nam");
    }

    @Test
    @DisplayName("getCurrentTime - format ngay dd/MM/yyyy HH:mm:ss")
    void getCurrentTime_shouldHaveCorrectFormat() {
        FunctionCallService.TimeResponse response = functionCallService.getCurrentTime();
        // Format: dd/MM/yyyy HH:mm:ss = 19 chars + "/" và ":"
        assertThat(response.datetime()).hasSize(19);
        assertThat(response.datetime()).contains("/");
        assertThat(response.datetime()).contains(":");
    }

    // =========================================================
    // 3. Test Tool: calculate
    // =========================================================

    @Test
    @DisplayName("calculate - phep cong")
    void calculate_addition() {
        FunctionCallService.CalculatorResponse result = functionCallService.calculate(10, "+", 5);
        assertThat(result.result()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("calculate - phep tru")
    void calculate_subtraction() {
        FunctionCallService.CalculatorResponse result = functionCallService.calculate(20, "-", 8);
        assertThat(result.result()).isEqualTo(12.0);
    }

    @Test
    @DisplayName("calculate - phep nhan")
    void calculate_multiplication() {
        FunctionCallService.CalculatorResponse result = functionCallService.calculate(6, "*", 7);
        assertThat(result.result()).isEqualTo(42.0);
    }

    @Test
    @DisplayName("calculate - phep chia")
    void calculate_division() {
        FunctionCallService.CalculatorResponse result = functionCallService.calculate(10, "/", 2);
        assertThat(result.result()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("calculate - chia cho 0 tra ve NaN")
    void calculate_divisionByZero_returnsNaN() {
        FunctionCallService.CalculatorResponse result = functionCallService.calculate(10, "/", 0);
        assertThat(result.result()).isNaN();
    }

    @Test
    @DisplayName("calculate - toan tu khong hop le nem exception")
    void calculate_invalidOperator_throws() {
        assertThatThrownBy(() -> functionCallService.calculate(5, "%", 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================
    // 4. chatWithWeatherTool
    // =========================================================

    @Test
    @DisplayName("chatWithWeatherTool - goi ChatClient voi tools")
    void chatWithWeatherTool_shouldCallChatClient() {
        when(callResponseSpec.content()).thenReturn("Ha Noi dang co 25 do C.");

        String result = functionCallService.chatWithWeatherTool("Thoi tiet Ha Noi the nao?");

        assertThat(result).isEqualTo("Ha Noi dang co 25 do C.");
        verify(requestSpec).tools(any(Object.class));
    }

    // =========================================================
    // 5. chatWithAllTools
    // =========================================================

    @Test
    @DisplayName("chatWithAllTools - goi tools(this) tren ChatClient")
    void chatWithAllTools_shouldRegisterThisAsToolProvider() {
        when(callResponseSpec.content()).thenReturn("Toi co the giup ve thoi tiet, tinh toan, thoi gian.");

        functionCallService.chatWithAllTools("Ban co the lam gi?");

        verify(requestSpec).tools(functionCallService);
    }
}
