/* @vitest-environment jsdom */

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

describe("Select", () => {
  it("opens an accessible listbox and reports the chosen item", () => {
    const onValueChange = vi.fn();
    render(
      <Select onValueChange={onValueChange} defaultValue="WORK">
        <SelectTrigger aria-label="Activity"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value="WORK">Work</SelectItem>
          <SelectItem value="ROT">Rot</SelectItem>
        </SelectContent>
      </Select>,
    );

    fireEvent.click(screen.getByRole("combobox", { name: "Activity" }));
    fireEvent.click(screen.getByRole("option", { name: "Rot" }));

    expect(onValueChange).toHaveBeenCalledWith("ROT");
  });
});
