package com.darshan.documind.controller;

import com.darshan.documind.dto.ConversationHistoryResponse;
import com.darshan.documind.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversations", description = "Q&A history stored in MongoDB")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Full Q&A history for one conversation, oldest turn first")
    public ConversationHistoryResponse history(@PathVariable String conversationId) {
        return conversationService.history(conversationId);
    }
}
