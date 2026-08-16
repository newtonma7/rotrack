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
  { href: "/settings", label: "settings" },
] as const;

export function ApplicationHeader() {
  const pathname = usePathname();

  return (
    <header className="relative z-20 px-[clamp(15px,4vw,56px)] pt-3 md:pt-5">
      <div className="mx-auto flex max-w-[1400px] items-center justify-between gap-2 rounded-full border border-[var(--rt-line)] bg-[var(--rt-paper)]/80 px-3 py-2 shadow-sm backdrop-blur-md md:px-4">
        <Link href="/" className="font-display text-xl tracking-[-0.04em]">
          rotrack<span className="text-[var(--rt-orange)]">.</span>
        </Link>
        <nav aria-label="Application" className="flex min-w-0 items-center justify-end gap-0.5">
          {links.map((link) => pathname === link.href ? (
            <span key={link.href} aria-current="page" className="flex h-8 items-center rounded-full bg-[var(--rt-cream-soft)] px-1.5 text-[0.6rem] font-semibold sm:px-2.5 sm:text-xs md:px-3">
              {link.label}
            </span>
          ) : (
            <Button key={link.href} variant="ghost" asChild className="h-8 rounded-full px-1.5 text-[0.6rem] font-normal text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)] sm:px-2 sm:text-xs md:px-3">
              <Link href={link.href}>{link.label}</Link>
            </Button>
          ))}
          <SignOutButton className="h-8 rounded-full px-1.5 text-[0.6rem] text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)] sm:px-2 sm:text-xs md:px-3" />
        </nav>
      </div>
    </header>
  );
}
