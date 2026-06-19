import { useCallback, useState } from "react";

import { askQuestion } from "../api/documind";
import type { Citation } from "../types";

/**
 * Drives one Q&A exchange against the Java backend, which returns the full
 * answer in a single JSON response (no streaming). The conversationId is
 * threaded back into the next ask to continue the thread.
 */
export function useAsk() {
  const [answer, setAnswer] = useState("");
  const [citations, setCitations] = useState<Citation[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ask = useCallback(
    async (question: string, documentId?: string) => {
      setAnswer("");
      setCitations([]);
      setError(null);
      setIsLoading(true);
      try {
        const response = await askQuestion({
          question,
          documentId,
          conversationId: conversationId ?? undefined,
        });
        setAnswer(response.answer);
        setCitations(response.citations);
        setConversationId(response.conversationId);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Request failed");
      } finally {
        setIsLoading(false);
      }
    },
    [conversationId],
  );

  const reset = useCallback(() => {
    setAnswer("");
    setCitations([]);
    setConversationId(null);
    setError(null);
    setIsLoading(false);
  }, []);

  return { answer, citations, conversationId, isLoading, error, ask, reset };
}
