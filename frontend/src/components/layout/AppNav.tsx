"use client";

import Link from "next/link";
import { LayoutDashboard, LogOut, Timer } from "lucide-react";
import { SignOutButton } from "@/components/auth/SignOutButton";
import { Button } from "@/components/ui/button";

type AppRoute = "dashboard" | "tracker";

export function AppNav({
  activeRoute,
  signOutRedirectTo = "/signin",
}: {
  activeRoute: AppRoute;
  signOutRedirectTo?: string;
}) {
  return (
    <header className="border-b border-[var(--rt-line)] bg-[var(--rt-paper)]/90 backdrop-blur-md">
      <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-4 px-6 py-4 md:px-10">
        <Link href="/" className="font-display text-2xl tracking-[-0.02em]">
          rotrack<span className="text-[var(--rt-orange)]">.</span>
        </Link>
        <nav aria-label="Application" className="flex items-center gap-2">
          <NavItem route="dashboard" activeRoute={activeRoute} href="/dashboard" icon={<LayoutDashboard aria-hidden="true" />}>
            dashboard
          </NavItem>
          <NavItem route="tracker" activeRoute={activeRoute} href="/tracker" icon={<Timer aria-hidden="true" />}>
            tracker
          </NavItem>
          <SignOutButton
            aria-label="log out"
            redirectTo={signOutRedirectTo}
            className="rounded-full text-[var(--rt-ink-muted)] hover:bg-[var(--rt-cream-soft)] hover:text-[var(--rt-ink)]"
          >
            <LogOut aria-hidden="true" />
            <span className="hidden sm:inline">log out</span>
          </SignOutButton>
        </nav>
      </div>
    </header>
  );
}

function NavItem({
  route,
  activeRoute,
  href,
  icon,
  children,
}: {
  route: AppRoute;
  activeRoute: AppRoute;
  href: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  if (route === activeRoute) {
    return (
      <span aria-current="page" className="rounded-full bg-[var(--rt-cream-soft)] px-4 py-2 text-sm font-semibold">
        {children}
      </span>
    );
  }

  return (
    <Button variant="ghost" asChild className="rounded-full">
      <Link href={href}>
        {icon}
        {children}
      </Link>
    </Button>
  );
}
