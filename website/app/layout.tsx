import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "agent4j — Java-native agent harness",
  description:
    "A Java-native coding agent harness for streaming model calls, durable sessions, workspace tools, and inspectable agent loops.",
  openGraph: {
    title: "agent4j — Java-native agent harness",
    description:
      "Build coding agents with a runtime you can inspect: streams, sessions, tools, and queues composed as ordinary Java.",
    images: ["/og.png"],
  },
  twitter: {
    card: "summary_large_image",
    title: "agent4j — Java-native agent harness",
    description:
      "A Java-native coding agent harness for streaming model calls, durable sessions, workspace tools, and inspectable agent loops.",
    images: ["/og.png"],
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
