package com.documind.query.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Cheap, explainable prompt-injection screen (Java equivalent of
 * {@code guardrails.py}). A first filter, not a complete defence: flags inputs
 * that try to override the system prompt, which then get a fixed refusal instead
 * of reaching retrieval or the LLM.
 */
@Component
public class Guardrails {

    public static final String INJECTION_REFUSAL =
            "I can't help with that — it looks like an attempt to override my instructions. "
                    + "Ask a question about your uploaded documents instead.";

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("ignore (all |the )?(previous|prior|above|earlier) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (all |the )?(previous|prior|above|earlier)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(reveal|show|print|repeat).{0,30}(system prompt|your instructions|your prompt)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend to be\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override .{0,20}(instructions|rules)", Pattern.CASE_INSENSITIVE)
    );

    public boolean isInjection(String question) {
        if (question == null) {
            return false;
        }
        return PATTERNS.stream().anyMatch(p -> p.matcher(question).find());
    }
}
