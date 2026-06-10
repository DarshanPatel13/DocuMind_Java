package com.darshan.documind.controller;

import com.darshan.documind.dto.AskRequest;
import com.darshan.documind.dto.AskResponse;
import com.darshan.documind.service.AskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ask")
@Tag(name = "Ask", description = "Question answering over uploaded documents (RAG)")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping
    @Operation(summary = "Ask a question about the uploaded documents",
            description = "Retrieves the most similar chunks and answers strictly from them, "
                    + "with [filename, chunk N] citations. Rate-limited to 10 requests/min per IP.")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request);
    }
}
