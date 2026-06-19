import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ChatView } from "../components/ChatView";
import { askQuestion } from "../api/documind";

// Mock the API so the test drives the (non-streaming) response directly.
vi.mock("../api/documind");

describe("ChatView", () => {
  beforeEach(() => {
    vi.mocked(askQuestion).mockReset();
    vi.mocked(askQuestion).mockResolvedValue({
      answer: "Refunds are issued within 30 days.",
      citations: [{ filename: "policy.pdf", chunkIndex: 2 }],
      conversationId: "c1",
    });
  });

  it("shows the answer and renders citations after asking", async () => {
    const user = userEvent.setup();
    render(<ChatView />);

    await user.type(screen.getByPlaceholderText(/ask a question/i), "refund policy?");
    await user.click(screen.getByRole("button", { name: /ask/i }));

    expect(await screen.findByText(/Refunds are issued within 30 days\./)).toBeInTheDocument();
    expect(screen.getByText(/\[policy\.pdf, chunk 2\]/)).toBeInTheDocument();
  });
});
