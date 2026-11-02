const APP_ENV = import.meta.env.VITE_APP_ENV ?? 'local';

/** Penanda environment non-prod supaya tester tidak tertukar dengan toko asli. */
function EnvBanner() {
  if (APP_ENV === 'prod') return null;
  const color = APP_ENV === 'uat' ? 'bg-violet-600' : APP_ENV === 'sit' ? 'bg-sky-600' : 'bg-slate-700';
  return (
    <div className={`${color} py-1 text-center text-xs font-bold uppercase tracking-widest text-white`}>
      {APP_ENV} environment · bukan toko produksi
    </div>
  );
}

export function Header({ cartCount, onCart }: { cartCount: number; onCart: () => void }) {
  return (
    <header className="sticky top-0 z-10 border-b border-orange-100 bg-[#fffaf2]/95 backdrop-blur">
      <EnvBanner />
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-4">
        <a href="#/" className="block">
          <p className="text-xs font-bold uppercase tracking-[.35em] text-orange-500">BeeShop</p>
          <p className="text-xl font-black">Belanja lebih mudah.</p>
        </a>
        <nav className="flex items-center gap-2 text-sm font-semibold">
          <a href="#/orders" className="rounded-full px-4 py-2 hover:bg-orange-100">Lacak pesanan</a>
          <button onClick={onCart} className="rounded-full bg-slate-900 px-5 py-2 font-bold text-white">
            Keranjang{cartCount > 0 && <span className="ml-2 rounded-full bg-orange-500 px-2 py-0.5 text-xs">{cartCount}</span>}
          </button>
        </nav>
      </div>
    </header>
  );
}
