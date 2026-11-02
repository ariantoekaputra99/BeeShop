import { useCallback, useEffect, useState } from 'react';
import type { Order } from '../types';
import { api } from '../lib/api';
import { load } from '../lib/storage';
import { Alert, Field, inputCls } from '../components/ui';
import { OrderDetail } from '../components/OrderDetail';
import { LAST_ORDER_KEY } from './CheckoutPage';

export function TrackOrderPage({ params }: { params: URLSearchParams }) {
  const last = load<{ orderNumber?: string; email?: string }>(LAST_ORDER_KEY, {});
  // Midtrans menambahkan ?order_id=... ke finish URL
  const initialNumber = params.get('order_id') ?? params.get('order') ?? last.orderNumber ?? '';
  const [orderNumber, setOrderNumber] = useState(initialNumber);
  const [email, setEmail] = useState(initialNumber === last.orderNumber ? last.email ?? '' : '');
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const lookup = useCallback(async (n: string, mail: string) => {
    setBusy(true); setError('');
    try {
      setOrder(await api<Order>(`/orders/${encodeURIComponent(n.trim())}/lookup`, { method: 'POST', body: { email: mail.trim() } }));
    } catch (e) {
      setOrder(null); setError((e as Error).message);
    } finally { setBusy(false); }
  }, []);

  useEffect(() => { if (initialNumber && email) lookup(initialNumber, email); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const cancel = async () => {
    if (!order || !confirm('Batalkan pesanan ini?')) return;
    setBusy(true);
    try { setOrder(await api<Order>(`/orders/${order.orderNumber}/cancel`, { method: 'POST', body: { email } })); }
    catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  };

  return (
    <main className="mx-auto max-w-3xl px-6 py-10">
      <h1 className="text-3xl font-black">Lacak pesanan</h1>
      <form onSubmit={e => { e.preventDefault(); lookup(orderNumber, email); }} className="mt-6 grid gap-3 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        <Field label="Nomor pesanan"><input required className={inputCls} value={orderNumber} onChange={e => setOrderNumber(e.target.value)} placeholder="BEE-20261102-XXXXXX" /></Field>
        <Field label="Email pemesan"><input required type="email" className={inputCls} value={email} onChange={e => setEmail(e.target.value)} /></Field>
        <button disabled={busy} className="rounded-xl bg-slate-900 px-6 py-3 font-bold text-white disabled:opacity-50">{busy ? 'Mencari...' : 'Cari'}</button>
      </form>
      <div className="mt-6 space-y-4">
        {error && <Alert>{error}</Alert>}
        {order && (
          <div className="rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100">
            <OrderDetail order={order} />
            {order.status === 'PENDING_PAYMENT' && (
              <div className="mt-6 flex flex-wrap gap-3">
                {order.paymentRedirectUrl && <a href={order.paymentRedirectUrl} className="rounded-xl bg-orange-500 px-5 py-3 font-bold text-white">Lanjutkan pembayaran</a>}
                <button onClick={cancel} disabled={busy} className="rounded-xl border px-5 py-3 font-semibold">Batalkan pesanan</button>
              </div>
            )}
          </div>
        )}
      </div>
    </main>
  );
}
