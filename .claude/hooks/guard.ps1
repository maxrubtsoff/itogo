# PreToolUse guard for Itogo — blocks edits that break the project's hard rules
# (CLAUDE.md §6/§9). Reads the hook JSON on stdin; exit 2 + stderr blocks the
# tool call, exit 0 lets it through. Fails OPEN: any parsing trouble lets the
# call proceed, so a broken guard never stalls work (rules are also backed by
# tests, review and CI).

$ErrorActionPreference = 'Stop'

# Cyrillic block messages must reach the agent as UTF-8, whatever the host codepage.
try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch {}

function Allow { exit 0 }
function Block([string]$msg) { [Console]::Error.WriteLine("ЗАПРЕТ (CLAUDE.md): $msg"); exit 2 }

try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { Allow }
    $j = $raw | ConvertFrom-Json
} catch { Allow }

$tool = [string]$j.tool_name
if ($tool -ne 'Write' -and $tool -ne 'Edit' -and $tool -ne 'MultiEdit') { Allow }

$path = [string]$j.tool_input.file_path
if ([string]::IsNullOrWhiteSpace($path)) { Allow }
$p = $path.Replace('\','/')

# Text being written: Write.content, Edit.new_string, or MultiEdit edits.
$text = ''
if ($null -ne $j.tool_input.content)    { $text += [string]$j.tool_input.content }
if ($null -ne $j.tool_input.new_string) { $text += "`n" + [string]$j.tool_input.new_string }
if ($null -ne $j.tool_input.edits) {
    foreach ($e in $j.tool_input.edits) { $text += "`n" + [string]$e.new_string }
}

# 1. Signing / keystore — an update must install over the current app.
if ($p -match '(?i)\.(keystore|jks)$' -or $p -match '(?i)/debug\.keystore$') {
    Block "подпись и keystore не трогаем — иначе обновление не встанет поверх. Переход на ключ публикации — только по docs/publishing.md и решению Максима."
}
if ($p -match '(?i)/build\.gradle\.kts$' -and $text -match 'signingConfigs') {
    Block "не меняем signingConfigs в build.gradle.kts — стабильная подпись (см. docs/publishing.md)."
}

# 2. Domain purity — no android.*/Compose inside domain.
if ($p -match '(?i)/domain/.+\.kt$' -and $text -match '(?m)^\s*import\s+(android|androidx)\.') {
    Block "домен чист: в domain/* нет импортов android.*/androidx.* (Compose). Выноси считающую/парсящую/решающую логику в чистый Kotlin под тест."
}

# 3. Privacy — no SMS, no QUERY_ALL_PACKAGES in the manifest.
if ($p -match '(?i)AndroidManifest\.xml$') {
    if ($text -match 'QUERY_ALL_PACKAGES') { Block "QUERY_ALL_PACKAGES запрещён — банки объявляются поимённо в <queries>." }
    if ($text -match '(?i)permission\.[A-Z_]*SMS') { Block "чтение SMS запрещено — приложение читает только уведомления." }
}

# 4. Design system — no hex colours / manual Unbounded on screens
#    (ui/* кроме ui/theme и ui/common). Дублирует DesignSystemGuardTest, ловит раньше.
if ($p -match '(?i)/ui/' -and $p -notmatch '(?i)/ui/(theme|common)/' -and $p -match '(?i)\.kt$') {
    if ($text -match 'Color\(0x[0-9A-Fa-f]{6,8}\)') { Block "на экранах нет hex-цветов — бери токены из ui/theme (Slitok)." }
    if ($text -match 'fontFamily\s*=\s*Unbounded') { Block "на экранах нет ручного fontFamily = Unbounded — бери AppTypography из ui/theme." }
}

Allow
