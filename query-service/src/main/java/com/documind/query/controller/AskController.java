package com.documind.query.controller;

import com.documind.contracts.AskRequest;
import com.documind.query.service.AskService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Streams a grounded answer as Server-Sent Events. Sits behind the gateway. */
@RestController
@RequestMapping("/api/ask")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request);
    }
}
