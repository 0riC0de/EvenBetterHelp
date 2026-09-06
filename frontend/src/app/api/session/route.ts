import { cookies } from 'next/headers';
import { NextRequest, NextResponse } from 'next/server';
export async function POST(request: NextRequest) {
  if (request.headers.get('origin') !== request.nextUrl.origin) return NextResponse.json({}, { status: 403 });
  const input = await request.json().catch(() => null);
  if (typeof input?.token !== 'string' || input.token.length > 8192) return NextResponse.json({}, { status: 400 });
  if (!process.env.BACKEND_URL) return NextResponse.json({}, { status: 503 });
  try {
    const check = await fetch(`${process.env.BACKEND_URL}/me`, { headers: { Authorization: `Bearer ${input.token}` }, cache: 'no-store', signal: AbortSignal.timeout(5000) });
    if (!check.ok) return NextResponse.json({}, { status: 401 });
    (await cookies()).set('helpdesk_session', input.token, { httpOnly: true, secure: process.env.COOKIE_SECURE !== 'false', sameSite: 'strict', path: '/', maxAge: 1800 });
    return NextResponse.json({ ok: true });
  } catch { return NextResponse.json({}, { status: 503 }); }
}
export async function DELETE(request: NextRequest) {
  if (request.headers.get('origin') !== request.nextUrl.origin) return NextResponse.json({}, { status: 403 });
  (await cookies()).delete('helpdesk_session');
  return NextResponse.json({ ok: true });
}
