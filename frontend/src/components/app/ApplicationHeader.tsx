"use client";

import Link from "next/link";
import { History, Settings2, Timer, type LucideIcon } from "lucide-react";
import { SignOutButton } from "@/components/auth/SignOutButton";
import { Button } from "@/components/ui/button";
import { usePathname } from "next/navigation";

const links: Array<{ href: string; label: string; icon?: LucideIcon }> = [
  { href: "/dashboard", label: "dashboard" },
  { href: "/tracker", label: "tracker", icon: Timer },
  { href: "/history", label: "history", icon: History },
  { href: "/settings", label: "settings", icon: Settings2 },
] as const;

export function ApplicationHeader() {
  const pathname = usePathname();

  return (
    <header className="border-b border-[var(--rt-line)] bg-[var(--rt-paper)]/90 backdrop-blur-md">
      <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-4 px-6 py-4 md:px-10">
        <Link href="/" className="font-display text-2xl tracking-[-0.02em]">
          rotrack<span className="text-[var(--rt-orange)]">.</span>
        </Link>
        <nav aria-label="Application" className="flex w-full flex-wrap items-center justify-start gap-2 sm:w-auto sm:justify-end">
          {links.map((link) => {
            const Icon = link.icon;
            return pathname === link.href ? (
              <span key={link.href} aria-current="page" className="flex items-center gap-2 rounded-full bg-[var(--rt-cream-soft)] px-4 py-2 text-sm font-semibold">
                {Icon && <Icon aria-hidden="true" className="size-4" />}
                {link.label}
              </span>
            ) : (
              <Button key={link.href} variant="ghost" asChild className="rounded-full">
                <Link href={link.href}>{Icon && <Icon aria-hidden="true" />}{link.label}</Link>
              </Button>
            );
          })}
          <SignOutButton className="rounded-full text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)]" />
        </nav>
      </div>
    </header>
  );
}
