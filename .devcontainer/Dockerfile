# Development container for Spring Boot with Java 25
FROM eclipse-temurin:25-jdk-jammy

# Install essential tools
RUN apt-get update && apt-get install -y \
  bash \
  bash-completion \
  git \
  curl \
  wget \
  unzip \
  zip \
  vim \
  sudo \
  procps \
  lsof \
  htop \
  && rm -rf /var/lib/apt/lists/*

# Create a non-root user with sudo access
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN groupadd --gid $USER_GID $USERNAME \
  && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME -s /bin/bash \
  && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
  && chmod 0440 /etc/sudoers.d/$USERNAME \
  # Create .gradle directory with correct ownership for the vscode user
  && mkdir -p /home/$USERNAME/.gradle \
  && chown -R $USERNAME:$USERNAME /home/$USERNAME/.gradle

# Set working directory
WORKDIR /workspace

# Install Starship prompt
RUN curl -sS https://starship.rs/install.sh | sh -s -- --yes

# Copy entrypoint script and ensure Unix line endings (fixes Windows CRLF issue)
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN sed -i 's/\r$//' /usr/local/bin/entrypoint.sh && chmod a+x /usr/local/bin/entrypoint.sh

# Copy Starship configuration
RUN mkdir -p /home/$USERNAME/.config
COPY starship.toml /home/$USERNAME/.config/starship.toml
RUN chown -R $USERNAME:$USERNAME /home/$USERNAME/.config

# Switch to non-root user
USER $USERNAME

# Initialise Starship in bash
RUN echo '' >> ~/.bashrc \
  && echo '# Starship prompt' >> ~/.bashrc \
  && echo 'eval "$(starship init bash)"' >> ~/.bashrc

# Set JAVA_HOME
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["sleep", "infinity"]
