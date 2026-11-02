import { useState, type ReactNode } from 'react';
import type { OrderStatus } from '../types';
import { STATUS_COLOR, STATUS_LABEL } from '../lib/format';
import { imageSrc } from '../lib/api';

export const inputCls = 'w-full rounded-xl border border-slate-200 bg-white p-3 text-sm outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100';

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-semibold text-slate-700">{label}</span>
      {children}
      {error && <span className="mt-1 block text-xs text-rose-600">{error}</span>}
    </label>
  );
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`inline-block rounded-full px-3 py-1 text-xs font-bold ${STATUS_COLOR[status]}`}>{STATUS_LABEL[status]}</span>;
}

export function ProductImage({ src, alt, className = '' }: { src?: string; alt: string; className?: string }) {
  const [broken, setBroken] = useState(false);
  return src && !broken
    ? <img src={imageSrc(src)} alt={alt} loading="lazy" onError={() => setBroken(true)} className={`object-cover ${className}`} />
    : <div aria-hidden className={`flex items-center justify-center bg-gradient-to-br from-orange-100 to-amber-50 text-5xl ${className}`}>🛍️</div>;
}

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'info' | 'success'; children: ReactNode }) {
  const cls = { error: 'bg-rose-50 text-rose-800 border-rose-200', info: 'bg-sky-50 text-sky-800 border-sky-200', success: 'bg-emerald-50 text-emerald-800 border-emerald-200' }[kind];
  return <div role={kind === 'error' ? 'alert' : 'status'} className={`rounded-xl border p-3 text-sm ${cls}`}>{children}</div>;
}

export function QtyStepper({ value, max, onChange }: { value: number; max: number; onChange: (n: number) => void }) {
  return (
    <div className="inline-flex items-center rounded-full border border-slate-200">
      <button type="button" aria-label="Kurangi" onClick={() => onChange(value - 1)} className="h-8 w-8 rounded-full font-bold hover:bg-slate-100">−</button>
      <span className="w-8 text-center text-sm font-semibold">{value}</span>
      <button type="button" aria-label="Tambah" disabled={value >= max} onClick={() => onChange(value + 1)} className="h-8 w-8 rounded-full font-bold hover:bg-slate-100 disabled:opacity-30">+</button>
    </div>
  );
}
