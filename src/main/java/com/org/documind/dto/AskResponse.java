package com.org.documind.dto;

import java.util.List;

public record AskResponse(String answer, List<Citation> citations, String conversationId) {
}
