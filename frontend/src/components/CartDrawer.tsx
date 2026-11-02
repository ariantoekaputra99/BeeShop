import type { Cart } from '../lib/cart';
import { money } from '../lib/format';
import { navigate } from '../lib/router';
import { productPath } from '../lib/productLink';
import { ProductImage, QtyStepper } from './ui';

export function CartDrawer({ cart, open, onClose }: { cart: Cart; open: boolean; onClose: () => void }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-30 flex justify-end bg-slate-950/50" onClick={onClose}>
      <aside role="dialog" aria-label="Keranjang" className="flex h-full w-full max-w-md flex-col bg-white shadow-2xl" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between border-b p-5">
          <h2 className="text-xl font-black">Keranjang ({cart.count})</h2>
          <button aria-label="Tutup" onClick={onClose} className="text-xl">✕</button>
        </div>
        <div className="flex-1 space-y-4 overflow-y-auto p-5">
          {cart.lines.length === 0 && <p className="py-16 text-center text-slate-500">Keranjang masih kosong.</p>}
          {cart.lines.map(l => (
            <div key={l.productId} className="flex gap-4">
              <ProductImage src={l.imageUrl} alt={l.name} className="h-16 w-16 shrink-0 rounded-xl text-2xl" />
              <div className="flex-1">
                <a href={`#${productPath({ id: l.productId, name: l.name })}`} onClick={onClose} className="font-semibold leading-tight hover:underline">{l.name}</a>
                <p className="text-sm text-orange-600">{money(l.price)}</p>
                <div className="mt-2 flex items-center justify-between">
                  <QtyStepper value={l.quantity} max={l.stock} onChange={n => cart.setQty(l.productId, n)} />
                  <button onClick={() => cart.setQty(l.productId, 0)} className="text-xs text-slate-500 underline">Hapus</button>
                </div>
              </div>
            </div>
          ))}
        </div>
        <div className="border-t p-5">
          <div className="flex justify-between text-lg font-black"><span>Subtotal</span><span>{money(cart.subtotal)}</span></div>
          <p className="mt-1 text-xs text-slate-500">Ongkir dihitung saat checkout.</p>
          <button disabled={!cart.lines.length} onClick={() => { onClose(); navigate('/checkout'); }}
            className="mt-4 w-full rounded-xl bg-orange-500 py-3 font-bold text-white disabled:opacity-40">Checkout</button>
        </div>
      </aside>
    </div>
  );
}
