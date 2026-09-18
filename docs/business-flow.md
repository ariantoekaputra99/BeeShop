# BeeShop Business Flow

1. Customer melihat produk aktif dari `GET /api/products`.
2. Customer meminta tarif dari `POST /api/shipping/rates`.
3. Customer checkout ke `POST /api/checkout`.
4. Sistem memvalidasi stok, membuat order, mengurangi stok, membuat payment session.
5. Customer diarahkan ke payment provider.
6. Provider mengirim callback ke `POST /api/payments/callback`.
7. Order berubah menjadi PAID dan diproses untuk fulfillment.

## Provider integration
Adapter saat ini menggunakan response development agar flow dapat diuji tanpa credential. Untuk production, `ShippingService` harus diganti dengan client RajaOngkir/Biteship dan `OrderService` dengan client Midtrans Snap menggunakan secret dari environment. Jangan commit API key.
