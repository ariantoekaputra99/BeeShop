import { useState } from 'react';
import type { CheckoutResponse, ShippingQuote } from '../types';
import type { Cart } from '../lib/cart';
import { ApiError, api } from '../lib/api';
import { money, weight } from '../lib/format';
import { navigate } from '../lib/router';
import { save } from '../lib/storage';
import { Alert, Field, QtyStepper, inputCls } from '../components/ui';

export const LAST_ORDER_KEY = 'beeshop.lastOrder';

type Form = { customerName: string; customerEmail: string; customerPhone: string; address: string; city: string; postalCode: string; notes: string };
const empty: Form = { customerName: '', customerEmail: '', customerPhone: '', address: '', city: '', postalCode: '', notes: '' };

export function CheckoutPage({ cart }: { cart: Cart }) {
  const [form, setForm] = useState<Form>(empty);
  const [quote, setQuote] = useState<ShippingQuote | null>(null);
  const [shippingCode, setShippingCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});

  const items = cart.lines.map(l => ({ productId: l.productId, quantity: l.quantity }));
  const fee = quote?.options.find(o => o.code === shippingCode)?.fee ?? 0;
  const set = (k: keyof Form) => (e: { target: { value: string } }) => {
    setForm(f => ({ ...f, [k]: e.target.value }));
    if (k === 'city' || k === 'postalCode') { setQuote(null); setShippingCode(''); }
  };

  if (!cart.lines.length) {
    return (
      <main className="mx-auto max-w-xl px-6 py-20 text-center">
        <h1 className="text-2xl font-black">Keranjang kosong</h1>
        <a href="#/" className="mt-6 inline-block rounded-full bg-slate-900 px-6 py-3 font-bold text-white">Kembali belanja</a>
      </main>
    );
  }

  const fetchRates = async () => {
    setError('');
    if (!form.city.trim() || !/^\d{5}$/.test(form.postalCode)) { setError('Isi kota dan kode pos (5 digit) untuk cek ongkir.'); return; }
    setBusy(true);
    try {
      const q = await api<ShippingQuote>('/shipping/rates', { method: 'POST', body: { destinationCity: form.city, postalCode: form.postalCode, items } });
      setQuote(q);
      setShippingCode(q.options[0]?.code ?? '');
    } catch (e) {
      setError((e as Error).message);
    } finally { setBusy(false); }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setFields({});
    if (!shippingCode) { setError('Pilih layanan pengiriman terlebih dahulu.'); return; }
    setBusy(true);
    try {
      const res = await api<CheckoutResponse>('/checkout', { method: 'POST', body: { ...form, notes: form.notes || undefined, shippingCode, items } });
      save(LAST_ORDER_KEY, { orderNumber: res.orderNumber, email: form.customerEmail });
      cart.clear();
      if (res.paymentProvider === 'mock') navigate(`/payment/${res.orderNumber}`);
      else window.location.href = res.redirectUrl;
    } catch (e) {
      const err = e as ApiError;
      setError(err.message);
      if (err.fields) setFields(err.fields);
    } finally { setBusy(false); }
  };

  return (
    <main className="mx-auto grid max-w-6xl gap-8 px-6 py-10 lg:grid-cols-[1.3fr_.7fr]">
      <form onSubmit={submit} className="space-y-4 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100">
        <h1 className="text-2xl font-black">Checkout</h1>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Nama lengkap" error={fields.customerName}><input required className={inputCls} value={form.customerName} onChange={set('customerName')} autoComplete="name" /></Field>
          <Field label="No. HP" error={fields.customerPhone}><input required className={inputCls} value={form.customerPhone} onChange={set('customerPhone')} placeholder="08xxxxxxxxxx" inputMode="tel" autoComplete="tel" /></Field>
        </div>
        <Field label="Email" error={fields.customerEmail}><input required type="email" className={inputCls} value={form.customerEmail} onChange={set('customerEmail')} autoComplete="email" /></Field>
        <Field label="Alamat lengkap" error={fields.address}><textarea required className={`${inputCls} h-24`} value={form.address} onChange={set('address')} autoComplete="street-address" /></Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Kota / Kabupaten" error={fields.city}><input required className={inputCls} value={form.city} onChange={set('city')} autoComplete="address-level2" /></Field>
          <Field label="Kode pos" error={fields.postalCode}><input required className={inputCls} value={form.postalCode} onChange={set('postalCode')} inputMode="numeric" maxLength={5} autoComplete="postal-code" /></Field>
        </div>
        <Field label="Catatan untuk penjual (opsional)"><input className={inputCls} value={form.notes} onChange={set('notes')} maxLength={500} /></Field>

        <div>
          <button type="button" onClick={fetchRates} disabled={busy} className="w-full rounded-xl border border-orange-300 p-3 font-semibold hover:bg-orange-50 disabled:opacity-50">
            {busy && !quote ? 'Mengambil ongkir...' : quote ? 'Hitung ulang ongkir' : 'Cek ongkir'}
          </button>
          {quote && (
            <fieldset className="mt-3 space-y-2">
              <legend className="mb-1 text-sm text-slate-500">Berat total {weight(quote.totalWeightGram)}</legend>
              {quote.options.map(o => (
                <label key={o.code} className={`flex cursor-pointer items-center justify-between rounded-xl border p-3 ${shippingCode === o.code ? 'border-orange-400 bg-orange-50' : ''}`}>
                  <span className="flex items-center gap-2">
                    <input type="radio" name="shipping" checked={shippingCode === o.code} onChange={() => setShippingCode(o.code)} />
                    <span><b>{o.courier} {o.service}</b> <small className="text-slate-500">({o.etd})</small></span>
                  </span>
                  <b>{money(o.fee)}</b>
                </label>
              ))}
            </fieldset>
          )}
        </div>

        {error && <Alert>{error}</Alert>}
        <button disabled={busy || !shippingCode} className="w-full rounded-xl bg-orange-500 py-3 font-bold text-white disabled:opacity-40">
          {busy && quote ? 'Memproses...' : `Buat pesanan & bayar ${money(cart.subtotal + fee)}`}
        </button>
      </form>

      <aside className="h-fit rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100">
        <h2 className="text-lg font-black">Ringkasan</h2>
        <ul className="mt-4 space-y-3">
          {cart.lines.map(l => (
            <li key={l.productId} className="flex items-center justify-between gap-3 text-sm">
              <div>
                <p className="font-semibold">{l.name}</p>
                <QtyStepper value={l.quantity} max={l.stock} onChange={n => { cart.setQty(l.productId, n); setQuote(null); setShippingCode(''); }} />
              </div>
              <span>{money(l.price * l.quantity)}</span>
            </li>
          ))}
        </ul>
        <dl className="mt-5 space-y-1 border-t pt-4 text-sm">
          <div className="flex justify-between"><dt>Subtotal</dt><dd>{money(cart.subtotal)}</dd></div>
          <div className="flex justify-between"><dt>Ongkir</dt><dd>{shippingCode ? money(fee) : '-'}</dd></div>
          <div className="flex justify-between pt-2 text-lg font-black"><dt>Total</dt><dd>{money(cart.subtotal + fee)}</dd></div>
        </dl>
      </aside>
    </main>
  );
}
