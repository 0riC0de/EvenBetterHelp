@echo off
setlocal
cd /d "c:\VibeCode\EvenBetterHelp"
set JWT_SECRET=helpdesk-super-secret-jwt-key-2026-very-secure-random
set JWT_ISSUER=helpdesk-local
set "NODE_EXE=C:\Users\Midrasha Ezrachit 30\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
if not exist "%NODE_EXE%" (
    set NODE_EXE=node
)
echo.
echo ========================================================
echo   EvenBetterHelp Sign-In Token
echo ========================================================
echo.
"%NODE_EXE%" ops\issue-dev-token.mjs
echo.
echo ========================================================
echo Copy the token above and paste into http://localhost:3000
echo ========================================================
echo.
pause

