import { useCallback, useEffect, useMemo, useState } from 'react';
import type { CartLine, Product } from '../types';
import { load, save } from './storage';

const KEY = 'beeshop.cart.v1';

export function useCart() {
  const [lines, setLines] = useState<CartLine[]>(() => load<CartLine[]>(KEY, []));
  useEffect(() => save(KEY, lines), [lines]);

  const add = useCallback((p: Product, qty = 1) => setLines(cur => {
    const found = cur.find(l => l.productId === p.id);
    if (found) return cur.map(l => l.productId === p.id ? { ...l, stock: p.stock, quantity: Math.min(l.quantity + qty, p.stock) } : l);
    return [...cur, { productId: p.id, name: p.name, price: p.price, stock: p.stock, imageUrl: p.imageUrl, quantity: Math.min(qty, p.stock) }];
  }), []);

  const setQty = useCallback((productId: number, qty: number) => setLines(cur =>
    qty <= 0 ? cur.filter(l => l.productId !== productId)
      : cur.map(l => l.productId === productId ? { ...l, quantity: Math.min(qty, l.stock) } : l)), []);

  const clear = useCallback(() => setLines([]), []);

  /** Samakan harga & stok di keranjang dengan katalog terbaru. */
  const refresh = useCallback((products: Product[]) => setLines(cur => cur.flatMap(l => {
    const p = products.find(x => x.id === l.productId);
    if (!p) return [l];
    if (p.stock <= 0) return [];
    return [{ ...l, name: p.name, price: p.price, stock: p.stock, quantity: Math.min(l.quantity, p.stock) }];
  })), []);

  const count = useMemo(() => lines.reduce((s, l) => s + l.quantity, 0), [lines]);
  const subtotal = useMemo(() => lines.reduce((s, l) => s + l.price * l.quantity, 0), [lines]);

  return { lines, add, setQty, clear, refresh, count, subtotal };
}

export type Cart = ReturnType<typeof useCart>;
