@echo off
setlocal
cd /d "c:\VibeCode\EvenBetterHelp"
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set DATABASE_URL=jdbc:postgresql://localhost:5432/helpdesk
set DATABASE_USER=helpdesk
set DATABASE_PASSWORD=helpdesk_secure_dev_pass_2026
set JWT_SECRET=helpdesk-super-secret-jwt-key-2026-very-secure-random
set JWT_ISSUER=helpdesk-local
set APP_ORIGIN=http://localhost:3000
set COOKIE_SECURE=false
set COBALT_WEBHOOK_TOKEN=secret-cobalt-webhook-token-random-82736481
set COBALT_BRIDGE_URL=http://localhost:8090
set COBALT_BRIDGE_TOKEN=secret-cobalt-bridge-token-random-19283746
set COBALT_SESSION_DIR=c:/VibeCode/EvenBetterHelp/.cache/whatsapp-session
set BACKEND_CALLBACK_URL=http://localhost:8080/bridge/events
set CALLS_ENABLED=false
"C:\Users\Midrasha Ezrachit 30\.gradle\wrapper\dists\gradle-9.5.1-bin\iq79hdu3mqx29lgffhp8bfmx\gradle-9.5.1\bin\gradle.bat" -p backend run
