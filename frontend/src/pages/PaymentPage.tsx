import { useEffect, useState } from 'react';
import type { Order } from '../types';
import { api } from '../lib/api';
import { money } from '../lib/format';
import { navigate } from '../lib/router';
import { load } from '../lib/storage';
import { Alert } from '../components/ui';
import { LAST_ORDER_KEY } from './CheckoutPage';

/** Halaman pembayaran simulasi (mode mock). Di production pelanggan diarahkan ke Midtrans Snap. */
export function PaymentPage({ orderNumber }: { orderNumber: string }) {
  const last = load<{ orderNumber?: string; email?: string }>(LAST_ORDER_KEY, {});
  const email = last.orderNumber === orderNumber ? last.email : undefined;
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!email) return;
    api<Order>(`/orders/${orderNumber}/lookup`, { method: 'POST', body: { email } }).then(setOrder).catch(e => setError(e.message));
  }, [orderNumber, email]);

  const pay = async (result: 'success' | 'failed') => {
    setBusy(true); setError('');
    try {
      await api(`/payments/mock/${orderNumber}?result=${result}`, { method: 'POST' });
      navigate(`/orders?order=${orderNumber}`);
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  };

  return (
    <main className="mx-auto max-w-lg px-6 py-16">
      <div className="rounded-3xl bg-white p-8 text-center shadow-sm ring-1 ring-orange-100">
        <p className="text-xs font-bold uppercase tracking-wider text-orange-500">Pembayaran simulasi</p>
        <h1 className="mt-2 text-2xl font-black">Selesaikan pembayaran</h1>
        <p className="mt-2 font-mono text-sm">{orderNumber}</p>
        {order && <p className="mt-4 text-4xl font-black">{money(order.total)}</p>}
        <p className="mt-4 text-sm text-slate-500">Mode development: belum ada credential Midtrans. Pilih hasil pembayaran untuk menguji alur.</p>
        {error && <div className="mt-4"><Alert>{error}</Alert></div>}
        <div className="mt-6 grid gap-3">
          <button disabled={busy} onClick={() => pay('success')} className="rounded-xl bg-emerald-600 py-3 font-bold text-white disabled:opacity-50">Bayar berhasil</button>
          <button disabled={busy} onClick={() => pay('failed')} className="rounded-xl border py-3 font-semibold disabled:opacity-50">Simulasikan gagal</button>
        </div>
      </div>
    </main>
  );
}
