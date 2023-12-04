$directory = "." # Replace with the path to your directory
$javaFiles = Get-ChildItem -Path $directory -Filter *.java -Recurse
$lineCount = 0

foreach ($file in $javaFiles) {
    $lineCount += (Get-Content $file.FullName | Measure-Object -Line).Lines
}

Write-Host "Total line count: $lineCount"
