$headers = @{
    "X-User-Id" = "ba77b8f7-ab32-46bd-85d2-5aade3526880"
    "X-Tenant-Id" = "0c914bb2-f63b-472c-a99a-39977112935d"
    "X-User-Role" = "SUPER_ADMIN"
    "Content-Type" = "application/json"
}

Write-Host "=== 1. Testing GET /api/v1/admin/calendars via Gateway:8080 ==="
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/calendars" -Method Get -Headers $headers
    Write-Host "Calendars response:" ($res | ConvertTo-Json -Depth 3)
} catch {
    Write-Host "Calendars GET error: $_"
}

Write-Host "`n=== 2. Testing POST /api/v1/admin/calendars via Gateway:8080 ==="
try {
    $calBody = @{
        code = "AY2026_LIVE"
        name = "Academic Year 2026-2027 Main Schedule"
        calendarType = "ACADEMIC"
        collegeId = "d7ce2532-1f7d-47d2-be33-8c4a6bb26c67"
        status = "DRAFT"
    } | ConvertTo-Json
    $resCal = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/calendars" -Method Post -Headers $headers -Body $calBody
    Write-Host "Calendar created:" ($resCal | ConvertTo-Json)
    $calId = $resCal.id

    Write-Host "`n=== 2b. Adding Event to Calendar $calId via Gateway:8080 ==="
    $evtBody = @{
        title = "Commencement Day Convocation"
        eventType = "COMMENCEMENT"
        isHoliday = $false
        startDate = 1791600000000
        endDate = 1791603600000
    } | ConvertTo-Json
    $resEvt = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/calendars/$calId/events" -Method Post -Headers $headers -Body $evtBody
    Write-Host "Event created:" ($resEvt | ConvertTo-Json)

    $resEvtList = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/calendars/$calId/events" -Method Get -Headers $headers
    Write-Host "Events list count:" $resEvtList.events.Count
} catch {
    Write-Host "Calendar error: $_"
}

Write-Host "`n=== 3. Testing POST /api/v1/admin/number-sequences via Gateway:8080 ==="
try {
    $seqBody = @{
        scopeKey = "ENROLLMENT_2026"
        prefix = "ENR26-"
        nextValue = 5001
        padding = 6
    } | ConvertTo-Json
    $resSeq = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/number-sequences" -Method Post -Headers $headers -Body $seqBody
    Write-Host "Sequence created:" ($resSeq | ConvertTo-Json)

    Write-Host "`n=== 3b. Testing Atomic Generation POST /api/v1/admin/number-sequences/next via Gateway:8080 ==="
    $nextBody = @{
        scopeKey = "ENROLLMENT_2026"
    } | ConvertTo-Json
    $resNext1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/number-sequences/next" -Method Post -Headers $headers -Body $nextBody
    Write-Host "Generated #1:" ($resNext1 | ConvertTo-Json)
    $resNext2 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/number-sequences/next" -Method Post -Headers $headers -Body $nextBody
    Write-Host "Generated #2:" ($resNext2 | ConvertTo-Json)
} catch {
    Write-Host "Sequence error: $_"
}

Write-Host "`n=== 4. Testing GET /api/v1/admin/lookups/types via Gateway:8080 ==="
try {
    $resLkp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/lookups/types" -Method Get -Headers $headers
    Write-Host "Lookup Types count:" $resLkp.lookupTypes.Count
} catch {
    Write-Host "Lookup error: $_"
}

Write-Host "`n=== 5. Testing GET /api/v1/admin/audit/access-events via Gateway:8080 ==="
try {
    $resAccess = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/audit/access-events?limit=5" -Method Get -Headers $headers
    Write-Host "Access events count:" $resAccess.accessEvents.Count
} catch {
    Write-Host "Access events error: $_"
}

Write-Host "`n=== 6. Testing GET /api/v1/admin/audit/change-log via Gateway:8080 ==="
try {
    $resChange = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/audit/change-log?limit=5" -Method Get -Headers $headers
    Write-Host "Change log count:" $resChange.changeLogs.Count
} catch {
    Write-Host "Change log error: $_"
}
