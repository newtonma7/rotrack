import type { Metadata } from "next";
import Link from "next/link";
import { SITE_NAME } from "@/lib/site";

export const metadata: Metadata = {
  title: "Page not found",
  description: "The rotrack page you requested does not exist.",
  robots: { index: false, follow: false },
};

function LogoMark() {
  return (
    <span aria-hidden="true" className="relative inline-block h-8 w-8">
      <span className="absolute inset-0 rounded-full bg-[var(--rt-orange)]" />
      <span className="absolute inset-[22%] rounded-full bg-[var(--rt-cream)]" />
      <span className="absolute left-1/2 top-1/2 h-[3px] w-[40%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--rt-ink)]" />
      <span className="absolute left-1/2 top-1/2 h-[40%] w-[3px] -translate-x-1/2 -translate-y-[85%] rounded-full bg-[var(--rt-ink)]" />
    </span>
  );
}

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[var(--rt-cream)] px-6 text-[var(--rt-ink)]">
      <section className="w-full max-w-xl rounded-[32px] border border-[var(--rt-line)] bg-[var(--rt-paper)] p-8 text-center shadow-[0_20px_50px_-20px_rgba(10,10,10,0.15)] md:p-12">
        <div className="mb-6 flex justify-center"><LogoMark /></div>
        <p className="text-[0.8rem] font-semibold uppercase tracking-[0.2em] text-[var(--rt-orange)]">404</p>
        <h1 className="mt-3 font-display text-5xl leading-none">that page wandered off.</h1>
        <p className="mx-auto mt-4 max-w-md text-[var(--rt-ink-muted)]">
          {SITE_NAME} could not find the page you requested.
        </p>
        <Link
          href="/"
          className="mt-8 inline-flex rounded-full bg-[var(--rt-orange)] px-6 py-3 font-medium text-[var(--rt-cream)] transition-colors hover:bg-[var(--rt-orange-deep)]"
        >
          Back to rotrack
        </Link>
      </section>
    </main>
  );
}
