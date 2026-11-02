# ======================================================================
# Dockerfile frontend SmartStock.
#
# React là ứng dụng tĩnh sau khi build: chỉ còn HTML/CSS/JS. Vì vậy ảnh chạy
# chỉ cần Nginx để phục vụ file tĩnh, không cần Node.
# ======================================================================

# ---------------------- Tầng 1: build ----------------------
FROM node:20-alpine AS builder

WORKDIR /build

# Copy file khai báo thư viện trước để tận dụng cache của Docker
COPY package.json package-lock.json* ./
# npm ci cài đúng phiên bản trong lock file, bảo đảm build tái lập được
RUN npm ci --no-audit --no-fund

COPY . .

# Địa chỉ backend phải được "nhúng" vào mã lúc BUILD, vì đây là ứng dụng tĩnh
# chạy trên trình duyệt - không đọc được biến môi trường lúc chạy.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# ---------------------- Tầng 2: phục vụ file tĩnh ----------------------
FROM nginx:1.27-alpine

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -q --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
