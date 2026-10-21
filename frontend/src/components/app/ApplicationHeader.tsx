"use client";

import Link from "next/link";
import { SignOutButton } from "@/components/auth/SignOutButton";
import { Button } from "@/components/ui/button";
import { usePathname } from "next/navigation";

const links = [
  { href: "/dashboard", label: "dashboard" },
  { href: "/tracker", label: "tracker" },
  { href: "/history", label: "history" },
  { href: "/notes", label: "notes" },
  { href: "/daily-logs", label: "daily logs" },
  { href: "/settings", label: "settings" },
] as const;

export function ApplicationHeader() {
  const pathname = usePathname();

  return (
    <header className="relative z-20 px-[clamp(15px,4vw,56px)] pt-3 md:pt-5">
      <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-[24px] border border-[var(--rt-line)] bg-[var(--rt-paper)]/80 px-3 py-2 shadow-sm backdrop-blur-md sm:flex-nowrap sm:rounded-full md:px-4">
        <Link href="/" className="shrink-0 font-display text-xl tracking-[-0.04em]">
          rotrack<span className="text-[var(--rt-orange)]">.</span>
        </Link>
        <nav aria-label="Application" className="order-last flex w-full min-w-0 items-center justify-between gap-0.5 sm:order-none sm:ml-auto sm:w-auto sm:justify-end">
          {links.map((link) => pathname === link.href || pathname.startsWith(`${link.href}/`) ? (
            <span key={link.href} aria-current="page" className="flex h-8 items-center whitespace-nowrap rounded-full bg-[var(--rt-cream-soft)] px-1.5 text-[0.65rem] font-semibold sm:px-2.5 sm:text-xs md:px-3">
              {link.label}
            </span>
          ) : (
            <Button key={link.href} variant="ghost" asChild className="h-8 rounded-full px-1.5 text-[0.65rem] font-normal text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)] sm:px-2 sm:text-xs md:px-3">
              <Link href={link.href}>{link.label}</Link>
            </Button>
          ))}
        </nav>
        <SignOutButton className="h-8 shrink-0 rounded-full px-1.5 text-xs text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)] sm:px-2 md:px-3" />
      </div>
    </header>
  );
}
