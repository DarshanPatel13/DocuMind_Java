package com.documind.query.controller;

import com.documind.contracts.ConversationHistoryResponse;
import com.documind.query.service.ConversationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read one conversation's full history. */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{id}")
    public ConversationHistoryResponse history(@PathVariable String id) {
        return conversationService.getHistory(id);
    }
}
