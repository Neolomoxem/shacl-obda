param(
    [string]$argFilePath
)

# Function to extract jar paths from the classpath string
function Extract-JarPaths {
    param([string]$classpath)
    $jarPaths = $classpath -split ';' | Where-Object { $_ -like "*.jar" }
    return $jarPaths
}

# Read the contents of the argument file
$argFileContent = Get-Content $argFilePath -Raw

# Regex to find the classpath (-cp) argument and extract the paths
if ($argFileContent -match '-cp "(.+?)"') {
    $classpath = $Matches[1]
} else {
    Write-Host "No classpath (-cp) found in the arguments."
    exit
}

# Extract jar paths from the classpath
$jarPaths = Extract-JarPaths -classpath $classpath

# Ensure the target directory exists
$targetDir = "./build/jars"
if (-Not (Test-Path $targetDir)) {
    New-Item -Path $targetDir -ItemType Directory
}

# Copy each jar file to the target directory
foreach ($jarPath in $jarPaths) {
    $fileName = [System.IO.Path]::GetFileName($jarPath)
    $destinationPath = Join-Path -Path $targetDir -ChildPath $fileName
    Copy-Item -Path $jarPath -Destination $destinationPath -Force
    Write-Host "Copied '$jarPath' to '$destinationPath'"
}

Write-Host "All jar files have been copied to $targetDir."
