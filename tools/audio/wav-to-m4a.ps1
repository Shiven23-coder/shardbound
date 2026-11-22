# Encodes a WAV file as AAC in an .m4a container with Windows' own Media
# Foundation encoder (no extra tools). Called by tools/audio/prepare.mjs for
# the music fallback that Safari on iPhones plays (P-48); Windows only.
#
#   powershell -File tools/audio/wav-to-m4a.ps1 -In <file.wav> -Out <file.m4a> [-Bitrate 64000]
param([Parameter(Mandatory)][string]$In, [Parameter(Mandatory)][string]$Out, [int]$Bitrate = 64000)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
  $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, [Type]$type) {
  $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op)); $task.Wait(-1) | Out-Null; $task.Result }
$asTaskAction = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
  $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncActionWithProgress`1' })[0]
[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Transcoding.MediaTranscoder, Windows.Media.Transcoding, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.MediaProperties.MediaEncodingProfile, Windows.Media.MediaProperties, ContentType = WindowsRuntime] | Out-Null
$src = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($In)) ([Windows.Storage.StorageFile])
$dir = Await ([Windows.Storage.StorageFolder]::GetFolderFromPathAsync((Split-Path $Out))) ([Windows.Storage.StorageFolder])
$dst = Await ($dir.CreateFileAsync((Split-Path $Out -Leaf), [Windows.Storage.CreationCollisionOption]::ReplaceExisting)) ([Windows.Storage.StorageFile])
$profile = [Windows.Media.MediaProperties.MediaEncodingProfile]::CreateM4a([Windows.Media.MediaProperties.AudioEncodingQuality]::Low)
$profile.Audio.Bitrate = $Bitrate
$t = New-Object Windows.Media.Transcoding.MediaTranscoder
$prep = Await ($t.PrepareFileTranscodeAsync($src, $dst, $profile)) ([Windows.Media.Transcoding.PrepareTranscodeResult])
if (-not $prep.CanTranscode) { throw "Cannot transcode: $($prep.FailureReason)" }
$task = $asTaskAction.MakeGenericMethod([double]).Invoke($null, @($prep.TranscodeAsync())); $task.Wait(-1) | Out-Null
"done: $((Get-Item $Out).Length) bytes, profile $($profile.Audio.Bitrate) bps $($profile.Audio.ChannelCount) ch $($profile.Audio.SampleRate) Hz"
