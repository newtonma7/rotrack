"use client";

import { useEffect, useState } from "react";

export function Clock3DLED() {
  const [mounted, setMounted] = useState(false);
  const [time, setTime] = useState("00:00:00");

  useEffect(() => {
    setMounted(true);
    const update = () => {
      const now = new Date();
      const h = now.getHours().toString().padStart(2, "0");
      const m = now.getMinutes().toString().padStart(2, "0");
      const s = now.getSeconds().toString().padStart(2, "0");
      setTime(`${h}:${m}:${s}`);
    };
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, []);

  if (!mounted) {
    return (
      <div
        className="flex items-center justify-center w-[360px] h-[120px] sm:w-[440px] sm:h-[140px]"
        aria-hidden="true"
      />
    );
  }

  return (
    <div
      className="flex items-center justify-center w-[360px] sm:w-[440px] md:w-[520px]"
      role="timer"
      aria-live="polite"
      aria-label={`Current local time: ${time}`}
    >
      {/* Outer casing / bezel */}
      <div className="rounded-2xl bg-zinc-800 p-1.5 shadow-[0_4px_12px_rgba(0,0,0,0.3)]">
        {/* Recessed display face */}
        <div
          className="flex items-center justify-center rounded-xl bg-white px-4 py-3"
          style={{
            boxShadow: "inset 0 2px 6px rgba(0,0,0,0.15), inset 0 -1px 2px rgba(255,255,255,0.5)",
          }}
        >
        <span
          className="inline-block min-w-[8em] text-center text-[clamp(3.5rem,10vw,6rem)] tabular-nums font-normal tracking-[0.1em] text-[#ff8c00]"
          style={{
            fontFamily: '"Digital-7", "Courier New", monospace',
            textShadow: "0 0 8px rgba(255, 140, 0, 0.5)",
          }}
        >
          {time}
        </span>
        </div>
      </div>
    </div>
  );
}
