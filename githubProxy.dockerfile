FROM java:11

# Set the working directory
WORKDIR /app

# Copy the dependencies
COPY target/dependency/BOOT-INF/lib /app/lib

# Copy the application jar
COPY target/dependency/META-INF/maven /app/META-INF/maven
COPY target/dependency/BOOT-INF/classes /app

# Run the app
CMD ["java", "-cp", "app:app/lib/*", "interview.project.Github.GithubProxyController"]