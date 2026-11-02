import { useState } from 'react';
import type { Product } from '../types';
import { money, weight } from '../lib/format';
import { ProductImage, QtyStepper } from './ui';

export function ProductModal({ product, onClose, onAdd }: { product: Product; onClose: () => void; onAdd: (p: Product, qty: number) => void }) {
  const [qty, setQty] = useState(1);
  return (
    <div className="fixed inset-0 z-30 overflow-y-auto bg-slate-950/50 p-4" onClick={onClose}>
      <div role="dialog" aria-label={product.name} className="mx-auto mt-10 grid max-w-3xl overflow-hidden rounded-3xl bg-white shadow-2xl md:grid-cols-2" onClick={e => e.stopPropagation()}>
        <ProductImage src={product.imageUrl} alt={product.name} className="h-72 w-full md:h-full" />
        <div className="p-6">
          <div className="flex justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-orange-500">{product.category ?? 'Produk'}</span>
            <button aria-label="Tutup" onClick={onClose}>✕</button>
          </div>
          <h2 className="mt-2 text-2xl font-black">{product.name}</h2>
          <p className="mt-2 text-3xl font-black text-orange-500">{money(product.price)}</p>
          <p className="mt-4 whitespace-pre-line text-sm text-slate-600">{product.description ?? 'Belum ada deskripsi.'}</p>
          <dl className="mt-4 grid grid-cols-2 gap-2 text-sm">
            <dt className="text-slate-500">SKU</dt><dd>{product.sku}</dd>
            <dt className="text-slate-500">Berat</dt><dd>{weight(product.weightGram)}</dd>
            <dt className="text-slate-500">Stok</dt><dd>{product.stock > 0 ? product.stock : 'Habis'}</dd>
          </dl>
          {product.stock > 0 && (
            <div className="mt-6 flex items-center gap-3">
              <QtyStepper value={qty} max={product.stock} onChange={n => setQty(Math.max(1, n))} />
              <button onClick={() => { onAdd(product, qty); onClose(); }} className="flex-1 rounded-xl bg-slate-900 py-3 font-bold text-white">
                Tambah ke keranjang
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
