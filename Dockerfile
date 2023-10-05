FROM ubuntu:jammy-20230624

WORKDIR /app

RUN apt-get update \
  && apt-get install -y unzip wget libfreetype6 fontconfig \
  && rm -rf /var/lib/apt/lists/*

RUN wget https://download.jetbrains.com/idea/ideaIC-2023.1.3.tar.gz \
    && tar -xzf ideaIC-2023.1.3.tar.gz \
    && rm ideaIC-2023.1.3.tar.gz \
    && mv idea-* idea \
    && cd idea \
    && mv plugins plugins-old \
    && mkdir plugins \
    && cp -r plugins-old/java plugins-old/java-ide-customization plugins-old/keymap-* plugins \
    && rm -r plugins-old

WORKDIR /app/idea

COPY build/distributions/formatter-plugin plugins/formatter-plugin

COPY formatter /usr/bin/formatter

WORKDIR /data

ENTRYPOINT ["formatter"]
