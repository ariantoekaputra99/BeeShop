import { useEffect, useState } from 'react';
import type { Page, Product } from '../types';
import { ApiError, api, qs } from '../lib/api';
import { money, weight } from '../lib/format';
import { navigate } from '../lib/router';
import { productPath } from '../lib/productLink';
import { Alert, ProductImage, QtyStepper } from '../components/ui';
import { RichDescription } from '../components/RichDescription';

type Props = { productId: number; onAdd: (p: Product, qty: number) => void };

export function ProductDetailPage({ productId, onAdd }: Props) {
  const [product, setProduct] = useState<Product | null>(null);
  const [related, setRelated] = useState<Product[]>([]);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState('');
  const [qty, setQty] = useState(1);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let alive = true;
    setProduct(null); setNotFound(false); setError(''); setQty(1); setRelated([]);
    api<Product>(`/products/${productId}`)
      .then(p => {
        if (!alive) return;
        setProduct(p);
        document.title = `${p.name} · BeeShop`;
        if (p.category) {
          api<Page<Product>>(`/products${qs({ category: p.category, size: 5 })}`)
            .then(r => alive && setRelated(r.content.filter(x => x.id !== p.id).slice(0, 4)))
            .catch(() => undefined);
        }
      })
      .catch(e => { if (!alive) return; if (e instanceof ApiError && e.status === 404) setNotFound(true); else setError(e.message); });
    return () => { alive = false; document.title = 'BeeShop - Belanja lebih mudah'; };
  }, [productId]);

  const share = async () => {
    try { await navigator.clipboard.writeText(window.location.href); setCopied(true); setTimeout(() => setCopied(false), 2000); }
    catch { /* clipboard tidak tersedia */ }
  };

  if (notFound) {
    return (
      <main className="mx-auto max-w-xl px-6 py-20 text-center">
        <h1 className="text-2xl font-black">Produk tidak ditemukan</h1>
        <p className="mt-2 text-slate-500">Produk mungkin sudah tidak dijual.</p>
        <a href="#/" className="mt-6 inline-block rounded-full bg-slate-900 px-6 py-3 font-bold text-white">Kembali ke katalog</a>
      </main>
    );
  }
  if (error) return <main className="mx-auto max-w-xl px-6 py-20"><Alert>{error}</Alert></main>;
  if (!product) return <main className="mx-auto max-w-6xl px-6 py-20 text-center text-slate-500">Memuat produk...</main>;

  const p = product;
  const inStock = p.stock > 0;

  return (
    <main className="mx-auto max-w-6xl px-6 py-8">
      <nav aria-label="Breadcrumb" className="mb-6 flex flex-wrap items-center gap-2 text-sm text-slate-500">
        <button onClick={() => (window.history.length > 1 ? window.history.back() : navigate('/'))} className="font-semibold text-slate-700 hover:underline">← Kembali</button>
        <span>·</span>
        <a href="#/" className="hover:underline">Katalog</a>
        {p.category && <><span>/</span><span>{p.category}</span></>}
      </nav>

      <div className="grid gap-8 lg:grid-cols-[1.1fr_.9fr]">
        <div className="overflow-hidden rounded-3xl bg-white shadow-sm ring-1 ring-orange-100">
          <ProductImage src={p.imageUrl} alt={p.name} className="aspect-square w-full text-8xl" />
        </div>

        <div>
          <p className="text-xs font-bold uppercase tracking-wider text-orange-500">{p.category ?? 'Produk'}</p>
          <h1 className="mt-2 text-3xl font-black leading-tight md:text-4xl">{p.name}</h1>
          <p className="mt-4 text-4xl font-black text-orange-500">{money(p.price)}</p>
          <p className={`mt-2 text-sm font-semibold ${!inStock ? 'text-rose-600' : p.stock <= 5 ? 'text-amber-700' : 'text-emerald-700'}`}>
            {!inStock ? 'Stok habis' : p.stock <= 5 ? `Tinggal ${p.stock} lagi` : `Stok tersedia (${p.stock})`}
          </p>

          {inStock && (
            <div className="mt-6 flex flex-wrap items-center gap-3">
              <QtyStepper value={qty} max={p.stock} onChange={n => setQty(Math.max(1, n))} />
              <button onClick={() => onAdd(p, qty)} className="flex-1 rounded-xl border-2 border-slate-900 py-3 font-bold">Tambah ke keranjang</button>
              <button onClick={() => { onAdd(p, qty); navigate('/checkout'); }} className="w-full rounded-xl bg-orange-500 py-3 font-bold text-white sm:w-auto sm:flex-1">Beli sekarang</button>
            </div>
          )}

          <dl className="mt-6 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 rounded-2xl bg-white p-4 text-sm ring-1 ring-orange-100">
            <dt className="text-slate-500">SKU</dt><dd className="font-mono">{p.sku}</dd>
            <dt className="text-slate-500">Berat</dt><dd>{weight(p.weightGram)} <span className="text-slate-400">(untuk hitung ongkir)</span></dd>
            {p.category && <><dt className="text-slate-500">Kategori</dt><dd>{p.category}</dd></>}
          </dl>

          <button onClick={share} className="mt-3 text-sm text-slate-500 underline">{copied ? 'Link disalin ✓' : 'Salin link produk'}</button>
        </div>
      </div>

      <section className="mt-10 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100 md:p-8">
        <h2 className="mb-4 text-xl font-black">Deskripsi produk</h2>
        <RichDescription text={p.description} />
      </section>

      {related.length > 0 && (
        <section className="mt-10">
          <h2 className="mb-4 text-xl font-black">Produk lain di {p.category}</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {related.map(r => (
              <a key={r.id} href={`#${productPath(r)}`} className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-orange-100 transition hover:-translate-y-1 hover:shadow-lg">
                <ProductImage src={r.imageUrl} alt={r.name} className="h-36 w-full text-4xl" />
                <div className="p-4">
                  <p className="font-semibold leading-tight">{r.name}</p>
                  <p className="mt-1 font-black text-orange-500">{money(r.price)}</p>
                </div>
              </a>
            ))}
          </div>
        </section>
      )}
    </main>
  );
}
