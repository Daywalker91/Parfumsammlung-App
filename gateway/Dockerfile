# Lizenz-Gateway — minimales Node-Image, kein Build-Step nötig (reines JS).
FROM node:20-alpine

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev

COPY server.js ./
COPY src ./src

ENV PORT=8080
EXPOSE 8080

# Kein Root nötig — node:20-alpine bringt einen unprivilegierten "node"-User mit.
USER node

CMD ["node", "server.js"]
