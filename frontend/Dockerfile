FROM node:18

ENV NODE_ENV=production

WORKDIR usr/src/app/

# Setup node
COPY ["package.json", "package-lock.json*", "tailwind.config.js", "./"]
COPY ["tsconfig.json", "./"]

RUN npm install
RUN npm install -g serve

# Source files
COPY public public/
COPY src src/

ENV REACT_APP_BACKEND_URL="sun03.pool.ifis.uni-luebeck.de/shacl-runner-api/"

# Build
RUN npm run build


EXPOSE 6777
EXPOSE 3000

# Serve
# CMD [ "npm", "run", "start"]
CMD [ "serve", "-s", "build"]