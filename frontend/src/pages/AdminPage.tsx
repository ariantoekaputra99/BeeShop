import { useCallback, useEffect, useState } from 'react';
import type { AdminRole, AdminSession, AdminUser, DashboardStats, Order, OrderStatus, Page, Product } from '../types';
import { ApiError, api, qs } from '../lib/api';
import { STATUS_LABEL, dateTime, money } from '../lib/format';
import { Alert, Field, StatusBadge, inputCls } from '../components/ui';
import { OrderDetail } from '../components/OrderDetail';

const SESSION_KEY = 'beeshop.adminSession';

/** Cermin aturan transisi di backend (OrderStatus.java). PAID hanya dari payment gateway. */
const NEXT: Record<OrderStatus, OrderStatus[]> = {
  PENDING_PAYMENT: ['CANCELLED'], PAID: ['PROCESSING', 'REFUNDED'], PROCESSING: ['SHIPPED', 'REFUNDED'],
  SHIPPED: ['DELIVERED'], DELIVERED: [], CANCELLED: [], REFUNDED: [],
};

/** Token disimpan di sessionStorage: hilang saat tab ditutup, tidak ikut ke tab lain. */
function readSession(): AdminSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    const s = raw ? (JSON.parse(raw) as AdminSession) : null;
    return s && new Date(s.expiresAt) > new Date() ? s : null;
  } catch { return null; }
}
function writeSession(s: AdminSession | null) {
  try { s ? sessionStorage.setItem(SESSION_KEY, JSON.stringify(s)) : sessionStorage.removeItem(SESSION_KEY); } catch { /* abaikan */ }
}

type Tab = 'dashboard' | 'orders' | 'products' | 'users' | 'profile';
const TAB_LABEL: Record<Tab, string> = { dashboard: 'Ringkasan', orders: 'Pesanan', products: 'Produk', users: 'Akun admin', profile: 'Profil' };

export function AdminPage() {
  const [session, setSession] = useState(readSession);
  const [tab, setTab] = useState<Tab>('dashboard');

  const expire = useCallback(() => { writeSession(null); setSession(null); }, []);
  const logout = async () => {
    if (session) await api('/admin/auth/logout', { method: 'POST', token: session.token }).catch(() => undefined);
    expire();
  };

  if (!session) return <Login onLogin={s => { writeSession(s); setSession(s); setTab('dashboard'); }} />;
  const tabs: Tab[] = session.user.role === 'OWNER'
    ? ['dashboard', 'orders', 'products', 'users', 'profile'] : ['dashboard', 'orders', 'products', 'profile'];
  const props = { token: session.token, onAuthError: expire };

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-black">Admin</h1>
          <p className="text-sm text-slate-500">{session.user.displayName} (@{session.user.username}) · {session.user.role}</p>
        </div>
        <div className="flex flex-wrap gap-2 text-sm font-semibold">
          {tabs.map(t => (
            <button key={t} onClick={() => setTab(t)} className={`rounded-full px-4 py-2 ${tab === t ? 'bg-slate-900 text-white' : 'bg-white ring-1 ring-orange-100'}`}>
              {TAB_LABEL[t]}
            </button>
          ))}
          <button onClick={logout} className="rounded-full px-4 py-2 text-slate-500 hover:bg-slate-100">Keluar</button>
        </div>
      </div>
      {tab === 'dashboard' && <Dashboard {...props} />}
      {tab === 'orders' && <Orders {...props} />}
      {tab === 'products' && <Products {...props} />}
      {tab === 'users' && <Users {...props} me={session.user} />}
      {tab === 'profile' && <Profile {...props} />}
    </main>
  );
}

type TabProps = { token: string; onAuthError: () => void };

function useAdminApi({ token, onAuthError }: TabProps) {
  return useCallback(async <T,>(path: string, opts: { method?: string; body?: unknown } = {}) => {
    try { return await api<T>(path, { ...opts, token }); }
    catch (e) { if (e instanceof ApiError && e.status === 401) onAuthError(); throw e; }
  }, [token, onAuthError]);
}

function Login({ onLogin }: { onLogin: (s: AdminSession) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true); setError('');
    try { onLogin(await api<AdminSession>('/admin/auth/login', { method: 'POST', body: { username, password } })); }
    catch (err) { setError((err as Error).message); setPassword(''); }
    finally { setBusy(false); }
  };
  return (
    <main className="mx-auto max-w-sm px-6 py-20">
      <form onSubmit={submit} className="space-y-4 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100">
        <h1 className="text-2xl font-black">Masuk admin</h1>
        <Field label="Username"><input required className={inputCls} value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></Field>
        <Field label="Password"><input type="password" required className={inputCls} value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></Field>
        {error && <Alert>{error}</Alert>}
        <button disabled={busy} className="w-full rounded-xl bg-slate-900 py-3 font-bold text-white disabled:opacity-50">{busy ? 'Memeriksa...' : 'Masuk'}</button>
      </form>
    </main>
  );
}

function Dashboard(props: TabProps) {
  const call = useAdminApi(props);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [error, setError] = useState('');
  useEffect(() => { call<DashboardStats>('/admin/dashboard').then(setStats).catch(e => setError(e.message)); }, [call]);
  if (error) return <Alert>{error}</Alert>;
  if (!stats) return <p className="text-slate-500">Memuat...</p>;
  const cards = [
    { label: 'Pendapatan (dibayar)', value: money(stats.revenue) },
    { label: 'Perlu diproses', value: String(stats.ordersByStatus.PAID ?? 0) },
    { label: 'Produk aktif', value: String(stats.activeProducts) },
    { label: 'Stok menipis (≤5)', value: String(stats.lowStockProducts) },
  ];
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map(c => (
          <div key={c.label} className="rounded-3xl bg-white p-5 shadow-sm ring-1 ring-orange-100">
            <p className="text-sm text-slate-500">{c.label}</p>
            <p className="mt-2 text-2xl font-black">{c.value}</p>
          </div>
        ))}
      </div>
      <div className="rounded-3xl bg-white p-5 shadow-sm ring-1 ring-orange-100">
        <p className="mb-3 font-semibold">Pesanan per status</p>
        <div className="flex flex-wrap gap-3">
          {(Object.keys(stats.ordersByStatus) as OrderStatus[]).map(s => (
            <div key={s} className="flex items-center gap-2"><StatusBadge status={s} /><b>{stats.ordersByStatus[s]}</b></div>
          ))}
        </div>
      </div>
    </div>
  );
}

function Orders(props: TabProps) {
  const call = useAdminApi(props);
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Order> | null>(null);
  const [selected, setSelected] = useState<Order | null>(null);
  const [tracking, setTracking] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  const loadOrders = useCallback(() => {
    call<Page<Order>>(`/admin/orders${qs({ status, q, page, size: 20 })}`).then(setData).catch(e => setError(e.message));
  }, [call, status, q, page]);
  useEffect(() => { const t = setTimeout(loadOrders, 250); return () => clearTimeout(t); }, [loadOrders]);

  const update = async (next: OrderStatus) => {
    if (!selected) return;
    if ((next === 'CANCELLED' || next === 'REFUNDED') && !confirm(`Ubah ke ${STATUS_LABEL[next]}? Stok akan dikembalikan.`)) return;
    setError('');
    try {
      const o = await call<Order>(`/admin/orders/${selected.orderNumber}/status`, {
        method: 'PATCH', body: { status: next, trackingNumber: tracking || undefined, note: note || undefined },
      });
      setSelected(o); setTracking(''); setNote(''); loadOrders();
    } catch (e) { setError((e as Error).message); }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
      <div className="space-y-3">
        <div className="flex flex-wrap gap-2">
          <input value={q} onChange={e => { setQ(e.target.value); setPage(0); }} placeholder="Cari no. order / nama / email" className={`${inputCls} flex-1`} />
          <select value={status} onChange={e => { setStatus(e.target.value as OrderStatus | ''); setPage(0); }} className={`${inputCls} w-auto`}>
            <option value="">Semua status</option>
            {(Object.keys(STATUS_LABEL) as OrderStatus[]).map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </div>
        {error && <Alert>{error}</Alert>}
        <div className="divide-y overflow-hidden rounded-3xl bg-white shadow-sm ring-1 ring-orange-100">
          {data?.content.length === 0 && <p className="p-6 text-center text-slate-500">Belum ada pesanan.</p>}
          {data?.content.map(o => (
            <button key={o.orderNumber} onClick={() => { setSelected(o); setError(''); }}
              className={`flex w-full items-center justify-between gap-3 p-4 text-left text-sm hover:bg-orange-50 ${selected?.orderNumber === o.orderNumber ? 'bg-orange-50' : ''}`}>
              <div>
                <p className="font-mono font-bold">{o.orderNumber}</p>
                <p className="text-slate-500">{o.customerName} · {dateTime(o.createdAt)}</p>
              </div>
              <div className="text-right"><StatusBadge status={o.status} /><p className="mt-1 font-semibold">{money(o.total)}</p></div>
            </button>
          ))}
        </div>
        {data && data.totalPages > 1 && (
          <div className="flex justify-between text-sm">
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="disabled:opacity-40">← Sebelumnya</button>
            <span>{page + 1}/{data.totalPages}</span>
            <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)} className="disabled:opacity-40">Berikutnya →</button>
          </div>
        )}
      </div>

      <div className="h-fit rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100">
        {!selected ? <p className="text-slate-500">Pilih pesanan untuk melihat detail.</p> : (
          <>
            <OrderDetail order={selected} />
            <p className="mt-4 text-sm text-slate-600">{selected.customerEmail} · Pembayaran: {selected.paymentStatus ?? '-'} {selected.paymentType && `(${selected.paymentType})`}</p>
            {NEXT[selected.status].length > 0 && (
              <div className="mt-6 space-y-3 border-t pt-4">
                {NEXT[selected.status].includes('SHIPPED') && (
                  <Field label="Nomor resi (wajib untuk Dikirim)"><input className={inputCls} value={tracking} onChange={e => setTracking(e.target.value)} /></Field>
                )}
                <Field label="Catatan (opsional)"><input className={inputCls} value={note} onChange={e => setNote(e.target.value)} /></Field>
                <div className="flex flex-wrap gap-2">
                  {NEXT[selected.status].map(s => (
                    <button key={s} onClick={() => update(s)}
                      className={`rounded-xl px-4 py-2 font-bold ${s === 'CANCELLED' || s === 'REFUNDED' ? 'border border-rose-300 text-rose-700' : 'bg-slate-900 text-white'}`}>
                      {STATUS_LABEL[s]}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

type ProductForm = { name: string; sku: string; price: string; stock: string; weightGram: string; category: string; imageUrl: string; description: string; active: boolean };
const emptyProduct: ProductForm = { name: '', sku: '', price: '', stock: '0', weightGram: '1000', category: '', imageUrl: '', description: '', active: true };

function Products(props: TabProps) {
  const call = useAdminApi(props);
  const [q, setQ] = useState('');
  const [data, setData] = useState<Page<Product> | null>(null);
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const [form, setForm] = useState<ProductForm>(emptyProduct);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const loadProducts = useCallback(() => {
    call<Page<Product>>(`/admin/products${qs({ q, size: 100 })}`).then(setData).catch(e => setError(e.message));
  }, [call, q]);
  useEffect(() => { const t = setTimeout(loadProducts, 250); return () => clearTimeout(t); }, [loadProducts]);

  const edit = (p?: Product) => {
    setFields({}); setError(''); setInfo('');
    if (!p) { setEditing('new'); setForm(emptyProduct); return; }
    setEditing(p.id);
    setForm({ name: p.name, sku: p.sku, price: String(p.price), stock: String(p.stock), weightGram: String(p.weightGram),
      category: p.category ?? '', imageUrl: p.imageUrl ?? '', description: p.description ?? '', active: p.active });
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault(); setFields({}); setError('');
    const body = { ...form, price: Number(form.price), stock: Number(form.stock), weightGram: Number(form.weightGram) };
    try {
      if (editing === 'new') await call('/admin/products', { method: 'POST', body });
      else await call(`/admin/products/${editing}`, { method: 'PUT', body });
      setInfo('Produk disimpan.'); setEditing(null); loadProducts();
    } catch (err) { setError((err as ApiError).message); setFields((err as ApiError).fields ?? {}); }
  };

  const deactivate = async (p: Product) => {
    if (!confirm(`Nonaktifkan ${p.name}? Produk tidak tampil lagi di toko.`)) return;
    try { await call(`/admin/products/${p.id}`, { method: 'DELETE' }); loadProducts(); }
    catch (err) { setError((err as Error).message); }
  };

  const set = (k: keyof ProductForm) => (e: { target: { value: string } }) => setForm(f => ({ ...f, [k]: e.target.value }));

  return (
    <div className="space-y-4">
      <div className="flex gap-2">
        <input value={q} onChange={e => setQ(e.target.value)} placeholder="Cari nama / SKU" className={`${inputCls} flex-1`} />
        <button onClick={() => edit()} className="rounded-xl bg-orange-500 px-5 font-bold text-white">+ Produk</button>
      </div>
      {info && <Alert kind="success">{info}</Alert>}
      {error && !editing && <Alert>{error}</Alert>}

      {editing !== null && (
        <form onSubmit={save} className="grid gap-4 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100 sm:grid-cols-2">
          <h2 className="text-xl font-black sm:col-span-2">{editing === 'new' ? 'Produk baru' : 'Edit produk'}</h2>
          <Field label="Nama" error={fields.name}><input required className={inputCls} value={form.name} onChange={set('name')} /></Field>
          <Field label="SKU (sama dengan BeePos)" error={fields.sku}><input required className={inputCls} value={form.sku} onChange={set('sku')} /></Field>
          <Field label="Harga (Rp)" error={fields.price}><input required type="number" min={1} step={1} className={inputCls} value={form.price} onChange={set('price')} /></Field>
          <Field label="Stok" error={fields.stock}><input required type="number" min={0} className={inputCls} value={form.stock} onChange={set('stock')} /></Field>
          <Field label="Berat per unit (gram)" error={fields.weightGram}><input required type="number" min={1} className={inputCls} value={form.weightGram} onChange={set('weightGram')} /></Field>
          <Field label="Kategori" error={fields.category}><input className={inputCls} value={form.category} onChange={set('category')} /></Field>
          <div className="sm:col-span-2"><Field label="URL gambar" error={fields.imageUrl}><input type="url" className={inputCls} value={form.imageUrl} onChange={set('imageUrl')} /></Field></div>
          <div className="sm:col-span-2"><Field label="Deskripsi" error={fields.description}><textarea className={`${inputCls} h-24`} value={form.description} onChange={set('description')} /></Field></div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.active} onChange={e => setForm(f => ({ ...f, active: e.target.checked }))} /> Tampilkan di toko</label>
          {error && <div className="sm:col-span-2"><Alert>{error}</Alert></div>}
          <div className="flex gap-2 sm:col-span-2">
            <button className="rounded-xl bg-slate-900 px-6 py-3 font-bold text-white">Simpan</button>
            <button type="button" onClick={() => setEditing(null)} className="rounded-xl border px-6 py-3">Batal</button>
          </div>
        </form>
      )}

      <div className="overflow-x-auto rounded-3xl bg-white shadow-sm ring-1 ring-orange-100">
        <table className="w-full text-left text-sm">
          <thead className="bg-orange-50 text-xs uppercase text-slate-500">
            <tr><th className="p-3">Produk</th><th className="p-3">SKU</th><th className="p-3">Harga</th><th className="p-3">Stok</th><th className="p-3">Status</th><th className="p-3" /></tr>
          </thead>
          <tbody className="divide-y">
            {data?.content.map(p => (
              <tr key={p.id} className={p.active ? '' : 'text-slate-400'}>
                <td className="p-3 font-semibold">{p.name}<p className="text-xs font-normal text-slate-500">{p.category}</p></td>
                <td className="p-3 font-mono">{p.sku}</td>
                <td className="p-3">{money(p.price)}</td>
                <td className={`p-3 ${p.stock <= 5 ? 'font-bold text-rose-600' : ''}`}>{p.stock}</td>
                <td className="p-3">{p.active ? 'Aktif' : 'Nonaktif'}</td>
                <td className="whitespace-nowrap p-3 text-right">
                  <button onClick={() => edit(p)} className="mr-3 underline">Edit</button>
                  {p.active && <button onClick={() => deactivate(p)} className="text-rose-600 underline">Nonaktifkan</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Users(props: TabProps & { me: AdminUser }) {
  const call = useAdminApi(props);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [form, setForm] = useState({ username: '', displayName: '', password: '', role: 'STAFF' as AdminRole });
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const load = useCallback(() => { call<AdminUser[]>('/admin/users').then(setUsers).catch(e => setError(e.message)); }, [call]);
  useEffect(load, [load]);

  const create = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setFields({}); setInfo('');
    try {
      await call('/admin/users', { method: 'POST', body: form });
      setInfo(`Akun ${form.username} dibuat. Berikan password awal secara pribadi dan minta segera diganti.`);
      setForm({ username: '', displayName: '', password: '', role: 'STAFF' }); load();
    } catch (err) { setError((err as ApiError).message); setFields((err as ApiError).fields ?? {}); }
  };

  const patch = async (u: AdminUser, body: Record<string, unknown>, confirmText?: string) => {
    if (confirmText && !confirm(confirmText)) return;
    setError(''); setInfo('');
    try { await call(`/admin/users/${u.id}`, { method: 'PATCH', body }); load(); }
    catch (err) { setError((err as Error).message); }
  };

  const resetPassword = (u: AdminUser) => {
    const pw = prompt(`Password baru untuk ${u.username} (min. 10 karakter):`);
    if (pw) patch(u, { newPassword: pw }).then(() => setInfo(`Password ${u.username} direset. Sesi lamanya sudah dikeluarkan.`));
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
      <div className="space-y-3">
        {error && <Alert>{error}</Alert>}
        {info && <Alert kind="success">{info}</Alert>}
        <div className="overflow-x-auto rounded-3xl bg-white shadow-sm ring-1 ring-orange-100">
          <table className="w-full text-left text-sm">
            <thead className="bg-orange-50 text-xs uppercase text-slate-500">
              <tr><th className="p-3">Akun</th><th className="p-3">Role</th><th className="p-3">Login terakhir</th><th className="p-3" /></tr>
            </thead>
            <tbody className="divide-y">
              {users.map(u => {
                const self = u.id === props.me.id;
                const locked = u.lockedUntil && new Date(u.lockedUntil) > new Date();
                return (
                  <tr key={u.id} className={u.active ? '' : 'text-slate-400'}>
                    <td className="p-3"><b>{u.displayName}</b><p className="text-xs">@{u.username}{self && ' (Anda)'}{!u.active && ' · nonaktif'}{locked && ' · terkunci'}</p></td>
                    <td className="p-3">
                      <select disabled={self} value={u.role} onChange={e => patch(u, { role: e.target.value })} className="rounded-lg border px-2 py-1">
                        <option value="OWNER">OWNER</option><option value="STAFF">STAFF</option>
                      </select>
                    </td>
                    <td className="p-3">{dateTime(u.lastLoginAt)}</td>
                    <td className="whitespace-nowrap p-3 text-right">
                      <button onClick={() => resetPassword(u)} className="mr-3 underline">Reset password</button>
                      {!self && (u.active
                        ? <button onClick={() => patch(u, { active: false }, `Nonaktifkan ${u.username}? Sesi login-nya langsung berakhir.`)} className="text-rose-600 underline">Nonaktifkan</button>
                        : <button onClick={() => patch(u, { active: true })} className="text-emerald-700 underline">Aktifkan</button>)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      <form onSubmit={create} className="h-fit space-y-3 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100">
        <h2 className="text-lg font-black">Tambah akun</h2>
        <Field label="Username" error={fields.username}><input required className={inputCls} value={form.username} onChange={e => setForm(f => ({ ...f, username: e.target.value }))} autoComplete="off" /></Field>
        <Field label="Nama tampilan" error={fields.displayName}><input required className={inputCls} value={form.displayName} onChange={e => setForm(f => ({ ...f, displayName: e.target.value }))} /></Field>
        <Field label="Password awal (min. 10 karakter)" error={fields.password}><input required type="password" minLength={10} className={inputCls} value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))} autoComplete="new-password" /></Field>
        <Field label="Role">
          <select className={inputCls} value={form.role} onChange={e => setForm(f => ({ ...f, role: e.target.value as AdminRole }))}>
            <option value="STAFF">STAFF: produk & pesanan</option>
            <option value="OWNER">OWNER: semua, termasuk akun admin</option>
          </select>
        </Field>
        <button className="w-full rounded-xl bg-slate-900 py-3 font-bold text-white">Buat akun</button>
      </form>
    </div>
  );
}

function Profile(props: TabProps) {
  const call = useAdminApi(props);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setInfo('');
    if (next !== repeat) { setError('Konfirmasi password tidak sama.'); return; }
    try {
      await call('/admin/auth/password', { method: 'POST', body: { currentPassword: current, newPassword: next } });
      setInfo('Password diganti. Sesi di perangkat lain sudah dikeluarkan.');
      setCurrent(''); setNext(''); setRepeat('');
    } catch (err) { setError((err as ApiError).fields?.newPassword ? 'Password baru minimal 10 karakter.' : (err as Error).message); }
  };

  return (
    <form onSubmit={submit} className="max-w-md space-y-3 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-orange-100">
      <h2 className="text-lg font-black">Ganti password</h2>
      <Field label="Password saat ini"><input required type="password" className={inputCls} value={current} onChange={e => setCurrent(e.target.value)} autoComplete="current-password" /></Field>
      <Field label="Password baru (min. 10 karakter)"><input required type="password" minLength={10} className={inputCls} value={next} onChange={e => setNext(e.target.value)} autoComplete="new-password" /></Field>
      <Field label="Ulangi password baru"><input required type="password" className={inputCls} value={repeat} onChange={e => setRepeat(e.target.value)} autoComplete="new-password" /></Field>
      {error && <Alert>{error}</Alert>}
      {info && <Alert kind="success">{info}</Alert>}
      <button className="w-full rounded-xl bg-slate-900 py-3 font-bold text-white">Simpan</button>
    </form>
  );
}
