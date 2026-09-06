import { cookies } from 'next/headers';
import { NextRequest, NextResponse } from 'next/server';
const permitted = /^(me|capabilities|ice-servers|queues|conversations|media)(\/[a-zA-Z0-9-]+)*$/;
function invalidOrigin(request: NextRequest) { return request.method !== 'GET' && request.headers.get('origin') !== request.nextUrl.origin; }
function tooLarge(body: string | undefined) { return body !== undefined && body.length > 65536; }
async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const route = path.join('/');
  if (!permitted.test(route)) return NextResponse.json({ message: 'Not found' }, { status: 404 });
  if (invalidOrigin(request)) return NextResponse.json({ message: 'Invalid origin' }, { status: 403 });
  const token = (await cookies()).get('helpdesk_session')?.value;
  if (!token) return NextResponse.json({ message: 'Please sign in' }, { status: 401 });
  const base = process.env.BACKEND_URL;
  if (!base) return NextResponse.json({ message: 'Backend is not configured' }, { status: 503 });
  const body = request.method === 'GET' ? undefined : await request.text();
  if (tooLarge(body)) return NextResponse.json({ message: 'Request is too large' }, { status: 413 });
  try {
    const result = await fetch(`${base}/${route}${request.nextUrl.search}`, { method: request.method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body, cache: 'no-store', signal: AbortSignal.timeout(25000) });
    return new NextResponse(await result.text(), { status: result.status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } });
  } catch { return NextResponse.json({ message: 'The helpdesk service is unavailable. Please try again.' }, { status: 503 }); }
}
export const GET = proxy;
export const POST = proxy;
export const PATCH = proxy;
