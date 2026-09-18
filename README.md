# BeeShop

Platform toko online untuk retail dengan katalog, cart, checkout, payment gateway, ongkir, order tracking, dan integrasi stok BeePos-App.

## Stack
- Frontend: React + Vite + TypeScript + Tailwind CSS
- Backend: Java 21 + Spring Boot 3 + JPA
- Database: PostgreSQL
- Payment: provider abstraction, siap Midtrans
- Shipping: provider abstraction, siap RajaOngkir/Biteship

## Modul
- Catalog dan kategori
- Cart dan checkout
- Customer address
- Order lifecycle
- Payment gateway callback
- Shipping rates dan tracking
- Admin API foundation
- Sinkronisasi stok dengan BeePos-App

## Menjalankan lokal
```bash
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

API: `http://localhost:8081/api`  
Storefront: `http://localhost:5173`

Salin `.env.example` menjadi `.env`. Credential payment dan shipping wajib diisi untuk memanggil provider sungguhan. Tanpa credential, adapter development mengembalikan response simulasi.
