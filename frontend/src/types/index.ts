// Shared types — these mirror the Java DTOs in
// src/main/java/com/org/documind/dto/*. Jackson serializes record components
// as camelCase, so the field names here are camelCase (chunkIndex, documentId,
// conversationId, uploadedAt, chunkCount) — unlike the Python/snake_case build.

export interface Citation {
  filename: string;
  chunkIndex: number;
}

export type DocumentStatus = "UPLOADED" | "PROCESSING" | "READY" | "FAILED";

export interface DocumentResponse {
  id: string;
  filename: string;
  status: DocumentStatus;
  uploadedAt: string;
  chunkCount: number;
  failureReason?: string | null;
}

export interface UploadResponse {
  documentId: string;
  status: string;
  message: string;
}

export interface AskRequest {
  question: string;
  documentId?: string;
  conversationId?: string;
}

// The Java backend returns the full answer in one JSON response (no streaming).
export interface AskResponse {
  answer: string;
  citations: Citation[];
  conversationId: string;
}

export interface ConversationTurn {
  question: string;
  answer: string;
  citations: Citation[];
  timestamp: string;
}

export interface ConversationHistory {
  conversationId: string;
  turns: ConversationTurn[];
}
