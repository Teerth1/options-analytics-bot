$oldPath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$pathToRemove = 'C:\Program Files\Common Files\Oracle\Java\javapath;'
$newPath = $oldPath.Replace($pathToRemove, '')
[Environment]::SetEnvironmentVariable('Path', $newPath, 'Machine')
Write-Host "Oracle javapath removed from system PATH"
Write-Host "New system PATH: $newPath"
