FROM ubuntu:20.04

WORKDIR /app

# Install necessary packages and Java 17
RUN apt-get update \
  && apt-get install -y unzip wget libfreetype6 fontconfig openjdk-17-jdk \
  && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME environment variable
ENV JAVA_HOME /usr/lib/jvm/java-17-openjdk-amd64

# Download and extract IntelliJ IDEA
RUN wget https://download.jetbrains.com/idea/ideaIC-2023.2.tar.gz \
    && tar -xzf ideaIC-2023.2.tar.gz \
    && rm ideaIC-2023.2.tar.gz \
    && mv idea-* idea

# Set the JAVA_HOME for IntelliJ IDEA
RUN echo "idea.jdk=$JAVA_HOME" >> /app/idea/bin/idea.properties

# Create Maven repository directory
RUN mkdir -p /root/.m2/repository

# Set the working directory to /app/idea for plugin operations
WORKDIR /app/idea

# Download and install the Gradle plugin for IntelliJ IDEA
RUN wget -O gradle-intellij-plugin.zip "https://plugins.jetbrains.com/plugin/download?rel=true&updateId=410281" \
    && mkdir -p plugins/gradle-intellij-plugin \
    && unzip gradle-intellij-plugin.zip -d plugins/gradle-intellij-plugin \
    && rm gradle-intellij-plugin.zip

# Set the working directory back to /data
WORKDIR /app
