import type { Order } from '../types';
import { STATUS_LABEL, dateTime, money } from '../lib/format';
import { StatusBadge } from './ui';

export function OrderDetail({ order }: { order: Order }) {
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-500">Nomor pesanan</p>
          <p className="font-mono text-lg font-bold">{order.orderNumber}</p>
        </div>
        <StatusBadge status={order.status} />
      </div>

      {order.trackingNumber && (
        <div className="rounded-2xl bg-violet-50 p-4 text-sm">
          <b>{order.courier} {order.courierService}</b> · Resi <span className="font-mono font-bold">{order.trackingNumber}</span>
        </div>
      )}

      <div className="grid gap-4 text-sm sm:grid-cols-2">
        <div>
          <p className="font-semibold">Dikirim ke</p>
          <p>{order.customerName} · {order.customerPhone}</p>
          <p className="text-slate-600">{order.address}, {order.city} {order.postalCode}</p>
          {order.notes && <p className="mt-1 italic text-slate-500">“{order.notes}”</p>}
        </div>
        <div>
          <p className="font-semibold">Pengiriman</p>
          <p>{order.courier} {order.courierService} ({order.shippingEtd})</p>
          <p className="text-slate-600">Dibuat {dateTime(order.createdAt)}</p>
          {order.status === 'PENDING_PAYMENT' && <p className="text-amber-700">Bayar sebelum {dateTime(order.expiresAt)}</p>}
        </div>
      </div>

      <table className="w-full text-sm">
        <tbody>
          {order.items.map(i => (
            <tr key={i.sku} className="border-b last:border-0">
              <td className="py-2">{i.productName} <span className="text-slate-500">× {i.quantity}</span></td>
              <td className="py-2 text-right">{money(i.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr><td className="pt-3">Subtotal</td><td className="pt-3 text-right">{money(order.subtotal)}</td></tr>
          <tr><td>Ongkir</td><td className="text-right">{money(order.shippingFee)}</td></tr>
          <tr className="text-base font-black"><td className="pt-1">Total</td><td className="pt-1 text-right">{money(order.total)}</td></tr>
        </tfoot>
      </table>

      <div>
        <p className="mb-2 text-sm font-semibold">Riwayat</p>
        <ol className="space-y-2 border-l-2 border-orange-200 pl-4 text-sm">
          {order.history.map((h, i) => (
            <li key={i}>
              <b>{STATUS_LABEL[h.status]}</b> <span className="text-slate-500">· {dateTime(h.at)}{h.actor && ` · ${h.actor}`}</span>
              {h.note && <p className="text-slate-600">{h.note}</p>}
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
