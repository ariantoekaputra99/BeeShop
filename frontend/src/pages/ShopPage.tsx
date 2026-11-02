import { useEffect, useState } from 'react';
import type { Page, Product } from '../types';
import { api, qs } from '../lib/api';
import { money } from '../lib/format';
import { Alert, ProductImage } from '../components/ui';
import { ProductModal } from '../components/ProductModal';

type Props = { onAdd: (p: Product, qty: number) => void; onCatalog: (products: Product[]) => void };

const PAGE_SIZE = 12;

export function ShopPage({ onAdd, onCatalog }: Props) {
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [category, setCategory] = useState('');
  const [sort, setSort] = useState('name');
  const [page, setPage] = useState(0);
  const [categories, setCategories] = useState<string[]>([]);
  const [data, setData] = useState<Page<Product> | null>(null);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<Product | null>(null);

  useEffect(() => { api<string[]>('/categories').then(setCategories).catch(() => setCategories([])); }, []);
  useEffect(() => { const t = setTimeout(() => setDebounced(query), 300); return () => clearTimeout(t); }, [query]);
  useEffect(() => setPage(0), [debounced, category, sort]);
  useEffect(() => {
    let alive = true;
    setError('');
    api<Page<Product>>(`/products${qs({ q: debounced, category, sort, page, size: PAGE_SIZE })}`)
      .then(d => { if (alive) { setData(d); onCatalog(d.content); } })
      .catch(e => alive && setError(e.message));
    return () => { alive = false; };
  }, [debounced, category, sort, page, onCatalog]);

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <section className="grid gap-6 rounded-[2rem] bg-gradient-to-br from-orange-400 to-amber-300 p-8 md:grid-cols-[1.2fr_.8fr] md:p-12">
        <div>
          <p className="text-sm font-bold uppercase tracking-wider text-orange-950">Kurasi pilihan hari ini</p>
          <h1 className="mt-4 max-w-xl text-4xl font-black leading-tight text-slate-950 md:text-5xl">Kebutuhan harian, sampai ke depan pintu.</h1>
          <p className="mt-5 max-w-lg text-slate-800">Produk pilihan dengan checkout praktis, pilihan kurir, dan pembayaran aman.</p>
          <button onClick={() => document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' })} className="mt-7 rounded-full bg-slate-950 px-6 py-3 font-bold text-white">Jelajahi katalog</button>
        </div>
        <div className="flex items-end justify-end">
          <div className="rounded-3xl bg-white/60 p-8 text-right shadow-xl">
            <p className="text-sm text-slate-600">Ongkir dalam kota mulai</p>
            <p className="mt-2 text-4xl font-black text-slate-950">Rp 9K</p>
            <p className="mt-1 text-sm text-slate-600">per kg</p>
          </div>
        </div>
      </section>

      <section id="catalog" className="py-12">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-bold uppercase tracking-wider text-orange-500">Shop collection</p>
            <h2 className="mt-1 text-3xl font-black">Katalog produk</h2>
          </div>
          <div className="flex flex-wrap gap-2">
            <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Cari nama atau SKU..." aria-label="Cari produk"
              className="rounded-full border border-orange-100 bg-white px-4 py-2 text-sm outline-none focus:border-orange-400" />
            <select value={sort} onChange={e => setSort(e.target.value)} aria-label="Urutkan" className="rounded-full border border-orange-100 bg-white px-4 py-2 text-sm">
              <option value="name">Nama A-Z</option>
              <option value="price_asc">Harga termurah</option>
              <option value="price_desc">Harga termahal</option>
              <option value="newest">Terbaru</option>
            </select>
          </div>
        </div>

        <div className="mb-6 flex flex-wrap gap-2">
          {['', ...categories].map(c => (
            <button key={c || 'all'} onClick={() => setCategory(c)}
              className={`rounded-full px-4 py-1.5 text-sm font-semibold ${category === c ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 ring-1 ring-orange-100 hover:bg-orange-50'}`}>
              {c || 'Semua'}
            </button>
          ))}
        </div>

        {error && <Alert>{error}</Alert>}
        {data && data.content.length === 0 && <p className="py-16 text-center text-slate-500">Produk tidak ditemukan.</p>}

        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {data?.content.map(p => (
            <article key={p.id} className="overflow-hidden rounded-3xl border border-orange-100 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-xl">
              <button className="block w-full text-left" onClick={() => setSelected(p)}>
                <ProductImage src={p.imageUrl} alt={p.name} className="h-48 w-full" />
              </button>
              <div className="p-5">
                <div className="flex justify-between text-xs text-slate-500">
                  <span>{p.category ?? 'Produk'}</span>
                  <span className={p.stock <= 5 ? 'font-semibold text-rose-600' : ''}>{p.stock > 0 ? `${p.stock} tersedia` : 'Stok habis'}</span>
                </div>
                <button onClick={() => setSelected(p)} className="mt-3 text-left text-xl font-bold hover:underline">{p.name}</button>
                <p className="mt-2 text-2xl font-black text-orange-500">{money(p.price)}</p>
                <button disabled={p.stock <= 0} onClick={() => onAdd(p, 1)} className="mt-5 w-full rounded-2xl bg-slate-900 py-3 font-bold text-white disabled:opacity-40">
                  {p.stock > 0 ? 'Tambah ke keranjang' : 'Stok habis'}
                </button>
              </div>
            </article>
          ))}
        </div>

        {data && data.totalPages > 1 && (
          <div className="mt-8 flex items-center justify-center gap-3 text-sm">
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="rounded-full border px-4 py-2 disabled:opacity-40">Sebelumnya</button>
            <span>Halaman {page + 1} dari {data.totalPages}</span>
            <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)} className="rounded-full border px-4 py-2 disabled:opacity-40">Berikutnya</button>
          </div>
        )}
      </section>
      {selected && <ProductModal product={selected} onClose={() => setSelected(null)} onAdd={onAdd} />}
    </main>
  );
}
