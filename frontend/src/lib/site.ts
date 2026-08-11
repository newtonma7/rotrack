import type { Metadata } from "next";

export const SITE_NAME = "rotrack";
export const SITE_DESCRIPTION = "A simple study tracker for seeing where your time goes: work, rot, or untracked.";
export const SITE_ICON = "/favicon.ico";

function resolveSiteOrigin(): string {
  const configuredOrigin = process.env.NEXT_PUBLIC_SITE_URL?.trim();

  if (configuredOrigin) {
    const origin = new URL(configuredOrigin);
    if (!/^https?:$/.test(origin.protocol)) {
      throw new Error("NEXT_PUBLIC_SITE_URL must use http or https");
    }
    if (origin.hostname.endsWith(".vercel.app") || origin.hostname.endsWith(".vercel.sh")) {
      throw new Error("NEXT_PUBLIC_SITE_URL must be the canonical production origin, not a Vercel preview URL");
    }
    return origin.origin;
  }

  // Local development stays usable, but every production build must provide
  // the canonical origin so metadata never falls back to localhost or a preview URL.
  if (process.env.NODE_ENV === "production") {
    throw new Error("NEXT_PUBLIC_SITE_URL is required for production builds");
  }
  return "http://localhost:3000";
}

export const SITE_ORIGIN = resolveSiteOrigin();
export const SITE_URL = new URL(SITE_ORIGIN);

export function createPageMetadata({
  title,
  description,
  path,
  noIndex = false,
}: {
  title: string;
  description: string;
  path: string;
  noIndex?: boolean;
}): Metadata {
  const canonical = new URL(path, SITE_URL).toString();

  return {
    title,
    description,
    alternates: { canonical },
    robots: noIndex ? { index: false, follow: false } : undefined,
    openGraph: {
      type: "website",
      url: canonical,
      title: `${title} | ${SITE_NAME}`,
      description,
      siteName: SITE_NAME,
      images: [{ url: SITE_ICON, alt: SITE_NAME }],
    },
    twitter: {
      card: "summary",
      title: `${title} | ${SITE_NAME}`,
      description,
      images: [SITE_ICON],
    },
  };
}
