import { useRef, useState } from 'react';
import { ApiError, uploadImage } from '../lib/api';
import { Alert, ProductImage, inputCls } from './ui';

const ACCEPT = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 5 * 1024 * 1024;

type Props = { value: string; onChange: (url: string) => void; token: string; onAuthError: () => void; error?: string };

/** Upload gambar produk: klik atau seret file, preview, ganti/hapus, atau pakai URL https. */
export function ImageUploader({ value, onChange, token, onAuthError, error }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [dragging, setDragging] = useState(false);
  const [useUrl, setUseUrl] = useState(value.startsWith('http'));

  const handle = async (file?: File) => {
    if (!file) return;
    setMessage('');
    if (!ACCEPT.includes(file.type)) { setMessage('Format harus JPG, PNG, atau WebP.'); return; }
    if (file.size > MAX_BYTES) { setMessage('Ukuran maksimal 5 MB.'); return; }
    setBusy(true);
    try {
      const uploaded = await uploadImage(file, token);
      onChange(uploaded.url);
      setUseUrl(false);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onAuthError();
      setMessage((e as Error).message);
    } finally {
      setBusy(false);
      if (input.current) input.current.value = '';
    }
  };

  return (
    <div className="space-y-2">
      <span className="block text-sm font-semibold text-slate-700">Gambar produk</span>
      <div className="flex flex-wrap items-start gap-4">
        <div
          role="button" tabIndex={0} aria-label="Pilih gambar produk"
          onClick={() => !busy && input.current?.click()}
          onKeyDown={e => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
          onDragOver={e => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={e => { e.preventDefault(); setDragging(false); handle(e.dataTransfer.files?.[0]); }}
          className={`relative flex h-40 w-40 shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed text-center text-xs
            ${dragging ? 'border-orange-500 bg-orange-50' : 'border-slate-300 bg-slate-50 hover:border-orange-400'}`}>
          {value
            ? <ProductImage src={value} alt="Preview gambar produk" className="h-full w-full" />
            : <span className="px-3 text-slate-500">Klik atau seret gambar ke sini<br />JPG, PNG, WebP · maks 5 MB</span>}
          {busy && <span className="absolute inset-0 flex items-center justify-center bg-white/80 font-semibold">Mengunggah...</span>}
        </div>
        <div className="flex-1 space-y-2 text-sm">
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={busy} onClick={() => input.current?.click()} className="rounded-xl border px-4 py-2 font-semibold hover:bg-slate-50 disabled:opacity-50">
              {value ? 'Ganti gambar' : 'Pilih gambar'}
            </button>
            {value && <button type="button" disabled={busy} onClick={() => onChange('')} className="rounded-xl border border-rose-200 px-4 py-2 text-rose-700">Hapus</button>}
            <button type="button" onClick={() => setUseUrl(u => !u)} className="px-2 py-2 text-slate-500 underline">
              {useUrl ? 'Sembunyikan URL' : 'Pakai URL'}
            </button>
          </div>
          {useUrl && (
            <input type="url" placeholder="https://..." className={inputCls}
              value={value.startsWith('http') ? value : ''} onChange={e => onChange(e.target.value.trim())} />
          )}
          <p className="text-xs text-slate-500">Gambar otomatis diputar sesuai orientasi foto, diperkecil ke maks 1600 px, dan metadata (termasuk lokasi GPS) dihapus.</p>
          {(message || error) && <Alert>{message || error}</Alert>}
        </div>
      </div>
      <input ref={input} type="file" accept={ACCEPT.join(',')} className="hidden" onChange={e => handle(e.target.files?.[0])} />
    </div>
  );
}
