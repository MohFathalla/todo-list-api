
setlocal EnableDelayedExpansion

cd /d "%~dp0"
set "PROJECT_ROOT=%~dp0"
set "API_DIR=%PROJECT_ROOT%todo-api"
set "TESTS_DIR=%PROJECT_ROOT%todo-api-tests"
set "BASE_URL=http://localhost:8080"

cd /d "%API_DIR%"
call mvn -q -DskipTests package
if errorlevel 1 (
    echo RESULT: BUILD FAILED - nothing pushed to GitHub.
    pause
    exit /b 1
)

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /PID %%p /F >nul 2>&1
)

start "TodoApiServer" /min java -jar "%API_DIR%\target\todo-api.jar"

set /a COUNT=0
:WAIT_LOOP
timeout /t 2 /nobreak >nul
curl -s -o nul -w "%%{http_code}" %BASE_URL%/api/categories > "%TEMP%\todo_health.txt" 2>nul
set /p HEALTH=<"%TEMP%\todo_health.txt"
if "!HEALTH!"=="200" goto SERVER_UP
set /a COUNT+=1
if !COUNT! GEQ 45 (
    echo RESULT: SERVER DID NOT START IN TIME - nothing pushed to GitHub.
    pause
    goto STOP_SERVER_FAIL
)
goto WAIT_LOOP

:SERVER_UP
cd /d "%TESTS_DIR%"
call npx playwright test
set "TEST_RESULT=%ERRORLEVEL%"

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /PID %%p /F /T >nul 2>&1
)

if not "%TEST_RESULT%"=="0" (
    echo RESULT: TESTS FAILED - nothing pushed to GitHub.
    pause
    exit /b 1
)

cd /d "%PROJECT_ROOT%"

for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH-mm-ss"') do set "COMMIT_STAMP=%%t"

git add -A
git commit -m "Auto commit %COMMIT_STAMP%" >nul 2>&1
git pull --rebase origin main >nul 2>&1
git push origin main
if errorlevel 1 (
    echo RESULT: TESTS PASSED BUT PUSH FAILED - check network/credentials.
    pause
    exit /b 1
)

echo RESULT: SUCCESS - all tests passed, changes pushed to GitHub as "Auto commit %COMMIT_STAMP%".
pause
exit /b 0

:STOP_SERVER_FAIL
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /PID %%p /F /T >nul 2>&1
)
pause
exit /b 1
