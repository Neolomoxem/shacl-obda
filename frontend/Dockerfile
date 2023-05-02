FROM node:18

ENV NODE_ENV=development

WORKDIR usr/src/app/

COPY ["package.json", "package-lock.json*", "./"]

RUN npm install

COPY ["tsconfig.json", "./"]

EXPOSE 6777

CMD [ "npm", "start"]