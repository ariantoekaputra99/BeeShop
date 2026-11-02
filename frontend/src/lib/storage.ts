/** localStorage bisa melempar error (mode privat, kuota penuh), jadi selalu dibungkus. */
export function load<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function save(key: string, value: unknown) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* abaikan */ }
}

export function remove(key: string) {
  try { localStorage.removeItem(key); } catch { /* abaikan */ }
}
