import "./globals.css";

import { AuthProvider } from "../context/AuthProvider";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Figtree:wght@300;400;500;600;700;800;900&display=swap"
        />
      </head>
      <body className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
        <AuthProvider>
          {children}
        </AuthProvider>
      </body>
    </html>
  );
}

