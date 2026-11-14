@echo off
setlocal
set MAVEN_WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar
set MAVEN_WRAPPER_PROPERTIES=%~dp0.mvn\wrapper\maven-wrapper.properties
if not exist "%MAVEN_WRAPPER_JAR%" (
  echo Maven Wrapper JAR is missing. Run the wrapper bootstrap described in README.md.
  exit /b 1
)
java -Dmaven.multiModuleProjectDirectory=%~dp0 -classpath "%MAVEN_WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
