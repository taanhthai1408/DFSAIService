package com.test.springAI.udemy.baihoc16.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HocChatService {
    @Autowired
    ChatClient chatClient;

    public String getChatResponse(String promt){
        return chatClient.prompt(promt).call().chatResponse().getResult().getOutput().getText();
    }
}
