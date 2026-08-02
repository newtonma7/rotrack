import "./globals.css";

import { AuthProvider } from "../context/AuthProvider";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html>
      <body className="min-h-screen bg-[var(--rt-cream)] text-[var(--rt-ink)]">
        <AuthProvider>
          {children}
        </AuthProvider>
      </body>
    </html>
  );
}

