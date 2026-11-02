/**
 * Menampilkan deskripsi produk dari teks biasa yang diketik admin:
 * baris kosong = paragraf baru, baris diawali "- " atau "* " = poin, "1. " = daftar bernomor,
 * baris diakhiri ":" yang berdiri sendiri = subjudul. Semua dirender sebagai teks (aman dari HTML).
 */
type Block = { kind: 'p'; text: string } | { kind: 'ul'; items: string[] } | { kind: 'ol'; items: string[] } | { kind: 'h'; text: string };

export function parseDescription(text: string): Block[] {
  const blocks: Block[] = [];
  const paragraphs = text.replace(/\r\n/g, '\n').split(/\n\s*\n/);
  for (const para of paragraphs) {
    let buffer: string[] = [];
    const flush = () => { if (buffer.length) { blocks.push({ kind: 'p', text: buffer.join('\n') }); buffer = []; } };
    for (const raw of para.split('\n')) {
      const line = raw.trim();
      if (!line) continue;
      const bullet = /^[-*•]\s+(.*)$/.exec(line);
      const numbered = /^\d+[.)]\s+(.*)$/.exec(line);
      const item = bullet ?? numbered;
      if (item) {
        flush();
        const kind: 'ul' | 'ol' = bullet ? 'ul' : 'ol';
        const last = blocks[blocks.length - 1];
        if (last && (last.kind === 'ul' || last.kind === 'ol') && last.kind === kind) last.items.push(item[1]);
        else blocks.push(kind === 'ul' ? { kind: 'ul', items: [item[1]] } : { kind: 'ol', items: [item[1]] });
      } else if (line.endsWith(':') && line.length <= 60 && para.split('\n').length > 1) {
        flush();
        blocks.push({ kind: 'h', text: line.slice(0, -1) });
      } else {
        buffer.push(line);
      }
    }
    flush();
  }
  return blocks;
}

export function RichDescription({ text }: { text?: string }) {
  if (!text || !text.trim()) return <p className="text-slate-500">Belum ada deskripsi untuk produk ini.</p>;
  return (
    <div className="space-y-3 text-[15px] leading-relaxed text-slate-700">
      {parseDescription(text).map((b, i) => {
        if (b.kind === 'h') return <h3 key={i} className="pt-2 font-bold text-slate-900">{b.text}</h3>;
        if (b.kind === 'ul') return <ul key={i} className="list-disc space-y-1 pl-5">{b.items.map((t, j) => <li key={j}>{t}</li>)}</ul>;
        if (b.kind === 'ol') return <ol key={i} className="list-decimal space-y-1 pl-5">{b.items.map((t, j) => <li key={j}>{t}</li>)}</ol>;
        return <p key={i} className="whitespace-pre-line">{b.text}</p>;
      })}
    </div>
  );
}
