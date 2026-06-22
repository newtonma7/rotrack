"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/context/AuthProvider";
import { SignOutButton } from "@/components/auth/SignOutButton";

/* ---------------------------------------------------------------
   rotrack — landing page
   Layer: frontend marketing route. Auth-aware nav uses AuthProvider
   (Supabase session) to route logged-in users to /tracker instead of signup.
   Aesthetic: "Tangerine Studio"
   Warm cream canvas · Asimov orange pops · playful bubbles ·
   chunky display type + clean body sans · subtle grain
--------------------------------------------------------------- */

const BG_IMG = "/efecto-2026-02-21T04-51-28.webp";

export default function LandingPage() {
  const [time, setTime] = useState("00:00:00");

  useEffect(() => {
    const tick = () => {
      const d = new Date();
      setTime(
        `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}`,
      );
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

  return (
    <main className="min-h-screen w-full bg-[var(--rt-cream)] text-[var(--rt-ink)] overflow-x-clip">
      {/* viewport 1: nav + hero share a single screen */}
      <div className="min-h-svh flex flex-col">
        <Nav />
        <Hero time={time} />
      </div>
      <Categories />
      <HowItWorks />
      <FinalCta />
      <Footer />
    </main>
  );
}

/* ============================================================ */
/*  NAV                                                          */
/* ============================================================ */
function Nav() {
  const { user, loading } = useAuth();
  const isLoggedIn = !loading && !!user;
  const startHref = isLoggedIn ? "/tracker" : "/signup";

  return (
    <header className="relative z-30">
      <div className="mx-auto max-w-[1400px] px-6 md:px-10 pt-5">
        <nav className="flex items-center justify-between rounded-full bg-[var(--rt-paper)]/30 backdrop-blur-sm border border-[var(--rt-line)]/50 pl-5 pr-2 py-2 shadow-[0_1px_0_rgba(10,10,10,0.02),0_12px_40px_-28px_rgba(10,10,10,0.18)]">
          <Link href="/" className="flex items-center gap-2 group">
            <span className="font-display text-[1.35rem] leading-none tracking-tight">
              rotrack
            </span>
          </Link>
          <ul className="hidden md:flex items-center gap-8 text-[0.9rem] text-[var(--rt-ink-soft)]">
            <li><a href="#categories" className="hover:text-[var(--rt-orange)] transition-colors">The two</a></li>
            <li><a href="#how" className="hover:text-[var(--rt-orange)] transition-colors">How it works</a></li>
          </ul>
          <div className="flex items-center gap-1.5">
            {isLoggedIn ? (
              <>
                <span className="hidden md:inline text-[0.85rem] text-[var(--rt-ink-muted)] max-w-[140px] truncate">
                  {user.email}
                </span>
                <SignOutButton
                  redirectTo="/"
                  className="inline-flex px-3 sm:px-4 py-2 h-auto text-[0.85rem] sm:text-[0.9rem] text-[var(--rt-ink-soft)] hover:text-[var(--rt-ink)] hover:bg-transparent"
                />
              </>
            ) : (
              <Link
                href="/signin"
                className="hidden sm:inline-flex px-4 py-2 text-[0.9rem] text-[var(--rt-ink-soft)] hover:text-[var(--rt-ink)] transition-colors"
              >
                Sign in
              </Link>
            )}
            <Link
              href={startHref}
              className="inline-flex items-center gap-1.5 rounded-full bg-[var(--rt-ink)] text-[var(--rt-cream)] px-4 py-2 text-[0.9rem] font-medium hover:bg-[var(--rt-orange)] transition-colors"
            >
              Start tracking
              <ArrowRight />
            </Link>
          </div>
        </nav>
      </div>
    </header>
  );
}

function LogoMark() {
  return (
    <span className="relative inline-block h-7 w-7">
      <span className="absolute inset-0 rounded-full bg-[var(--rt-orange)]" />
      <span className="absolute inset-[22%] rounded-full bg-[var(--rt-cream)]" />
      <span className="absolute left-1/2 top-1/2 h-[3px] w-[40%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--rt-ink)]" />
      <span className="absolute left-1/2 top-1/2 h-[40%] w-[3px] -translate-x-1/2 -translate-y-[85%] rounded-full bg-[var(--rt-ink)]" />
    </span>
  );
}

function ArrowRight() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
      <path d="M3 7h8m0 0L7.5 3.5M11 7l-3.5 3.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

/* ============================================================ */
/*  HERO                                                         */
/* ============================================================ */
function Hero({ time }: { time: string }) {
  const { user, loading } = useAuth();
  const startHref = !loading && user ? "/tracker" : "/signup";

  return (
    <section className="relative flex-1 flex items-center py-10 md:py-12">
      {/* decorative bubbles — kept off the headline reading path */}
      <Bubble className="left-[8%] bottom-[-2%] h-16 w-16 bg-[var(--rt-orange)] rt-float-slow" />
      <Bubble className="left-[2%] bottom-[38%] h-3 w-3 bg-[var(--rt-ink)] rt-float" />
      <Bubble className="left-[28%] bottom-[3%] h-5 w-5 bg-[var(--rt-ink)] rt-float-slow" />
      <Bubble className="right-[44%] top-[14%] h-6 w-6 bg-[var(--rt-ink)] rt-float" />
      <Bubble className="right-[8%] bottom-[6%] h-20 w-20 border-2 border-[var(--rt-ink)] bg-transparent rt-float-slow" />
      <Bubble className="right-[3%] top-[34%] h-2.5 w-2.5 bg-[var(--rt-orange)] rt-float" />

      <div className="mx-auto max-w-[1400px] px-6 md:px-10 grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-6 items-center">
        {/* left — copy */}
        <div className="lg:col-span-7 lg:self-start lg:pt-2 relative z-10">
          <h1
            className="rt-rise text-[clamp(3rem,7.5vw,6.8rem)] leading-[0.95] tracking-[-0.035em]"
            style={{
              fontFamily: '"Figtree", ui-sans-serif, system-ui, sans-serif',
              fontWeight: 600,
              animationDelay: "80ms",
            }}
          >
            track your
            <br />
            <span className="relative inline-block">
              <span className="relative z-10 text-[var(--rt-orange)]">rot</span>
              <Underline />
            </span>
            , own
            <br />
            the <span className="italic font-normal">clock</span>
          </h1>

          <p
            className="rt-rise mt-8 max-w-xl text-[1.05rem] md:text-[1.15rem] leading-relaxed text-[var(--rt-ink-soft)]"
            style={{ animationDelay: "160ms" }}
          >
            Rotrack is a playful time-study tool that splits your day into two
            honest buckets: <b className="text-[var(--rt-ink)]">rot</b> and{" "}
            <b className="text-[var(--rt-ink)]">work</b>. If you&apos;re not
            actively working, you&apos;re rotting. Log minutes, watch patterns
            emerge, reclaim the hours you didn&apos;t know you lost.
          </p>

          <div
            className="rt-rise mt-10 flex flex-wrap items-center gap-3"
            style={{ animationDelay: "240ms" }}
          >
            <Link
              href={startHref}
              className="group inline-flex items-center gap-2 rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] px-6 py-3.5 font-medium shadow-[0_10px_30px_-10px_rgba(236,107,14,0.6)] hover:bg-[var(--rt-orange-deep)] transition-all hover:-translate-y-0.5"
            >
              Start tracking free
              <span className="transition-transform group-hover:translate-x-1"><ArrowRight /></span>
            </Link>
            <a
              href="#how"
              className="inline-flex items-center gap-2 rounded-full border border-[var(--rt-ink)] px-6 py-3.5 font-medium hover:bg-[var(--rt-ink)] hover:text-[var(--rt-cream)] transition-colors"
            >
              See how it works
            </a>
          </div>

        </div>

        {/* right — graphic panel */}
        <div className="lg:col-span-5 relative lg:-mr-4 xl:-mr-12">
          <HeroGraphic time={time} />
        </div>
      </div>
    </section>
  );
}

function Underline() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 220 14"
      className="absolute left-0 right-0 -bottom-2 w-full h-3"
      preserveAspectRatio="none"
    >
      <path
        d="M2 8 C 50 2, 120 14, 218 6"
        stroke="var(--rt-ink)"
        strokeWidth="3"
        strokeLinecap="round"
        fill="none"
      />
    </svg>
  );
}

function HeroGraphic({ time }: { time: string }) {
  return (
    <div className="relative aspect-[4/5] w-full max-w-[520px] ml-auto">
      {/* orange stripe panel (the webp) — tilted, framed */}
      <div
        className="absolute inset-0 rounded-[32px] overflow-hidden border border-[var(--rt-line)] shadow-[0_40px_80px_-30px_rgba(10,10,10,0.3)] rotate-[3deg]"
        style={{
          backgroundImage: `url(${BG_IMG})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-black/15" />
      </div>

      {/* floating clock card */}
      <div className="absolute left-8 sm:left-16 top-6 rotate-[-4deg] rt-float">
        <div className="rounded-2xl bg-[var(--rt-paper)] border border-[var(--rt-line)] shadow-[0_20px_50px_-20px_rgba(10,10,10,0.35)] p-4 w-[260px]">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[0.7rem] uppercase tracking-widest text-[var(--rt-ink-muted)]">right now</span>
            <span className="h-2 w-2 rounded-full bg-[var(--rt-orange)] animate-pulse" />
          </div>
          <div
            className="text-[2.4rem] leading-none text-[var(--rt-orange)] tabular-nums tracking-[0.08em]"
            style={{ fontFamily: '"Digital-7", monospace', textShadow: "0 0 12px rgba(236,107,14,0.35)" }}
          >
            {time}
          </div>
          <div className="mt-3 flex gap-1">
            <span className="h-1.5 flex-[3] rounded-full bg-[var(--rt-orange)]" />
            <span className="h-1.5 flex-[2] rounded-full bg-[var(--rt-ink)]" />
            <span className="h-1.5 flex-[5] rounded-full bg-[var(--rt-line)]" />
          </div>
        </div>
      </div>

      {/* floating bucket chip */}
      <div className="absolute right-0 sm:right-[-28px] bottom-16 rotate-[5deg] rt-float-slow">
        <div className="rounded-2xl bg-[var(--rt-ink)] text-[var(--rt-cream)] p-4 w-[210px] shadow-[0_20px_50px_-20px_rgba(10,10,10,0.55)]">
          <div className="flex items-center justify-between">
            <span className="text-[0.7rem] uppercase tracking-widest opacity-70">today&rsquo;s rot</span>
            <span className="font-display text-[1.6rem] leading-none text-[var(--rt-orange)]">2h 14m</span>
          </div>
          <div className="mt-3 text-[0.8rem] opacity-80">
            down 38% from Tuesday
          </div>
        </div>
      </div>

      {/* orbiting shape */}
      <div className="absolute -top-6 right-6 h-20 w-20 rt-spin-slow">
        <div className="h-full w-full rounded-full border-2 border-[var(--rt-ink)] border-dashed" />
        <span className="absolute -top-2 left-1/2 -translate-x-1/2 h-4 w-4 rounded-full bg-[var(--rt-orange)]" />
      </div>
    </div>
  );
}

function Bubble({ className = "" }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`pointer-events-none absolute rounded-full ${className}`}
    />
  );
}

/* ============================================================ */
/*  CATEGORIES                                                   */
/* ============================================================ */
type Bucket = {
  key: "rot" | "work";
  label: string;
  tagline: string;
  body: string;
  examples: string[];
};

const BUCKETS: Bucket[] = [
  {
    key: "rot",
    label: "Rot",
    tagline: "The scroll. The spiral.",
    body: "Time that quietly disappears. Autoplay feeds, 2 a.m. reddit, tab-flipping, waiting-to-start. If you&apos;re not in active work, it&apos;s rot. Log it without guilt &mdash; just see it.",
    examples: ["tiktok", "reddit hole", "youtube autoplay", "doomscroll"],
  },
  {
    key: "work",
    label: "Work",
    tagline: "The real thing.",
    body: "Focused, intentional effort on what actually matters to you. School, craft, training, creating. Rotrack helps you see how much of it is truly there.",
    examples: ["deep focus", "study block", "building", "practicing"],
  },
];

function Categories() {
  return (
    <section
      id="categories"
      className="relative min-h-svh flex items-center py-24 md:py-28 bg-[var(--rt-paper)] border-y border-[var(--rt-line)] overflow-hidden"
    >
      <div className="absolute inset-0 rt-grain opacity-30 pointer-events-none" />
      <Bubble className="left-[-40px] top-20 h-40 w-40 bg-[var(--rt-orange-soft)]/60 rt-float-slow" />
      <Bubble className="right-[-60px] bottom-10 h-56 w-56 border-2 border-[var(--rt-ink)]/10 rt-float-slow" />
      <Bubble className="right-[12%] top-[14%] h-3 w-3 bg-[var(--rt-ink)] rt-float" />
      <Bubble className="left-[48%] top-[8%] h-6 w-6 bg-[var(--rt-orange)] rt-float" />
      <Bubble className="left-[10%] bottom-[8%] h-2.5 w-2.5 bg-[var(--rt-orange)] rt-float-slow" />

      <div className="mx-auto max-w-[1400px] px-6 md:px-10">
        <div className="max-w-3xl">
          <span className="inline-block text-[0.8rem] uppercase tracking-[0.2em] text-[var(--rt-orange)] font-medium">
            &sect; 01 &mdash; the two buckets
          </span>
          <h2 className="mt-4 font-display text-[clamp(2.4rem,5.5vw,4.8rem)] leading-[0.95] tracking-tight">
            every minute lands
            <br />
            in <span className="text-[var(--rt-orange)]">one of two</span> places
          </h2>
          <p className="mt-6 text-[1.05rem] text-[var(--rt-ink-soft)] max-w-xl">
            No 40-category tagging system. No guilt-trips. Just a framework
            honest enough to change your behaviour.
          </p>
        </div>

        <div className="mt-16 grid grid-cols-1 md:grid-cols-2 gap-5 max-w-4xl">
          {BUCKETS.map((b, i) => (
            <BucketCard key={b.key} b={b} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}

function BucketCard({ b, index }: { b: Bucket; index: number }) {
  const isWork = b.key === "work";
  return (
    <article
      className={[
        "group relative overflow-hidden rounded-[28px] border p-7 min-h-[380px] flex flex-col justify-between transition-transform hover:-translate-y-1",
        isWork
          ? "bg-[var(--rt-ink)] text-[var(--rt-cream)] border-[var(--rt-ink)]"
          : "bg-[var(--rt-paper)] border-[var(--rt-line)]",
      ].join(" ")}
    >
      {/* decorative orb */}
      <span
        aria-hidden="true"
        className={[
          "absolute -right-10 -top-10 h-40 w-40 rounded-full transition-transform duration-700 group-hover:scale-110",
          b.key === "rot" && "bg-[var(--rt-orange)]",
          b.key === "work" && "bg-[var(--rt-orange)]/90",
        ].filter(Boolean).join(" ")}
      />

      <div className="relative">
        <div className="flex items-center gap-3">
          <span className={[
            "font-display text-[1rem] tabular-nums tracking-widest",
            isWork ? "text-[var(--rt-cream)]/70" : "text-[var(--rt-ink-muted)]",
          ].join(" ")}>
            0{index + 1}
          </span>
          <span className={[
            "h-px flex-1",
            isWork ? "bg-[var(--rt-cream)]/20" : "bg-[var(--rt-line)]",
          ].join(" ")} />
        </div>

        <h3 className="mt-6 font-display text-[3rem] leading-none tracking-tight">
          {b.label}
        </h3>
        <p className={[
          "mt-2 text-[1rem] font-medium",
          isWork ? "text-[var(--rt-orange)]" : "text-[var(--rt-orange-deep)]",
        ].join(" ")}>
          {b.tagline}
        </p>
        <p
          className={[
            "mt-4 text-[0.95rem] leading-relaxed",
            isWork ? "text-[var(--rt-cream)]/80" : "text-[var(--rt-ink-soft)]",
          ].join(" ")}
          dangerouslySetInnerHTML={{ __html: b.body }}
        />
      </div>

      <div className="relative mt-6 flex flex-wrap gap-1.5">
        {b.examples.map((ex) => (
          <span
            key={ex}
            className={[
              "inline-flex items-center rounded-full px-2.5 py-1 text-[0.75rem] border",
              isWork
                ? "border-[var(--rt-cream)]/20 text-[var(--rt-cream)]/85"
                : "border-[var(--rt-line)] text-[var(--rt-ink-soft)] bg-[var(--rt-cream)]",
            ].join(" ")}
          >
            {ex}
          </span>
        ))}
      </div>
    </article>
  );
}

/* ============================================================ */
/*  HOW IT WORKS                                                 */
/* ============================================================ */
function HowItWorks() {
  const steps = [
    {
      n: "01",
      t: "Tap what you\u2019re doing",
      d: "One button per bucket. No categories to invent, no journaling required. Start the timer when you start; stop it when you stop.",
    },
    {
      n: "02",
      t: "Let the week shape up",
      d: "Rotrack quietly stitches your taps into a week-view. Orange for rot, ink for work. Patterns surface on their own.",
    },
    {
      n: "03",
      t: "Notice, then nudge",
      d: "Set gentle targets (or don\u2019t). The point isn\u2019t to be a monk &mdash; it\u2019s to see the math, and pick one thing to trade next week.",
    },
  ];

  return (
    <section id="how" className="relative min-h-svh flex items-center py-24 md:py-28 bg-[var(--rt-cream)] overflow-hidden">
      <div className="absolute inset-0 rt-grain opacity-20 pointer-events-none" />
      <Bubble className="left-[-60px] top-[18%] h-48 w-48 border-2 border-[var(--rt-ink)]/10 rt-float-slow" />
      <Bubble className="right-[6%] top-[12%] h-4 w-4 bg-[var(--rt-orange)] rt-float" />
      <Bubble className="right-[18%] bottom-[14%] h-24 w-24 bg-[var(--rt-orange-soft)]/50 rt-float-slow" />
      <Bubble className="left-[32%] bottom-[6%] h-2.5 w-2.5 bg-[var(--rt-ink)] rt-float" />

      <div className="relative mx-auto max-w-[1400px] px-6 md:px-10">
        <div className="flex flex-col lg:flex-row lg:items-end lg:justify-between gap-8">
          <div className="max-w-2xl">
            <span className="inline-block text-[0.8rem] uppercase tracking-[0.2em] text-[var(--rt-orange)] font-medium">
              &sect; 02 &mdash; how it works
            </span>
            <h2 className="mt-4 font-display text-[clamp(2.4rem,5.5vw,4.8rem)] leading-[0.95] tracking-tight">
              two taps. one honest picture.
            </h2>
          </div>
          <p className="text-[1rem] text-[var(--rt-ink-soft)] max-w-sm">
            Rotrack is designed to be low-friction. You should forget it&rsquo;s
            there &mdash; until the data shows up and tells the truth.
          </p>
        </div>

        <ol className="mt-14 grid grid-cols-1 md:grid-cols-3 gap-0 md:gap-6 border-t border-[var(--rt-line)]">
          {steps.map((s, i) => (
            <li
              key={s.n}
              className={[
                "relative py-10 md:py-12",
                i !== steps.length - 1 ? "border-b md:border-b-0 md:border-r border-[var(--rt-line)]" : "",
                "pr-0 md:pr-6",
              ].join(" ")}
            >
              <div className="flex items-baseline gap-4">
                <span className="font-display text-[3.2rem] leading-none text-[var(--rt-orange)]">
                  {s.n}
                </span>
                <span className="h-px flex-1 bg-[var(--rt-line)]" />
              </div>
              <h3 className="mt-5 text-[1.5rem] leading-tight tracking-[-0.02em] font-semibold">
                {s.t}
              </h3>
              <p className="mt-3 text-[0.95rem] leading-relaxed text-[var(--rt-ink-soft)]">
                {s.d}
              </p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

/* ============================================================ */
/*  FINAL CTA                                                    */
/* ============================================================ */
function FinalCta() {
  const { user, loading } = useAuth();
  const isLoggedIn = !loading && !!user;
  const startHref = isLoggedIn ? "/tracker" : "/signup";

  return (
    <section className="relative min-h-svh flex items-center py-24 md:py-28 overflow-hidden">
      <Bubble className="left-[10%] top-10 h-10 w-10 bg-[var(--rt-orange)] rt-float" />
      <Bubble className="right-[8%] bottom-16 h-28 w-28 border-2 border-[var(--rt-ink)] bg-transparent rt-float-slow" />
      <Bubble className="left-[48%] bottom-4 h-4 w-4 bg-[var(--rt-ink)]" />

      <div className="mx-auto max-w-[1100px] px-6 md:px-10">
        <div
          className="relative rounded-[40px] overflow-hidden border border-[var(--rt-ink)] bg-[var(--rt-ink)] text-[var(--rt-cream)] px-8 md:px-16 py-16 md:py-24"
        >
          {/* stripe accent using the webp */}
          <div
            aria-hidden="true"
            className="absolute -right-24 top-0 bottom-0 w-[45%] opacity-70"
            style={{
              backgroundImage: `url(${BG_IMG})`,
              backgroundSize: "cover",
              backgroundPosition: "center",
              maskImage:
                "linear-gradient(to left, black 40%, transparent 100%)",
              WebkitMaskImage:
                "linear-gradient(to left, black 40%, transparent 100%)",
            }}
          />

          <div className="relative max-w-2xl">
            <span className="inline-block text-[0.8rem] uppercase tracking-[0.2em] text-[var(--rt-orange)] font-medium">
              ready when you are
            </span>
            <h2 className="mt-4 font-display text-[clamp(2.4rem,6vw,5.2rem)] leading-[0.95] tracking-tight">
              stop guessing where
              <br />
              your <span className="text-[var(--rt-orange)]">hours</span> go.
            </h2>
            <p className="mt-6 text-[1.05rem] text-[var(--rt-cream)]/75 max-w-lg">
              Join rotrack and start the first honest week of time-logging
              you&rsquo;ll actually stick with.
            </p>
            <div className="mt-9 flex flex-wrap items-center gap-3">
              <Link
                href={startHref}
                className="inline-flex items-center gap-2 rounded-full bg-[var(--rt-orange)] text-[var(--rt-cream)] px-6 py-3.5 font-medium hover:bg-[var(--rt-orange-deep)] transition-colors"
              >
                Start tracking free
                <ArrowRight />
              </Link>
              {!isLoggedIn && (
                <Link
                  href="/signin"
                  className="inline-flex items-center gap-2 rounded-full border border-[var(--rt-cream)]/30 text-[var(--rt-cream)] px-6 py-3.5 font-medium hover:bg-[var(--rt-cream)] hover:text-[var(--rt-ink)] transition-colors"
                >
                  I have an account
                </Link>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ============================================================ */
/*  FOOTER                                                       */
/* ============================================================ */
function Footer() {
  const { user, loading } = useAuth();
  const isLoggedIn = !loading && !!user;

  return (
    <footer className="border-t border-[var(--rt-line)] bg-[var(--rt-cream)]">
      <div className="mx-auto max-w-[1400px] px-6 md:px-10 py-10 flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-2 text-[var(--rt-ink-soft)]">
          <LogoMark />
          <span className="font-display text-[1.1rem] tracking-tight text-[var(--rt-ink)]">
            rotrack
          </span>
          <span className="text-[0.85rem]">&mdash; track the rot.</span>
        </div>
        <div className="text-[0.85rem] text-[var(--rt-ink-muted)] flex items-center gap-5">
          {isLoggedIn ? (
            <Link href="/tracker" className="hover:text-[var(--rt-orange)]">Tracker</Link>
          ) : (
            <Link href="/signup" className="hover:text-[var(--rt-orange)]">Sign up</Link>
          )}
          <span>&copy; {new Date().getFullYear()}</span>
        </div>
      </div>
    </footer>
  );
}
