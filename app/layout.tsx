import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Lovely | Offline Dating",
  description: "A private, data-driven dating experience that works entirely offline.",
  applicationName: "Lovely",
};

export const viewport: Viewport = { themeColor: "#f8f6fa", width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
