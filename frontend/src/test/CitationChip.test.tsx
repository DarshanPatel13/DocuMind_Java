import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CitationChip } from "../components/CitationChip";

describe("CitationChip", () => {
  it("renders the citation as [filename, chunk N]", () => {
    render(<CitationChip citation={{ filename: "policy.pdf", chunkIndex: 2 }} />);
    expect(screen.getByText(/\[policy\.pdf, chunk 2\]/)).toBeInTheDocument();
  });
});
