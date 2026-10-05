@echo off
set APP_HOME=%~dp0
if not defined JAVA_HOME goto noJavaHome
set JAVACMD=%JAVA_HOME%\bin\java.exe
if exist "%JAVACMD%" goto run
:noJavaHome
set JAVACMD=java.exe
:run
"%JAVACMD%" %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=gradlew -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
if errorlevel 1 exit /b 1
goto end
:end
