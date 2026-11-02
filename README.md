# BeeShop

Toko online untuk retail: katalog, keranjang, checkout, payment gateway (Midtrans Snap), ongkir, lacak pesanan, panel admin, dan sinkronisasi stok dengan BeePos-App.

## Stack
- **Frontend:** React 18 + Vite + TypeScript + Tailwind CSS
- **Backend:** Java 21 + Spring Boot 3.3 + Spring Data JPA
- **Database:** PostgreSQL 16 (H2 khusus test)
- **Payment:** abstraksi `PaymentGateway` dengan adapter Midtrans Snap dan mock untuk development
- **Shipping:** abstraksi `ShippingProvider`, saat ini adapter mock (siap ditambah RajaOngkir/Biteship)

## Fitur
| Modul | Isi |
|---|---|
| Katalog | Pencarian nama/SKU, filter kategori, urutan harga/terbaru, paginasi, detail produk |
| Keranjang | Tersimpan di browser, stepper jumlah, harga & stok disinkronkan dengan katalog terbaru |
| Checkout | Data pelanggan + alamat, ongkir dihitung dari berat produk, stok dikunci (pessimistic lock) supaya tidak oversell |
| Payment | Midtrans Snap. Notifikasi diverifikasi dengan `signature_key` SHA-512 dan nominal dicocokkan dengan total order. Idempoten. Mode mock untuk development |
| Lifecycle order | `PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → DELIVERED`, plus `CANCELLED` / `REFUNDED`. Transisi divalidasi, ada riwayat status, stok dikembalikan otomatis saat batal/refund |
| Auto-expire | Order yang tidak dibayar melewati `PAYMENT_TIMEOUT_MINUTES` dibatalkan dan stoknya dilepas |
| Lacak pesanan | Pelanggan cek status, resi, dan riwayat dengan nomor order + email. Order yang belum dibayar bisa dibatalkan |
| Admin | Ringkasan (omzet, order per status, stok menipis), kelola produk, proses order dan input resi. Dilindungi `ADMIN_API_KEY` |
| BeePos-App | `PUT` stok absolut per SKU, `GET` penjualan online yang sudah dibayar sejak waktu tertentu. Dilindungi `BEEPOS_INTEGRATION_KEY` |

## Menjalankan lokal
```bash
cp .env.example .env            # isi ADMIN_API_KEY dan BEEPOS_INTEGRATION_KEY
docker compose up -d postgres   # PostgreSQL di port 5433

cd backend && ADMIN_API_KEY=ganti-saya mvn spring-boot:run
cd frontend && npm install && npm run dev
```
- Storefront: http://localhost:5173
- Admin: http://localhost:5173/#/admin (masuk dengan `ADMIN_API_KEY`)
- API: http://localhost:8081/api · Health: http://localhost:8081/actuator/health

Atau jalankan semuanya dengan Docker: `docker compose --profile app up --build`.

Tanpa `MIDTRANS_SERVER_KEY`, checkout diarahkan ke halaman **pembayaran simulasi** untuk menguji alur. Katalog contoh diisi otomatis saat database masih kosong (`SEED_DEMO_DATA=true`).

## Test
```bash
cd backend && mvn test        # 18 integration test (checkout, stok, payment, admin, BeePos)
cd frontend && npm run build  # type-check + build
```

## API
### Publik
| Method | Endpoint | Keterangan |
|---|---|---|
| GET | `/api/products?q=&category=&sort=&page=&size=` | `sort`: `name`, `price_asc`, `price_desc`, `newest` |
| GET | `/api/products/{id}` | Detail produk aktif |
| GET | `/api/categories` | Daftar kategori |
| POST | `/api/shipping/rates` | `{destinationCity, postalCode, items:[{productId, quantity}]}` |
| POST | `/api/checkout` | Membuat order + sesi pembayaran. `shippingCode` dari hasil cek ongkir |
| POST | `/api/orders/{orderNumber}/lookup` | `{email}` → detail & riwayat order |
| POST | `/api/orders/{orderNumber}/cancel` | `{email}` → batalkan order yang belum dibayar |
| GET | `/api/payments/config` | Provider aktif + Midtrans client key |
| POST | `/api/payments/midtrans/notification` | Webhook Midtrans (Payment Notification URL) |
| POST | `/api/payments/mock/{orderNumber}?result=success\|failed` | Hanya aktif di mode mock |

### Admin (header `X-Admin-Key`)
`GET /api/admin/dashboard` · `GET|POST /api/admin/products` · `GET|PUT|DELETE /api/admin/products/{id}` · `GET /api/admin/orders?status=&q=` · `GET /api/admin/orders/{orderNumber}` · `PATCH /api/admin/orders/{orderNumber}/status` dengan body `{status, trackingNumber?, note?}`

### BeePos-App (header `X-Integration-Key`)
- `PUT /api/integrations/beepos/stock` dengan body `{items:[{sku, stock}]}` → `{updated, unknownSkus}`
- `GET /api/integrations/beepos/sales?since=2026-11-01T00:00:00Z` → penjualan online yang sudah dibayar, per SKU

## Konfigurasi production
1. Isi `ADMIN_API_KEY` dan `BEEPOS_INTEGRATION_KEY` dengan string acak panjang (`openssl rand -hex 32`). Kalau kosong, endpoint admin/integrasi selalu menolak akses (fail-closed).
2. Isi `MIDTRANS_SERVER_KEY` / `MIDTRANS_CLIENT_KEY` dan set `MIDTRANS_SANDBOX=false`. Daftarkan `https://<domain-api>/api/payments/midtrans/notification` sebagai Payment Notification URL di dashboard Midtrans.
3. Set `CORS_ORIGINS` dan `STOREFRONT_URL` ke domain storefront.
4. Set `SEED_DEMO_DATA=false`.
5. Jalankan di belakang HTTPS. Admin key dikirim lewat header.

Alur bisnis lengkap ada di [docs/business-flow.md](docs/business-flow.md).
