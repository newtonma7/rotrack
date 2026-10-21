import { Button } from "@/components/ui/button";
import { addLocalDays, formatLocalDate } from "@/lib/daily-log-date";
import type { DailyLogRange, DailyLogSummary } from "@/types/daily-logs";

type DailyLogCalendarProps = {
  monthDate: string;
  range: Pick<DailyLogRange, "start" | "end">;
  summaries: DailyLogSummary[];
  selectedDate: string;
  onSelectDate: (date: string) => void;
  onMonthChange: (direction: number) => void;
  canMovePrevious: boolean;
  canMoveNext: boolean;
};

export function DailyLogCalendar({
  monthDate,
  range,
  summaries,
  selectedDate,
  onSelectDate,
  onMonthChange,
  canMovePrevious,
  canMoveNext,
}: DailyLogCalendarProps) {
  const summaryByDate = new Map(summaries.map((summary) => [summary.localDate, summary]));
  const monthLabel = formatLocalDate(monthDate, { month: "long", year: "numeric" });

  return (
    <section
      aria-label="Daily log calendar"
      className="rounded-[28px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-4 md:p-6"
    >
      <div className="mb-5 flex items-center justify-between">
        <Button
          variant="outline"
          aria-label="Previous month"
          disabled={!canMovePrevious}
          onClick={() => onMonthChange(-1)}
          className="rounded-full"
        >
          ←
        </Button>
        <h2 className="font-display text-xl">{monthLabel}</h2>
        <Button
          variant="outline"
          aria-label="Next month"
          disabled={!canMoveNext}
          onClick={() => onMonthChange(1)}
          className="rounded-full"
        >
          →
        </Button>
      </div>

      <div
        aria-hidden="true"
        className="grid grid-cols-7 text-center text-xs font-semibold text-[var(--rt-ink-muted)]"
      >
        {["sun", "mon", "tue", "wed", "thu", "fri", "sat"].map((weekday) => (
          <span key={weekday} className="pb-2">{weekday}</span>
        ))}
      </div>

      <div
        role="grid"
        aria-label={`${monthLabel} daily logs`}
        className="grid grid-cols-7 gap-1"
      >
        {Array.from({ length: 42 }, (_, index) => {
          const date = addLocalDays(range.start, index);
          const summary = summaryByDate.get(date);
          const isOutsideMonth = date.slice(0, 7) !== monthDate.slice(0, 7);
          const isSelected = selectedDate === date;

          return (
            <div role="gridcell" key={date} aria-selected={isSelected}>
              <button
                type="button"
                aria-label={dayLabel(date, summary)}
                onClick={() => onSelectDate(date)}
                className={`flex min-h-14 w-full flex-col items-center justify-center rounded-2xl text-sm transition-colors focus-visible:outline-2 focus-visible:outline-[var(--rt-orange)] ${isSelected ? "bg-[var(--rt-ink)] text-[var(--rt-cream)]" : "hover:bg-[var(--rt-cream-soft)]"} ${isOutsideMonth && !isSelected ? "text-[var(--rt-ink-muted)]/50" : ""}`}
              >
                <span>{Number(date.slice(-2))}</span>
                {summary && <ActivityDots summary={summary} />}
              </button>
            </div>
          );
        })}
      </div>

      <div className="mt-4 flex flex-wrap gap-4 text-xs text-[var(--rt-ink-muted)]">
        <span>● work</span>
        <span>● rot</span>
        <span>◌ reflection</span>
      </div>
    </section>
  );
}

function dayLabel(date: string, summary?: DailyLogSummary): string {
  const day = formatLocalDate(date);
  if (!summary) return day;
  const reflection = summary.hasReflectionContent ? ", reflection" : "";
  return `${day}, ${summary.workSeconds} work seconds, ${summary.rotSeconds} rot seconds${reflection}`;
}

function ActivityDots({ summary }: { summary: DailyLogSummary }) {
  return (
    <span className="mt-1 flex gap-1" aria-hidden="true">
      {summary.workSeconds > 0 && <i className="size-1.5 rounded-full bg-[var(--rt-orange)]" />}
      {summary.rotSeconds > 0 && <i className="size-1.5 rounded-full bg-[var(--rt-ink-muted)]" />}
      {summary.hasReflectionContent && <i className="size-1.5 rounded-full border border-current" />}
    </span>
  );
}
