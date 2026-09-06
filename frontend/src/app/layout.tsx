import type { Metadata, Viewport } from 'next';
import { Providers } from './theme';
export const metadata: Metadata = { title: 'EvenBetterHelp · Team inbox', description: 'A calmer workspace for every customer conversation.', manifest: '/manifest.webmanifest', appleWebApp: { capable: true, statusBarStyle: 'default', title: 'EvenBetterHelp' }, icons: { icon: '/icon.svg', apple: '/icon.svg' } };
export const viewport: Viewport = { width: 'device-width', initialScale: 1, themeColor: '#216b52', viewportFit: 'cover' };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body><Providers>{children}</Providers></body></html>;
}
