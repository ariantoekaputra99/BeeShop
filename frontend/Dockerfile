FROM node:20-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
# production | uat | sit -> memakai frontend/.env.<mode>
ARG BUILD_MODE=production
# Opsional: timpa URL API tanpa mengubah file .env.<mode>
ARG API_URL=
RUN if [ -n "$API_URL" ]; then export VITE_API_URL="$API_URL"; fi \
    && npm run build -- --mode "$BUILD_MODE"

FROM nginx:1.27-alpine
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
