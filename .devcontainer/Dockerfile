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
  jq \
  pandoc \
  texlive-latex-base \
  texlive-latex-recommended \
  texlive-latex-extra \
  texlive-fonts-recommended \
  && rm -rf /var/lib/apt/lists/*

# Install Node.js 22 LTS (required for Claude CLI and frontend tooling)
RUN curl   -fsSL https://deb.nodesource.com/setup_22.x | bash - \
  && apt-get install -y --no-install-recommends nodejs \
  && rm -rf /var/lib/apt/lists/*

# Install Claude CLI
RUN npm install -g @anthropic-ai/claude-code

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

# Initialise Starship in bash and configure terminal colors
RUN echo '' >> ~/.bashrc \
  && echo '# 256-color terminal' >> ~/.bashrc \
  && echo 'export TERM=xterm-256color' >> ~/.bashrc \
  && echo '' >> ~/.bashrc \
  && echo '# Colored output aliases' >> ~/.bashrc \
  && echo 'alias ls="ls --color=auto"' >> ~/.bashrc \
  && echo 'alias ll="ls --color=auto -lh"' >> ~/.bashrc \
  && echo 'alias la="ls --color=auto -lha"' >> ~/.bashrc \
  && echo 'alias grep="grep --color=auto"' >> ~/.bashrc \
  && echo 'alias egrep="egrep --color=auto"' >> ~/.bashrc \
  && echo 'alias fgrep="fgrep --color=auto"' >> ~/.bashrc \
  && echo 'alias diff="diff --color=auto"' >> ~/.bashrc \
  && echo 'alias ip="ip --color=auto"' >> ~/.bashrc \
  && echo '' >> ~/.bashrc \
  && echo '# LS colors (GNU dircolors)' >> ~/.bashrc \
  && echo 'eval "$(dircolors -b)"' >> ~/.bashrc \
  && echo '' >> ~/.bashrc \
  && echo '# Git colors' >> ~/.bashrc \
  && echo 'git config --global color.ui auto' >> ~/.bashrc \
  && echo 'git config --global color.branch auto' >> ~/.bashrc \
  && echo 'git config --global color.diff auto' >> ~/.bashrc \
  && echo 'git config --global color.status auto' >> ~/.bashrc \
  && echo '' >> ~/.bashrc \
  && echo '# Starship prompt' >> ~/.bashrc \
  && echo 'eval "$(starship init bash)"' >> ~/.bashrc

# Set JAVA_HOME
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["sleep", "infinity"]
