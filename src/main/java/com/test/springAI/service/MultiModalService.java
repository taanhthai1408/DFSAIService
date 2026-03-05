package com.test.springAI.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * MultiModalService - Vision / Multimodal with Spring AI 1.0.0
 *
 * Requires llava model on Ollama server: ollama pull llava
 * Spring AI 1.0.0: Media(MimeType, URI) or Media(MimeType, Resource)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiModalService {

    private final ChatModel chatModel;

    private static final String VISION_MODEL = "llava";

    // =========================================================
    // 1. ANALYZE IMAGE FROM URL
    // =========================================================

    public String analyzeImageUrl(String imageUrl, String question) {
        log.debug("AnalyzeImageUrl: url={}", imageUrl);
        // Validate URL format
        try {
            URI uri = URI.create(imageUrl);
            if (uri.getScheme() == null) {
                throw new IllegalArgumentException("Invalid image URL: " + imageUrl);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid image URL: " + imageUrl, e);
        }

        Media imageMedia = new Media(MimeTypeUtils.IMAGE_JPEG, URI.create(imageUrl));

        UserMessage userMessage = UserMessage.builder()
                .text(question)
                .media(List.of(imageMedia))
                .build();

        Prompt prompt = new Prompt(
                List.of(userMessage),
                OllamaOptions.builder().model(VISION_MODEL).build());

        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    // =========================================================
    // 2. ANALYZE IMAGE FROM BYTES
    // =========================================================

    public String analyzeImageBytes(byte[] imageData, String mimeType, String question) {
        log.debug("AnalyzeImageBytes: mimeType={}, size={}bytes", mimeType, imageData.length);

        Media imageMedia = new Media(
                MimeTypeUtils.parseMimeType(mimeType),
                new org.springframework.core.io.ByteArrayResource(imageData));

        UserMessage userMessage = UserMessage.builder()
                .text(question)
                .media(List.of(imageMedia))
                .build();

        Prompt prompt = new Prompt(
                List.of(userMessage),
                OllamaOptions.builder().model(VISION_MODEL).build());

        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    // =========================================================
    // 3. MULTIPLE IMAGES
    // =========================================================

    public String analyzeMultipleImages(List<String> imageUrls, String question) {
        log.debug("AnalyzeMultipleImages: {} images", imageUrls.size());
        List<Media> mediaList = new ArrayList<>();
        for (String url : imageUrls) {
            mediaList.add(new Media(MimeTypeUtils.IMAGE_JPEG, URI.create(url)));
        }

        UserMessage userMessage = UserMessage.builder()
                .text(question)
                .media(mediaList)
                .build();

        Prompt prompt = new Prompt(
                List.of(userMessage),
                OllamaOptions.builder().model(VISION_MODEL).build());

        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    // =========================================================
    // 4. SPECIALIZED VISION TASKS
    // =========================================================

    public String extractTextFromImage(String imageUrl) {
        return analyzeImageUrl(imageUrl,
                "Please read and extract all text visible in this image.");
    }

    public String describeImage(String imageUrl) {
        return analyzeImageUrl(imageUrl,
                "Please describe the contents of this image in detail: objects, colors, context.");
    }

    public String classifyImage(String imageUrl) {
        return analyzeImageUrl(imageUrl,
                "Please classify this image: type of image, main subject, scene.");
    }
}
