# scripts/dev/build_document_service.ps1
$ErrorActionPreference = "Stop"

$base = "services\student-services\document-service"
$cp = (Get-Content "$base\classpath.txt").Trim()
$srcFiles = Get-ChildItem -Path "$base\src\main\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
if (-not (Test-Path "$base\target\classes")) {
    New-Item -ItemType Directory -Path "$base\target\classes" -Force | Out-Null
}

Write-Host "Compiling $($srcFiles.Count) Java source files in document-service..."
& javac -cp "$cp" -d "$base\target\classes" $srcFiles
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed with code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "Compilation succeeded!" -ForegroundColor Green

# Compile test files if classpath includes test libraries (junit etc.)
$testFiles = Get-ChildItem -Path "$base\src\test\java" -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
if ($testFiles) {
    if (-not (Test-Path "$base\target\test-classes")) {
        New-Item -ItemType Directory -Path "$base\target\test-classes" -Force | Out-Null
    }
    $testCp = "$base\target\classes;$cp"
    # check for junit in classpath
    Write-Host "Compiling $($testFiles.Count) test files..."
    & javac -cp "$testCp" -d "$base\target\test-classes" $testFiles
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Test compilation succeeded!" -ForegroundColor Green

        Write-Host "Running JUnit unit tests..."
        & java -cp "$base\target\test-classes;$base\target\classes;$cp" org.junit.runner.JUnitCore com.campx.document.DocumentDomainServiceTest com.campx.document.DocumentControllerTest
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Unit tests failed!" -ForegroundColor Red
            exit $LASTEXITCODE
        }
        Write-Host "All unit tests PASSED!" -ForegroundColor Green
    }
}
