#!/bin/sh

# Ensure gradle wrapper is executable
if [ -f "/workspace/gradlew" ]; then
    sudo chmod a+x /workspace/gradlew
fi

# Create gradle properties directory if not exists
mkdir -p ~/.gradle

# Configure Gradle daemon for better performance
cat > ~/.gradle/gradle.properties << 'EOF'
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.jvmargs=-Xmx2048m -XX:+HeapDumpOnOutOfMemoryError
EOF

# Download OpenJDK source code for better IDE support (non-blocking)
if [ ! -f "/opt/java/openjdk/lib/src.zip" ]; then
    echo "Attempting to download OpenJDK source code..."
    cd /tmp
    if curl -L -f --connect-timeout 10 --max-time 120 -o temurin25.tar.gz "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" 2>/dev/null; then
        if tar -xzf temurin25.tar.gz --wildcards "*/lib/src.zip" 2>/dev/null; then
            sudo cp jdk-*/lib/src.zip /opt/java/openjdk/lib/src.zip 2>/dev/null && \
                echo "Installed OpenJDK source code." || \
                echo "Warning: Could not copy src.zip (may need root access)."
        else
            echo "Warning: Could not extract src.zip from downloaded archive."
        fi
        rm -rf temurin25.tar.gz jdk-* 2>/dev/null
    else
        echo "Warning: Could not download OpenJDK source. IDE source navigation may be limited."
    fi
else
    echo "OpenJDK source code already installed."
fi

# Show src.zip status (non-fatal)
ls -la /opt/java/openjdk/lib/src.zip 2>/dev/null || echo "Note: src.zip not available."

echo "Development environment initialized successfully!"



# Execute the main command
exec "$@"

