import "./globals.css";
import { colors } from "./themes";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html>
      <body style={{ backgroundColor: colors.primary.DEFAULT, minHeight: "100vh" }}>{children}</body>
    </html>
  );
}
