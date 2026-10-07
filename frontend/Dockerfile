# The dashboard SPA (D-004): built with Vite, served by nginx with a same-origin /api proxy to the backend.
# Build context: frontend/ (Render: dockerContext ./frontend, dockerfilePath ./frontend/Dockerfile).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund 2>/dev/null || npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# BACKEND_URL / PORT are substituted into the template at container start (nginx.conf.template).
# Render sets PORT; the dad-coach-ui service sets BACKEND_URL to the backend's public URL.
ENV BACKEND_URL=http://backend:8080
ENV PORT=80
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
