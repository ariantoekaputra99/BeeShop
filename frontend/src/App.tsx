import { useCallback, useState } from 'react';
import type { Product } from './types';
import { useCart } from './lib/cart';
import { useRoute } from './lib/router';
import { Header } from './components/Header';
import { CartDrawer } from './components/CartDrawer';
import { ShopPage } from './pages/ShopPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { PaymentPage } from './pages/PaymentPage';
import { TrackOrderPage } from './pages/TrackOrderPage';
import { AdminPage } from './pages/AdminPage';

export default function App() {
  const route = useRoute();
  const cart = useCart();
  const [cartOpen, setCartOpen] = useState(false);
  const [toast, setToast] = useState('');

  const add = useCallback((p: Product, qty: number) => {
    cart.add(p, qty);
    setToast(`${p.name} ditambahkan ke keranjang`);
    window.setTimeout(() => setToast(''), 2200);
  }, [cart]);

  let page;
  if (route.path === '/checkout') page = <CheckoutPage cart={cart} />;
  else if (route.path.startsWith('/payment/')) page = <PaymentPage orderNumber={decodeURIComponent(route.path.slice('/payment/'.length))} />;
  else if (route.path === '/orders') page = <TrackOrderPage params={route.params} />;
  else if (route.path === '/admin') page = <AdminPage />;
  else page = <ShopPage onAdd={add} onCatalog={cart.refresh} />;

  return (
    <div className="min-h-screen bg-[#fffaf2] text-slate-900">
      <Header cartCount={cart.count} onCart={() => setCartOpen(true)} />
      {page}
      <footer className="border-t border-orange-100 py-8 text-center text-sm text-slate-500">
        © {new Date().getFullYear()} BeeShop · <a href="#/orders" className="underline">Lacak pesanan</a> · <a href="#/admin" className="underline">Admin</a>
      </footer>
      <CartDrawer cart={cart} open={cartOpen} onClose={() => setCartOpen(false)} />
      {toast && <div role="status" className="fixed bottom-6 left-1/2 z-40 w-[min(90%,420px)] -translate-x-1/2 rounded-2xl bg-slate-950 p-4 text-center text-sm text-white shadow-2xl">{toast}</div>}
    </div>
  );
}
