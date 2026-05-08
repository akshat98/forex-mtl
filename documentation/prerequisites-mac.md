# Prerequisites For macOS

Install Homebrew first if needed:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Install Git:

```bash
brew install git
```

Install Docker Desktop:

```bash
brew install --cask docker
```

Install Java 17:

```bash
brew install openjdk@17
```

Use Java 17 in the current shell:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Install sbt:

```bash
brew install sbt
```

After installing Docker Desktop, start the Docker app once and wait until Docker is running.
