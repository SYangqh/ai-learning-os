@echo off
setlocal
echo Starting AI Learning OS...
cd /d "%~dp0"

:: Step 1: Start infrastructure (PostgreSQL + Redis)
echo [1/3] Starting PostgreSQL + Redis via Docker Compose...
docker compose up -d
if %ERRORLEVEL% neq 0 (
    echo ERROR: Docker Compose failed. Is Docker Desktop running?
    pause
    exit /b 1
)
echo Waiting 5s for DB to be ready...
timeout /t 5 /nobreak >nul

:: Step 2: Spring Boot backend
echo [2/3] Starting backend...
start "AI-LearningOS Backend" cmd /k "cd /d "%~dp0backend-spring" && mvnw.cmd spring-boot:run"

:: Step 3: Next.js frontend
echo [3/3] Starting frontend...
if not exist "%~dp0frontend\node_modules" (
    echo Installing npm packages...
    pushd "%~dp0frontend"
    call npm install -s
    popd
)
start "AI-LearningOS Frontend" cmd /k "cd /d "%~dp0frontend" && npm run dev"

echo.
echo   App:     http://localhost:3000
echo   API:     http://localhost:8080/swagger-ui.html
echo   Swagger: http://localhost:8080/swagger-ui.html
echo.
echo All services started. To stop infrastructure: docker compose down
pause
endlocal
