FROM node:18

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:20 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ENV NODE_ENV=development

WORKDIR /usr/src/app/

COPY ["package.json", "package-lock.json*", "./"]

RUN mkdir out

RUN mkdir tmp

RUN npm install

EXPOSE 6777

EXPOSE 9229

CMD [ "npm", "run", "debug" ]
