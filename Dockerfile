FROM ubuntu:jammy-20230624

WORKDIR /app

# Install necessary packages
RUN apt-get update \
  && apt-get install -y unzip wget libfreetype6 fontconfig \
  && rm -rf /var/lib/apt/lists/*

# Download and extract IntelliJ IDEA
RUN wget https://download.jetbrains.com/idea/ideaIC-2023.1.3.tar.gz \
    && tar -xzf ideaIC-2023.1.3.tar.gz \
    && rm ideaIC-2023.1.3.tar.gz \
    && mv idea-* idea

# Set the working directory to /app/idea for plugin operations
WORKDIR /app/idea

# Download and install the Gradle plugin for IntelliJ IDEA
RUN wget -O gradle-intellij-plugin.zip "https://plugins.jetbrains.com/plugin/download?rel=true&updateId=410281" \
    && mkdir -p plugins/gradle-intellij-plugin \
    && unzip gradle-intellij-plugin.zip -d plugins/gradle-intellij-plugin \
    && rm gradle-intellij-plugin.zip

# Copy your custom plugin and formatter
COPY build/distributions/formatter-plugin plugins/formatter-plugin
COPY formatter /usr/bin/formatter

# Set the working directory back to /data
WORKDIR /data

ENTRYPOINT ["formatter"]
