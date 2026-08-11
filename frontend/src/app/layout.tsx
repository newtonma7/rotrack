import type { Metadata } from "next";
import "./globals.css";

import { AuthProvider } from "../context/AuthProvider";
import { SITE_DESCRIPTION, SITE_ICON, SITE_NAME, SITE_ORIGIN, SITE_URL } from "@/lib/site";

export const metadata: Metadata = {
  metadataBase: SITE_URL,
  title: {
    default: `${SITE_NAME} — track your rot.`,
    template: `%s | ${SITE_NAME}`,
  },
  description: SITE_DESCRIPTION,
  alternates: { canonical: SITE_ORIGIN },
  icons: { icon: SITE_ICON },
  openGraph: {
    type: "website",
    url: SITE_ORIGIN,
    title: `${SITE_NAME} — track your rot.`,
    description: SITE_DESCRIPTION,
    siteName: SITE_NAME,
    images: [{ url: SITE_ICON, alt: SITE_NAME }],
  },
  twitter: {
    card: "summary",
    title: `${SITE_NAME} — track your rot.`,
    description: SITE_DESCRIPTION,
    images: [SITE_ICON],
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
        <AuthProvider>
          {children}
        </AuthProvider>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify({
              "@context": "https://schema.org",
              "@type": "WebSite",
              name: SITE_NAME,
              url: SITE_ORIGIN,
              description: SITE_DESCRIPTION,
            }),
          }}
        />
      </body>
    </html>
  );
}

