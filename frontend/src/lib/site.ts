import type { Metadata } from "next";

export const SITE_NAME = "rotrack";
export const SITE_DESCRIPTION = "A simple study tracker for seeing where your time goes: work, rot, or untracked.";
export const SITE_ICON = "/favicon.ico";
export const SITE_SHARE_IMAGE = "/efecto-2026-02-21T04-51-28.webp";

function resolveSiteOrigin(): string {
  const explicitOrigin = process.env.NEXT_PUBLIC_SITE_URL?.trim();
  const vercelProductionOrigin = process.env.VERCEL_PROJECT_PRODUCTION_URL?.trim();
  const configuredOrigin = explicitOrigin || (
    vercelProductionOrigin
      ? vercelProductionOrigin.startsWith("http")
        ? vercelProductionOrigin
        : `https://${vercelProductionOrigin}`
      : undefined
  );

  if (configuredOrigin) {
    const origin = new URL(configuredOrigin);
    if (!/^https?:$/.test(origin.protocol)) {
      throw new Error("NEXT_PUBLIC_SITE_URL must use http or https");
    }
    return origin.origin;
  }

  // Local development stays usable, but every production build must provide
  // the canonical origin so metadata never falls back to localhost or VERCEL_URL.
  if (process.env.NODE_ENV === "production") {
    throw new Error("NEXT_PUBLIC_SITE_URL or VERCEL_PROJECT_PRODUCTION_URL is required for production builds");
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
      images: [{ url: SITE_SHARE_IMAGE, alt: `${SITE_NAME} study tracker` }],
    },
    twitter: {
      card: "summary",
      title: `${title} | ${SITE_NAME}`,
      description,
      images: [SITE_SHARE_IMAGE],
    },
  };
}
