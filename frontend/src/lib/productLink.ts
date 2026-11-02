/** URL detail produk yang mudah dibaca dan dibagikan, mis. #/produk/12-kopi-arabika-gayo-250g */
export const slugify = (s: string) =>
  s.toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 60);

export const productPath = (p: { id: number; name: string }) => `/produk/${p.id}-${slugify(p.name)}`;

/** Ambil id dari path /produk/<id>-<slug>; null bila tidak valid. */
export const productIdFromPath = (path: string): number | null => {
  const m = /^\/produk\/(\d+)(?:-[a-z0-9-]*)?$/.exec(path);
  return m ? Number(m[1]) : null;
};
