# Start all 3 Spring Boot services
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Starting File Processing Pipeline" -ForegroundColor Cyan
Write-Host " Upload:3001 | Processing:3002 | Validation:3003" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$root = "c:\Users\prash\Desktop\dynatrace_poc2"

Write-Host "Starting Upload Service on port 3001..." -ForegroundColor Green
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d $root\upload-service && mvn spring-boot:run" -WindowStyle Normal

Start-Sleep -Seconds 5

Write-Host "Starting Processing Service on port 3002..." -ForegroundColor Green
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d $root\processing-service && mvn spring-boot:run" -WindowStyle Normal

Start-Sleep -Seconds 5

Write-Host "Starting Validation Service on port 3003..." -ForegroundColor Green
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d $root\validation-service && mvn spring-boot:run" -WindowStyle Normal

Write-Host ""
Write-Host "All 3 services starting in separate windows." -ForegroundColor Yellow
Write-Host "Open http://localhost:3001 in your browser." -ForegroundColor Yellow
