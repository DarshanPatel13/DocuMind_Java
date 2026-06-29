package com.documind.query.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tiny query-intent heuristic (Java equivalent of {@code intent.py}). Top-k
 * retrieval can't satisfy "enumerate / summarize the WHOLE document" requests
 * (it only ever sees k chunks), so this detects that aggregate intent and the
 * ask flow switches to whole-document mode.
 */
@Component
public class IntentDetector {

    private static final List<Pattern> AGGREGATE = List.of(
            Pattern.compile("\\b(list|enumerate)\\b.{0,20}\\b(all|every|the)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsummar(y|ise|ize)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(how many|count)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(overview|outline)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\ball (the )?(topics|sections|questions|points)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bwhat (are|were) all\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(entire|whole) (document|paper|pdf)\\b", Pattern.CASE_INSENSITIVE)
    );

    public boolean isAggregate(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return AGGREGATE.stream().anyMatch(p -> p.matcher(question).find());
    }
}
