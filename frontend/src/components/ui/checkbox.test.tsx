/* @vitest-environment jsdom */

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Checkbox } from "@/components/ui/checkbox";

describe("Checkbox", () => {
  it("exposes an accessible checked state and toggles", () => {
    render(<Checkbox aria-label="Share study summary" />);
    const checkbox = screen.getByRole("checkbox", { name: "Share study summary" });

    expect(checkbox.getAttribute("data-state")).toBe("unchecked");
    fireEvent.click(checkbox);
    expect(checkbox.getAttribute("data-state")).toBe("checked");
  });
});
