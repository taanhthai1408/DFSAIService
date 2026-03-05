package com.test.springAI.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * =============================================================
 * FunctionCallService - Tool / Function Calling (Spring AI 1.0.0)
 * =============================================================
 *
 * Spring AI 1.0.0: dùng @Tool annotation thay FunctionCallback.
 * ChatClient dùng .tools(toolBeanName) hoặc .tools(toolInstance).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionCallService {

    private final ChatClient chatClient;

    // =========================================================
    // 1. TOOL METHODS (@Tool annotation)
    // Trong Spring AI 1.0.0, @Tool đánh dấu method là tool
    // =========================================================

    @Tool(description = "Lay thong tin thoi tiet hien tai cua mot thanh pho")
    public WeatherResponse getWeather(String city) {
        log.info("[TOOL CALLED] getWeather: city={}", city);
        Map<String, WeatherResponse> mockWeather = Map.of(
                "Ha Noi", new WeatherResponse("Ha Noi", 25, "Co may", 75),
                "TP.HCM", new WeatherResponse("TP.HCM", 32, "Nang", 60),
                "Da Nang", new WeatherResponse("Da Nang", 28, "Quang dang", 65));
        return mockWeather.getOrDefault(city, new WeatherResponse(city, 20, "Khong ro", 50));
    }

    @Tool(description = "Lay ngay gio hien tai theo moi truong")
    public TimeResponse getCurrentTime() {
        log.info("[TOOL CALLED] getCurrentTime");
        String formatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        return new TimeResponse(formatted, "UTC+7", "Viet Nam");
    }

    @Tool(description = "Calculate math expression: plus, minus, multiply, divide")
    public CalculatorResponse calculate(double a, String operator, double b) {
        log.info("[TOOL CALLED] calculate: {} {} {}", a, operator, b);
        double result;
        if ("+".equals(operator)) {
            result = a + b;
        } else if ("-".equals(operator)) {
            result = a - b;
        } else if ("*".equals(operator)) {
            result = a * b;
        } else if ("/".equals(operator)) {
            result = (b != 0) ? a / b : Double.NaN;
        } else {
            throw new IllegalArgumentException("Unknown operator: " + operator);
        }
        return new CalculatorResponse(result, a + " " + operator + " " + b + " = " + result);
    }

    // =========================================================
    // 2. CHAT METHODS VỚI TOOLS
    // Spring AI 1.0.0: .tools(toolBeanRef hoặc instance)
    // =========================================================

    /**
     * Chat với weather tool.
     */
    public String chatWithWeatherTool(String message) {
        log.debug("ChatWithWeatherTool: {}", message);
        return chatClient.prompt()
                .user(message)
                .tools(this) // pass this service as tool provider
                .call()
                .content();
    }

    /**
     * Chat với calculator tool.
     */
    public String chatWithCalculatorTool(String message) {
        log.debug("ChatWithCalculatorTool: {}", message);
        return chatClient.prompt()
                .user(message)
                .tools(this)
                .call()
                .content();
    }

    /**
     * Chat với time tool.
     */
    public String chatWithTimeTool(String message) {
        log.debug("ChatWithTimeTool: {}", message);
        return chatClient.prompt()
                .user(message)
                .tools(this)
                .call()
                .content();
    }

    /**
     * Chat với tất cả tools (đã bao gồm trong this).
     */
    public String chatWithAllTools(String message) {
        log.debug("ChatWithAllTools: {}", message);
        return chatClient.prompt()
                .user(message)
                .tools(this)
                .call()
                .content();
    }

    // =========================================================
    // Record types cho Tool I/O
    // =========================================================

    @JsonClassDescription("Thong tin thoi tiet")
    public record WeatherResponse(String city, int temperature, String condition, int humidity) {
    }

    @JsonClassDescription("Thong tin thoi gian")
    public record TimeResponse(String datetime, String timezone, String country) {
    }

    @JsonClassDescription("Ket qua tinh toan")
    public record CalculatorResponse(double result, String expression) {
    }

    // Tool Input records
    @JsonClassDescription("Yeu cau lay thoi tiet")
    public record WeatherRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("Ten thanh pho") String city) {
    }

    @JsonClassDescription("Yeu cau la thoi gian")
    public record TimeRequest(
            @JsonProperty(required = false) @JsonPropertyDescription("Mui gio") String timezone) {
    }

    @JsonClassDescription("Yeu cau tinh toan")
    public record CalculatorRequest(
            @JsonPropertyDescription("So thu nhat") double a,
            @JsonPropertyDescription("Toan tu: +, -, *, /") String operator,
            @JsonPropertyDescription("So thu hai") double b) {
    }
}
