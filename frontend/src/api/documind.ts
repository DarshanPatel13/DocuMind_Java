import type { AxiosProgressEvent } from "axios";

import type {
  AskRequest,
  AskResponse,
  ConversationHistory,
  DocumentResponse,
  UploadResponse,
} from "../types";
import { apiClient } from "./client";

export async function listDocuments(): Promise<DocumentResponse[]> {
  const { data } = await apiClient.get<DocumentResponse[]>("/api/documents");
  return data;
}

export async function uploadDocument(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<UploadResponse> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await apiClient.post<UploadResponse>("/api/documents", form, {
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    },
  });
  return data;
}

export async function getConversation(conversationId: string): Promise<ConversationHistory> {
  const { data } = await apiClient.get<ConversationHistory>(
    `/api/conversations/${conversationId}`,
  );
  return data;
}

/**
 * Ask a question. The Java backend returns the full answer + citations in one
 * JSON response (no streaming), so this is a plain POST.
 */
export async function askQuestion(body: AskRequest): Promise<AskResponse> {
  const { data } = await apiClient.post<AskResponse>("/api/ask", body);
  return data;
}
