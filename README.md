# MP-100 Studio · v2

MP-100 Studio는 2009년에 만든 Java 음악 플레이어 MP-100을 다시 구현한 JavaFX 데스크톱 앱입니다. 스틸 블루 테마와 좌우 레벨 미터를 갖추고 있으며 로컬 MP3, FLAC, WAV를 재생합니다.

여러 파일을 창에 끌어 놓거나 선택 창에서 추가할 수 있습니다. 목록의 행을 드래그하거나 위·아래 아이콘을 눌러 순서를 바꿉니다. 곡을 두 번 클릭하면 재생됩니다. 일시정지 상태에서 탐색한 뒤 이어듣기를 누르면 새 위치에서 재생을 시작합니다.

## 실행

아래 방법으로 소스에서 실행하거나 Windows EXE를 빌드할 수 있습니다. EXE를 빌드한 뒤 `dist\2.0.5\MP-100 Studio\MP-100 Studio.exe`를 더블클릭하면 실행됩니다. 패키지 폴더에 Java 런타임과 FFmpeg가 함께 들어 있으므로, 이동할 때는 `MP-100 Studio` 폴더 전체를 옮기세요.

### 소스에서 실행

JDK 25 이상, Maven 3.9, FFmpeg의 `ffmpeg`와 `ffprobe`가 필요합니다. 저장소 루트에서 아래 명령을 실행합니다. FFmpeg 도구를 PATH에 추가하거나, PowerShell에서 두 실행 파일이 있는 폴더를 지정합니다.

```powershell
$env:MP100_FFMPEG_DIR = 'C:\Tools\ffmpeg\bin'
mvn javafx:run
```

이미 PATH에서 두 도구를 실행할 수 있으면 환경 변수 설정을 생략할 수 있습니다. [FFmpeg 배포 안내](https://ffmpeg.org/download.html)

### Windows EXE 빌드

Windows의 JDK 25 이상, Maven, MinGW-w64 GCC로 빌드합니다. 기본 FFmpeg 폴더는 `.local\ffmpeg`입니다. 다른 폴더를 쓸 때는 `-FfmpegDirectory`로 `ffmpeg.exe`, `ffprobe.exe`, `LICENSE`가 있는 위치를 지정합니다.

```powershell
.\package-windows.ps1
```

이미 만들어둔 패키지와 나란히 빌드하려면 `-Destination`으로 새 출력 폴더를 지정합니다.

## 오디오 엔진

앱은 곡의 샘플레이트와 비트 깊이를 읽고 해당 PCM 포맷을 우선 시도합니다. 기본 출력 장치가 받아들이는 범위에서 원본 샘플레이트를 우선 유지하고, 필요하면 비트 깊이나 샘플레이트를 조정합니다. 원본이 모노면 모노를 먼저 시도하고, 다채널은 스테레오로 재생합니다. MP3에는 원본 PCM 비트 깊이가 없으므로 화면에도 그렇게 표시됩니다.

Windows EXE는 WASAPI 공유 출력을 우선 사용하며, 열 수 없으면 Java Sound로 재생합니다. 소스 실행에서 WASAPI를 사용하려면 `MP100_WASAPI_DLL`에 빌드한 DLL 경로를 지정합니다. Now Playing의 출력 값은 앱이 연 PCM 스트림의 포맷입니다. Windows 믹서나 장치에서 이 스트림을 추가로 변환할 수 있습니다.

Java 엔진은 일정 시간 분량씩 샘플을 받아 소프트웨어 볼륨을 적용하고 좌우 RMS를 구합니다. 100% 볼륨에서는 디코딩한 PCM을 그대로 전달합니다. 재생 위치는 출력 버퍼에서 소비된 프레임 수로 계산합니다. 탐색이나 곡 전환 시 기존 디코더와 출력을 닫고 새 위치에서 시작합니다. 재생 목록은 각 항목의 ID를 유지하므로 순서를 바꾸거나 앞에 있던 항목을 삭제해도 재생 중인 곡을 계속 추적합니다.

## 테스트

```powershell
mvn test
```

기본 테스트는 가짜 PCM 출력과 헤드리스 JavaFX로 실행됩니다. FFmpeg 도구가 설정된 환경에서는 아래 명령으로 생성한 MP3, FLAC, WAV를 실제 FFmpeg로 디코딩하는 통합 테스트도 실행할 수 있습니다.

```powershell
mvn test '-Dmp100.test.ffmpeg=true'
```

로컬 출력 장치까지 검사하려면 같은 명령에 `'-Dmp100.test.audioDevice=true'`를 추가합니다. 짧은 테스트 톤이 재생됩니다.

Windows WASAPI 경로는 빌드한 DLL을 `MP100_WASAPI_DLL`에 지정한 후 `'-Dmp100.test.wasapi=true'`를 추가해 검사합니다.

## 라이선스

MP-100 Studio 소스와 자체 제작 아이콘은 [MIT License](LICENSE)를 따릅니다. FFmpeg와 JavaFX 등 외부 의존성에는 각각의 라이선스가 적용됩니다.
