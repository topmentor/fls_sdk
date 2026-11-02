<#
.SYNOPSIS
    FLS_SDK 프로젝트를 템플릿으로 신규 Android 프로젝트를 생성합니다.

.DESCRIPTION
    대화형으로 프로젝트명·패키지·플랫폼 설정값을 입력받아, 현재 프로젝트를 지정
    디렉토리에 복사하고 다음을 일괄 치환/재편성합니다.

      1. 소스 파일 복사 (.git, 빌드 산출물, 런타임 디렉토리 제외)
      2. 플랫폼 설정값 치환 (저장 디렉토리명, applicationId, 서버 도메인 힌트 등)
      3. Java 패키지 일괄 치환 (com.sox.flssdk → 신규 패키지)
      4. 프로젝트명 일괄 치환 (SOXCollector / FLS SDK → 신규 프로젝트명)
      5. 소스 패키지 디렉토리 구조 재편성 (com/sox/flssdk → 새/패키지)
      6. 프로젝트명이 포함된 파일명 변경 (SOXCollector.app.iml 등)

    외부 의존성 없이 동작합니다 (Windows PowerShell 5+ / PowerShell 7+).

.EXAMPLE
    .\new-project.ps1
        대화형으로 모든 값을 입력받아 진행합니다.

.EXAMPLE
    .\new-project.ps1 -Dest C:\work\MyApp -ProjectName MyApp -Package com.example.myapp -Yes
        비대화형(스크립트/CI)으로 진행합니다. 누락 값은 대화형으로 보충됩니다.
#>

[CmdletBinding()]
param(
    [string] $Dest,
    [string] $ProjectName,
    [string] $DisplayName,
    [string] $Package,
    [string] $StorageDir,
    [string] $ApplicationId,
    # -Yes: 확인 프롬프트 없이 진행 (비대화형)
    [switch] $Yes
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# -Yes 지정 시 비대화형 모드: 누락된 입력은 기본값으로 채우고 확인 프롬프트를 생략한다.
$script:NonInteractive = $Yes.IsPresent

# ─────────────────────────────────────────────────────────────────────────────
# 원본(현재 프로젝트) 토큰 — 조사 결과에 근거한 고정 값
# ─────────────────────────────────────────────────────────────────────────────
$SRC_MAIN_PKG   = 'com.sox.flssdk'              # 메인 소스 패키지 / applicationId / namespace
$SRC_TEST_PKG   = 'net.stayonbox.soxcollector'  # test / androidTest 패키지
$SRC_EXT_PKG    = 'net.flssdk.app'              # 루트 build.gradle ext.flsSDK (미사용 보조 토큰)
$SRC_GRADLE_NM  = 'SOXCollector'                # settings.gradle rootProject.name / *.iml 파일명
$SRC_APP_NAME   = 'FLS SDK'                      # strings.xml app_name
$SRC_STORAGE    = 'FLSSDK'                       # CommonDoc.java 저장 디렉토리명
$SRC_DOMAIN     = 'example.com'            # fragment_position_setting.xml 서버 URL hint

# 내용 치환 대상 텍스트 확장자 (그 외 확장자는 바이너리로 간주해 스킵)
$TEXT_EXT = @(
    '.java', '.kt', '.xml', '.gradle', '.properties', '.pro',
    '.md', '.txt', '.json', '.cfg', '.yml', '.yaml',
    '.iml', '.bat', '.sh', '.gitignore', '.name'
)
# 확장자가 없어도 텍스트로 처리할 파일명
$TEXT_NAMES = @('gradlew', '.gitignore')

# ─────────────────────────────────────────────────────────────────────────────
# 헬퍼
# ─────────────────────────────────────────────────────────────────────────────
function Write-Step([string] $msg)  { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok([string] $msg)    { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn2([string] $msg) { Write-Host "    [!] $msg" -ForegroundColor Yellow }
function Fail([string] $msg)        { Write-Host "`n[오류] $msg" -ForegroundColor Red; exit 1 }

# 기본값 안내가 포함된 대화형 입력. -Required 면 빈 값 거부, -Validator 로 형식 검증.
function Read-Value {
    param(
        [string] $Prompt,
        [string] $Default = '',
        [string] $Current = '',          # param 으로 이미 받은 값 (있으면 그대로 사용)
        [switch] $Required,
        [scriptblock] $Validator,        # { param($v) ... } → $true/$false
        [string] $ValidatorMsg = '형식이 올바르지 않습니다.'
    )
    while ($true) {
        if ($Current) { $value = $Current; $Current = '' }   # 첫 루프에서만 param 값 사용
        elseif ($script:NonInteractive) {
            # 비대화형: param 미지정 시 기본값 사용 (필수+기본값 없음이면 아래에서 실패 처리)
            $value = $Default
        }
        else {
            $label = if ($Default) { "$Prompt [$Default]" } else { $Prompt }
            $entered = Read-Host $label
            $value = if ([string]::IsNullOrWhiteSpace($entered)) { $Default } else { $entered.Trim() }
        }
        if ($script:NonInteractive -and $Required -and [string]::IsNullOrWhiteSpace($value)) {
            Fail "비대화형 모드에서 필수 값이 누락되었습니다: $Prompt"
        }
        if ($Required -and [string]::IsNullOrWhiteSpace($value)) {
            Write-Warn2 '필수 입력입니다. 값을 입력하세요.'; continue
        }
        if ($Validator -and -not [string]::IsNullOrWhiteSpace($value) -and -not (& $Validator $value)) {
            Write-Warn2 $ValidatorMsg; continue
        }
        return $value
    }
}

# 패키지 형식 검증: 소문자/숫자/언더스코어 세그먼트, '.' 구분, 2개 이상
$PkgValidator = { param($v) $v -match '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$' }
# 프로젝트명 검증: 영문자 시작, 영숫자/언더스코어, 공백·특수문자 불가
$NameValidator = { param($v) $v -match '^[A-Za-z][A-Za-z0-9_]*$' }

# 파일이 텍스트 대상인지 판정
function Test-TextFile([System.IO.FileInfo] $file) {
    if ($TEXT_NAMES -contains $file.Name) { return $true }
    return ($TEXT_EXT -contains $file.Extension.ToLower())
}

# UTF-8(BOM 없음) 으로 내용을 읽고 치환 후, 변경이 있을 때만 다시 쓴다.
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
function Invoke-FileReplace {
    param([string] $Path, [System.Collections.Specialized.OrderedDictionary] $Map)
    $text = [System.IO.File]::ReadAllText($Path)
    $orig = $text
    foreach ($key in $Map.Keys) {
        if ($text.Contains($key)) { $text = $text.Replace($key, $Map[$key]) }  # 대소문자 구분(Ordinal)
    }
    if (-not [object]::ReferenceEquals($text, $orig) -and $text -ne $orig) {
        [System.IO.File]::WriteAllText($Path, $text, $Utf8NoBom)
        return $true
    }
    return $false
}

# ─────────────────────────────────────────────────────────────────────────────
# 0. 원본 디렉토리 확인 (스크립트 위치 = 원본 루트)
# ─────────────────────────────────────────────────────────────────────────────
$SourceRoot = $PSScriptRoot
if (-not (Test-Path (Join-Path $SourceRoot 'settings.gradle'))) {
    Fail "원본 프로젝트 루트를 찾을 수 없습니다 (settings.gradle 없음): $SourceRoot"
}

Write-Host "FLS_SDK 신규 프로젝트 생성 스크립트" -ForegroundColor Magenta
Write-Host "원본: $SourceRoot"

# ─────────────────────────────────────────────────────────────────────────────
# 1. 대화형 입력 수집 & 검증
# ─────────────────────────────────────────────────────────────────────────────
Write-Step '프로젝트 정보 입력'

$ProjectName = Read-Value -Prompt '신규 프로젝트명 (PascalCase, 예: MyCollector)' `
    -Current $ProjectName -Required -Validator $NameValidator `
    -ValidatorMsg '영문자로 시작하고 영숫자/언더스코어만 사용하세요 (공백 불가).'

$DisplayName = Read-Value -Prompt '신규 앱 표시명 (예: My Collector)' `
    -Current $DisplayName -Default $ProjectName -Required

$Package = Read-Value -Prompt '신규 패키지' `
    -Current $Package -Default 'com.example.myapp' -Required -Validator $PkgValidator `
    -ValidatorMsg '소문자 패키지 형식이어야 합니다 (예: com.example.myapp).'

$defaultStorage = ($ProjectName -replace '[^A-Za-z0-9]', '').ToUpper()
$StorageDir = Read-Value -Prompt '신규 저장 디렉토리명 (Downloads 하위)' `
    -Current $StorageDir -Default $defaultStorage -Required `
    -Validator { param($v) $v -match '^[A-Za-z0-9_\-]+$' } `
    -ValidatorMsg '영숫자/언더스코어/하이픈만 사용하세요.'

$ApplicationId = Read-Value -Prompt 'applicationId' `
    -Current $ApplicationId -Default $Package -Required -Validator $PkgValidator `
    -ValidatorMsg '소문자 패키지 형식이어야 합니다.'

$Dest = Read-Value -Prompt '대상 디렉토리 (신규 프로젝트 생성 경로)' `
    -Current $Dest -Required

# 대상 경로 정규화/검증
$DestFull = [System.IO.Path]::GetFullPath($Dest)
$SrcFull  = [System.IO.Path]::GetFullPath($SourceRoot)
if ($DestFull.TrimEnd('\') -ieq $SrcFull.TrimEnd('\')) {
    Fail '대상 디렉토리가 원본과 같습니다.'
}
if ($DestFull.TrimEnd('\').StartsWith($SrcFull.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    Fail '대상 디렉토리가 원본 하위에 있습니다. 원본 밖 경로를 지정하세요.'
}
if ((Test-Path $DestFull) -and (Get-ChildItem -LiteralPath $DestFull -Force | Select-Object -First 1)) {
    Fail "대상 디렉토리가 비어있지 않습니다: $DestFull"
}

# 치환값 미리보기
Write-Step '치환 요약'
$summary = [ordered]@{
    '프로젝트명 (SOXCollector →)'        = $ProjectName
    '앱 표시명 (FLS SDK →)'              = $DisplayName
    '메인 패키지 (com.sox.flssdk →)'     = $Package
    '테스트 패키지 (net.stayonbox.* →)'  = $Package
    'applicationId'                      = $ApplicationId
    '저장 디렉토리 (FLSSDK →)'           = $StorageDir
    '대상 디렉토리'                       = $DestFull
}
$summary.GetEnumerator() | ForEach-Object { Write-Host ("    {0,-34}: {1}" -f $_.Key, $_.Value) }

if (-not $Yes) {
    $confirm = Read-Host "`n위 내용으로 진행할까요? (y/N)"
    if ($confirm -notmatch '^[Yy]') { Write-Host '취소되었습니다.'; exit 0 }
}

# ─────────────────────────────────────────────────────────────────────────────
# 2. 소스 파일 복사 (robocopy, 제외 목록 적용)
# ─────────────────────────────────────────────────────────────────────────────
Write-Step '소스 파일 복사'
New-Item -ItemType Directory -Force -Path $DestFull | Out-Null

# /XD: 이름이 일치하는 디렉토리 전부 제외 (.git, build, .gradle, release, odd)
# /XF: 파일 패턴 제외 (*.log, *.class)
# /XD: .git, 빌드산출물(build), gradle 캐시(.gradle), 런타임(release/odd),
#      Claude 런타임(.claude), 생성 스크립트 산출물(superpowers 스펙)
# /XF: 로그/클래스, 생성 스크립트 본체, 휘발성 IDE 세션 상태(workspace.xml)
$excludeDirs  = @('.git', 'build', '.gradle', 'release', 'odd', '.claude', 'superpowers')
$excludeFiles = @('*.log', '*.class', 'new-project.ps1', 'workspace.xml')
$roboArgs = @($SrcFull, $DestFull, '/E', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/R:1', '/W:1')
$roboArgs += '/XD'; $roboArgs += $excludeDirs
$roboArgs += '/XF'; $roboArgs += $excludeFiles

& robocopy.exe @roboArgs | Out-Null
# robocopy 종료코드 0~7 = 정상, 8 이상 = 실패
if ($LASTEXITCODE -ge 8) { Fail "복사 실패 (robocopy exit $LASTEXITCODE)" }
$global:LASTEXITCODE = 0
Write-Ok "복사 완료 → $DestFull"
Write-Warn2 "제외: $($excludeDirs -join ', ') 디렉토리, $($excludeFiles -join ', ')"

# ─────────────────────────────────────────────────────────────────────────────
# 3+4. 텍스트 내용 치환 (패키지 / 프로젝트명 / 플랫폼 설정값)
# ─────────────────────────────────────────────────────────────────────────────
Write-Step '파일 내용 치환'

# 긴 토큰 우선 순서로 OrderedDictionary 구성
$pkgSlashSrcMain = $SRC_MAIN_PKG.Replace('.', '/')
$pkgSlashSrcTest = $SRC_TEST_PKG.Replace('.', '/')
$pkgSlashDst     = $Package.Replace('.', '/')

# 대소문자 구분(Ordinal) OrderedDictionary — 'flssdk'(leaf)와 'FLSSDK'(저장명)를
# 서로 다른 키로 취급해야 하므로 [ordered]@{}(대소문자 무시) 대신 명시적으로 생성.
$replaceMap = New-Object System.Collections.Specialized.OrderedDictionary([System.StringComparer]::Ordinal)
$replaceMap[$SRC_MAIN_PKG]   = $Package           # com.sox.flssdk → 신규 패키지
$replaceMap[$pkgSlashSrcMain] = $pkgSlashDst       # com/sox/flssdk → 신규 패키지 경로
$replaceMap[$SRC_TEST_PKG]   = $Package           # net.stayonbox.soxcollector → 신규 패키지
$replaceMap[$pkgSlashSrcTest] = $pkgSlashDst       # net/stayonbox/soxcollector → 신규 패키지 경로
$replaceMap[$SRC_EXT_PKG]    = $Package           # net.flssdk.app → 신규 패키지
# 위 패키지 치환 이후 남은 소문자 'flssdk'(예: SharedPreferences 명 "flssdk_position")
# 를 신규 패키지 leaf(마지막 세그먼트)로 치환. 반드시 패키지 치환 뒤에 적용.
$pkgLeaf = ($Package -split '\.')[-1]
$replaceMap['flssdk']        = $pkgLeaf            # flssdk_position → <leaf>_position
$replaceMap[$SRC_GRADLE_NM]  = $ProjectName       # SOXCollector → 프로젝트명
$replaceMap[$SRC_APP_NAME]   = $DisplayName       # FLS SDK → 앱 표시명
$replaceMap[$SRC_STORAGE]    = $StorageDir        # FLSSDK → 저장 디렉토리명
# 서버 도메인은 코드에 박히지 않고 런타임 설정 화면에서 입력된다. 원본의
# 회사 도메인이 힌트(android:hint)로 새 프로젝트에 남지 않도록 중립 예시로만 치환.
$replaceMap[$SRC_DOMAIN]     = 'example.com'       # example.com → example.com (UI 힌트)

# applicationId 가 패키지와 다르면, 패키지 치환으로 이미 바뀐 값을 applicationId 로 교정
$appIdFixupNeeded = ($ApplicationId -ne $Package)

$changedCount = 0
$files = Get-ChildItem -LiteralPath $DestFull -Recurse -File -Force
foreach ($f in $files) {
    if (-not (Test-TextFile $f)) { continue }
    if (Invoke-FileReplace -Path $f.FullName -Map $replaceMap) { $changedCount++ }
}
Write-Ok "$changedCount 개 파일 내용 치환"

# applicationId 보정: app/build.gradle 의 applicationId 만 별도 값으로 교체
if ($appIdFixupNeeded) {
    $appGradle = Join-Path $DestFull 'app\build.gradle'
    if (Test-Path $appGradle) {
        $g = [System.IO.File]::ReadAllText($appGradle)
        # 패키지 치환 후 applicationId 는 신규 패키지로 바뀐 상태 → applicationId 줄만 교정
        $g2 = $g -replace '(applicationId\s+")[^"]*(")', "`${1}$ApplicationId`${2}"
        if ($g2 -ne $g) {
            [System.IO.File]::WriteAllText($appGradle, $g2, $Utf8NoBom)
            Write-Ok "applicationId → $ApplicationId 보정"
        }
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# 5. 소스 패키지 디렉토리 구조 재편성
# ─────────────────────────────────────────────────────────────────────────────
Write-Step '패키지 디렉토리 재편성'

# 옛 패키지 leaf 디렉토리를 신규 패키지 경로로 이동하고, 비게 된 상위 디렉토리 정리
function Move-PackageDir {
    param([string] $JavaRoot, [string] $OldPkg, [string] $NewPkg)
    $oldLeaf = Join-Path $JavaRoot ($OldPkg.Replace('.', '\'))
    if (-not (Test-Path $oldLeaf)) { return }
    $newLeaf = Join-Path $JavaRoot ($NewPkg.Replace('.', '\'))
    if ((Test-Path $newLeaf) -and ($oldLeaf -ieq $newLeaf)) { return }   # 동일 경로면 스킵

    $newParent = Split-Path $newLeaf -Parent
    New-Item -ItemType Directory -Force -Path $newParent | Out-Null

    if (Test-Path $newLeaf) {
        # 신규 leaf 가 이미 존재(상위 경로 공유 등) → 내용물만 병합 이동
        Get-ChildItem -LiteralPath $oldLeaf -Force | ForEach-Object {
            Move-Item -LiteralPath $_.FullName -Destination $newLeaf -Force
        }
        Remove-Item -LiteralPath $oldLeaf -Recurse -Force
    } else {
        Move-Item -LiteralPath $oldLeaf -Destination $newLeaf -Force
    }

    # 비게 된 옛 패키지 상위 디렉토리 정리 (leaf 의 부모부터 JavaRoot 직전까지)
    $cursor = Split-Path $oldLeaf -Parent
    $rootFull = [System.IO.Path]::GetFullPath($JavaRoot).TrimEnd('\')
    while ($cursor) {
        $curFull = [System.IO.Path]::GetFullPath($cursor).TrimEnd('\')
        if ($curFull -ieq $rootFull) { break }
        if (-not (Test-Path $cursor)) { break }
        if (Get-ChildItem -LiteralPath $cursor -Force | Select-Object -First 1) { break }  # 비어있지 않으면 중단
        Remove-Item -LiteralPath $cursor -Force
        $cursor = Split-Path $cursor -Parent
    }
    Write-Ok "$OldPkg → $NewPkg  ($JavaRoot)"
}

Move-PackageDir -JavaRoot (Join-Path $DestFull 'app\src\main\java')        -OldPkg $SRC_MAIN_PKG -NewPkg $Package
Move-PackageDir -JavaRoot (Join-Path $DestFull 'app\src\test\java')        -OldPkg $SRC_TEST_PKG -NewPkg $Package
Move-PackageDir -JavaRoot (Join-Path $DestFull 'app\src\androidTest\java') -OldPkg $SRC_TEST_PKG -NewPkg $Package

# ─────────────────────────────────────────────────────────────────────────────
# 6. 프로젝트명이 포함된 파일명 변경
# ─────────────────────────────────────────────────────────────────────────────
Write-Step '파일명 변경'

# 파일/디렉토리 이름에 포함된 토큰을 신규 값으로 치환 (긴 이름 우선)
$nameMap = [ordered]@{
    $SRC_GRADLE_NM = $ProjectName    # SOXCollector → 프로젝트명 (예: SOXCollector.app.iml)
    $SRC_STORAGE   = $StorageDir     # FLSSDK → 저장 디렉토리명
}
# 깊은 경로부터 처리 (디렉토리 이름 변경이 하위 경로에 영향 주지 않도록)
$renamed = 0
$entries = Get-ChildItem -LiteralPath $DestFull -Recurse -Force |
    Sort-Object { $_.FullName.Length } -Descending
foreach ($e in $entries) {
    $newName = $e.Name
    foreach ($k in $nameMap.Keys) {
        if ($newName.Contains($k)) { $newName = $newName.Replace($k, $nameMap[$k]) }
    }
    if ($newName -ne $e.Name) {
        Rename-Item -LiteralPath $e.FullName -NewName $newName -Force
        Write-Ok "$($e.Name) → $newName"
        $renamed++
    }
}
if ($renamed -eq 0) { Write-Warn2 '이름을 변경할 파일이 없습니다.' }

# ─────────────────────────────────────────────────────────────────────────────
# 완료
# ─────────────────────────────────────────────────────────────────────────────
Write-Step '완료'
Write-Host "신규 프로젝트가 생성되었습니다: $DestFull" -ForegroundColor Green
Write-Host @"

다음 단계:
    cd "$DestFull"
    git init                       # 새 git 저장소로 초기화 (선택)
    .\gradlew assembleDebug        # 빌드 확인

확인 사항:
    - local.properties 의 sdk.dir 경로가 현재 머신에 맞는지 확인
    - 서버 도메인/추가 설정값을 앱 설정 화면 또는 코드에서 점검
"@ -ForegroundColor Gray
