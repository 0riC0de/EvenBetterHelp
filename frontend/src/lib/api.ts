import type { SendInput } from './types';
export class ApiFailure extends Error {
  constructor(public status: number, message: string) { super(message); }
}
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/backend${path}`, { ...init, cache: 'no-store', headers: { 'Content-Type': 'application/json', ...init.headers } });
  const body = await response.json().catch(() => ({ message: 'The server could not be reached.' }));
  if (!response.ok) throw new ApiFailure(response.status, body.message ?? 'Request failed. Please try again.');
  return body as T;
}
export function sendRequest(conversation: string, input: SendInput) {
  return request(`/conversations/${conversation}/messages`, { method: 'POST', body: JSON.stringify(input) });
}
export function errorMessage(error: unknown): string { return error instanceof Error ? error.message : 'Something went wrong. Please try again.'; }
export async function uploadFile(conversation: string, file: Blob, fileName: string) {
  const contentType = file.type.split(';')[0];
  const grant = await request<{ mediaId: string; url: string; headers: Record<string, string> }>(`/conversations/${conversation}/uploads`, {
    method: 'POST', body: JSON.stringify({ fileName, contentType, bytes: file.size }),
  });
  const result = await fetch(grant.url, { method: 'PUT', headers: grant.headers, body: file });
  if (!result.ok) throw new Error('The attachment could not be uploaded. Please try again.');
  return grant.mediaId;
}
