import type { ApiErrorBody } from '../types';

export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8081/api').replace(/\/+$/, '');

export class ApiError extends Error {
  constructor(public status: number, message: string, public fields?: Record<string, string>) { super(message); }
}

type Options = { method?: string; body?: unknown; token?: string };

export async function api<T>(path: string, { method = 'GET', body, token }: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'Server tidak dapat dihubungi. Pastikan backend berjalan.');
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    const err = data as ApiErrorBody | undefined;
    throw new ApiError(res.status, err?.message ?? `Request gagal (${res.status})`, err?.fields);
  }
  return data as T;
}

export const qs = (params: Record<string, string | number | undefined>) => {
  const s = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== '') s.set(k, String(v)); });
  const out = s.toString();
  return out ? `?${out}` : '';
};
