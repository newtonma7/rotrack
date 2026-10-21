"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { DailyLogCalendar } from "@/components/daily-logs/DailyLogCalendar";
import { DailyLogDetail } from "@/components/daily-logs/DailyLogDetail";
import { getDailyLog, getDailyLogs, getPreferences } from "@/lib/api";
import { ApiRequestError } from "@/lib/api-errors";
import { calendarRange, isCanonicalLocalDate, localDateInTimeZone, monthStart, shiftLocalMonth } from "@/lib/daily-log-date";
import { requestAppNavigation } from "@/lib/navigation-guard";
import { getBrowserTimeZone } from "@/lib/timezone";
import type { DailyLog, DailyLogSummaryPage, Reflection } from "@/types/daily-logs";

function today(timeZone: string): string {
  return localDateInTimeZone(new Date(), timeZone);
}

export function DailyLogsExperience({
  localDate: routeDate,
  initialTimeZone,
}: {
  localDate?: string;
  initialTimeZone?: string;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [timeZone, setTimeZone] = useState(initialTimeZone ?? getBrowserTimeZone());
  const currentTimeZone = useRef(timeZone);
  const [timeZoneReady, setTimeZoneReady] = useState(false);
  const [selectedDate, setSelectedDate] = useState(
    routeDate && isCanonicalLocalDate(routeDate) ? routeDate : today(getBrowserTimeZone()),
  );
  const [summaries, setSummaries] = useState<DailyLogSummaryPage | null>(null);
  const [dailyLog, setDailyLog] = useState<DailyLog | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const timeZoneChangedByUser = useRef(false);
  const dateSelectedByUser = useRef(false);
  const requestSequence = useRef(0);

  useEffect(() => {
    let active = true;
    void getPreferences()
      .then((preferences) => {
        if (!active) return;
        const savedTimeZone = initialTimeZone ?? preferences.timeZone ?? getBrowserTimeZone();
        const effectiveTimeZone = timeZoneChangedByUser.current ? currentTimeZone.current : savedTimeZone;
        if (!timeZoneChangedByUser.current) { currentTimeZone.current = savedTimeZone; setTimeZone(savedTimeZone); }
        if (!routeDate && !dateSelectedByUser.current) setSelectedDate(today(effectiveTimeZone));
        setTimeZoneReady(true);
      })
      .catch(() => {
        if (!active) return;
        const browserTimeZone = initialTimeZone ?? getBrowserTimeZone();
        const effectiveTimeZone = timeZoneChangedByUser.current ? currentTimeZone.current : browserTimeZone;
        if (!timeZoneChangedByUser.current) { currentTimeZone.current = browserTimeZone; setTimeZone(browserTimeZone); }
        if (!routeDate && !dateSelectedByUser.current) setSelectedDate(today(effectiveTimeZone));
        setTimeZoneReady(true);
      });
    return () => { active = false; };
  }, [initialTimeZone, routeDate]);

  useEffect(() => {
    if (routeDate && isCanonicalLocalDate(routeDate)) setSelectedDate(routeDate);
  }, [routeDate]);

  const selectedDateLabel = routeDate && pathname !== "/daily-logs" ? routeDate : selectedDate;
  const monthDate = useMemo(() => monthStart(selectedDateLabel), [selectedDateLabel]);
  const range = useMemo(() => calendarRange(monthDate), [monthDate]);

  const load = useCallback(async () => {
    const requestId = ++requestSequence.current;
    setLoading(true);
    setError(null);
    try {
      const [summaryPage, detail] = await Promise.all([
        getDailyLogs({ ...range, timeZone }),
        getDailyLog(selectedDateLabel, timeZone),
      ]);
      if (requestId !== requestSequence.current) return;
      setSummaries(summaryPage);
      setDailyLog(detail);
    } catch (cause) {
      if (requestId !== requestSequence.current) return;
      setError(cause instanceof ApiRequestError ? cause.message : "Daily logs could not be loaded.");
    } finally {
      if (requestId === requestSequence.current) setLoading(false);
    }
  }, [range, selectedDateLabel, timeZone]);

  useEffect(() => {
    if (timeZoneReady) void load();
    return () => { requestSequence.current += 1; };
  }, [load, timeZoneReady]);

  const navigateToDate = (date: string) => {
    requestAppNavigation(() => {
      dateSelectedByUser.current = true;
      setSelectedDate(date);
      router.push(`/daily-logs/${date}?timeZone=${encodeURIComponent(timeZone)}`);
    });
  };

  const changeMonth = (direction: number) => {
    requestAppNavigation(() => {
      const nextDate = shiftLocalMonth(selectedDateLabel, direction);
      dateSelectedByUser.current = true;
      setSelectedDate(nextDate);
      if (pathname !== "/daily-logs") router.push(`/daily-logs/${nextDate}?timeZone=${encodeURIComponent(timeZone)}`);
    });
  };

  const changeTimeZone = (nextTimeZone: string) => {
    requestAppNavigation(() => {
      timeZoneChangedByUser.current = true;
      currentTimeZone.current = nextTimeZone;
      setTimeZone(nextTimeZone);
      // Date navigation remounts this workspace; keep the projection timezone in the URL.
      router.replace(`${pathname}?timeZone=${encodeURIComponent(nextTimeZone)}`, { scroll: false });
    });
  };

  const handleReflectionSaved = (reflection: Reflection) => {
    setDailyLog((current) => current ? { ...current, reflection } : current);
    setSummaries((current) => current ? {
      ...current,
      days: current.days.map((day) => day.localDate === selectedDateLabel
        ? { ...day, hasReflectionContent: Boolean(reflection.contentText.trim()) }
        : day),
    } : current);
  };

  const selectedDetail = dailyLog && dailyLog.localDate === selectedDateLabel ? dailyLog : null;
  const showCalendarOnMobile = pathname === "/daily-logs";
  const showDetailOnMobile = pathname !== "/daily-logs";

  return (
    <main className="mx-auto max-w-[1400px] px-4 pb-16 pt-8 md:px-10 md:pt-12">
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--rt-orange)]">
            your days, as they happened
          </p>
          <h1 className="mt-2 font-display text-4xl tracking-[-0.06em] md:text-6xl">
            daily logs<span className="text-[var(--rt-orange)]">.</span>
          </h1>
        </div>
        <label className="text-sm text-[var(--rt-ink-muted)]">
          Timezone
          <select
            aria-label="Timezone"
            value={timeZone}
            onChange={(event) => changeTimeZone(event.target.value)}
            className="ml-2 rounded-full border border-[var(--rt-line)] bg-[var(--rt-paper)] px-3 py-2 text-[var(--rt-ink)] focus-visible:outline-2 focus-visible:outline-[var(--rt-orange)]"
          >
            <option value={timeZone}>{timeZone}</option>
            <option value="UTC">UTC</option>
            <option value="America/New_York">America/New_York</option>
            <option value="Europe/Berlin">Europe/Berlin</option>
            <option value="Asia/Tokyo">Asia/Tokyo</option>
            <option value="Australia/Lord_Howe">Australia/Lord_Howe</option>
          </select>
        </label>
      </div>

      {error && (
        <div role="alert" className="mb-5 rounded-2xl border border-[var(--rt-orange)] bg-[var(--rt-orange-soft)] p-4">
          <p>{error}</p>
          <Button onClick={() => void load()} className="mt-3 rounded-full">Retry</Button>
        </div>
      )}
      {loading && (
        <p role="status" className="mb-4 text-sm text-[var(--rt-ink-muted)]">Loading daily logs…</p>
      )}
      <div className="grid gap-6 lg:grid-cols-[minmax(300px,0.8fr)_minmax(0,1.2fr)]">
        <div className={showCalendarOnMobile ? "" : "hidden lg:block"}>
          <DailyLogCalendar
            monthDate={monthDate}
            range={range}
            summaries={summaries?.days ?? []}
            selectedDate={selectedDateLabel}
            onSelectDate={navigateToDate}
            onMonthChange={changeMonth}
            canMovePrevious={monthDate !== "0001-01-01"}
            canMoveNext={monthDate !== "9999-12-01"}
          />
        </div>
        <section aria-label={`Daily log for ${selectedDateLabel}`} className={`${showDetailOnMobile ? "" : "hidden lg:block"} min-w-0`}>
          {selectedDetail && (
            <DailyLogDetail
              dailyLog={selectedDetail}
              onReflectionSaved={handleReflectionSaved}
              onReload={async () => (await getDailyLog(selectedDateLabel, timeZone)).reflection}
            />
          )}
        </section>
      </div>
    </main>
  );
}
