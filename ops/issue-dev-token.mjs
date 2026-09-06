import { createHmac } from 'node:crypto';
const secret = process.env.JWT_SECRET;
if (!secret || secret.length < 32) throw new Error('Set JWT_SECRET from your local .env before running this development helper.');
const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
const now = Math.floor(Date.now() / 1000);
const ttl = parseInt(process.env.TOKEN_TTL_SECONDS || process.argv[2] || '1800', 10);
const payload = { sub: 'local-agent', iss: process.env.JWT_ISSUER ?? 'helpdesk-local', aud: 'helpdesk', iat: now, exp: now + ttl };
const input = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}`;
console.log(`${input}.${createHmac('sha256', secret).update(input).digest('base64url')}`);
