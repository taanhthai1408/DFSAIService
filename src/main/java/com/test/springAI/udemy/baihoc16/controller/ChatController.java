package com.test.springAI.udemy.baihoc16.controller;


import com.test.springAI.udemy.baihoc16.service.HocChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ChatController {
    @Autowired
    private HocChatService hocChatService;

    @GetMapping("/hoc/chat")
    public String chat(@RequestParam("prompt") String prompt){
        log.info("prompt : {}",prompt);
        return hocChatService.getChatResponse(prompt);
    }
}
