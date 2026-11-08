# MP-100 아카이브

## 2009년 원본

[mp100-2009](mp100-2009/)에는 대학 Java 과제로 만든 MP-100의 Eclipse 프로젝트 설정, Java 소스, 당시 컴파일된 클래스 파일, GIF 리소스가 있습니다. 당시 폴더 구조와 파일 내용 그대로 보존했습니다. 실행 진입점은 `src/PlayerMP.java`의 `main()`입니다.

### Java Media Framework

원본은 JMF 2.1.1e의 `javax.media`에 의존합니다. Eclipse의 `.classpath`는 당시 설정 그대로이며, 소스에서 빌드하려면 JMF를 별도로 준비해 Eclipse 빌드 경로에 추가해야 합니다.

당시 폴더에 들어 있던 `jmf-2_1_1e-windows-i586.exe`는 Sun의 제3자 설치 파일입니다. 배포 조건이 적용되므로, 설치 파일은 [Oracle Java 아카이브의 JMF 2.1.1e Windows Performance Pack](https://www.oracle.com/java/technologies/java-archive-downloads-java-client-downloads.html)에서 받을 수 있습니다. [JMF 바이너리 라이선스](https://www.oracle.com/downloads/licenses/javase-license.html)

로컬 원본에 보존된 JMF 설치 파일의 SHA-256은 아래와 같습니다.

```text
F28FE50C32BE3D9D3D6DC94D99C18D240F99F8A6901D164DA90062B46B6ABC9E
```

Windows 탐색기 썸네일 캐시인 `Thumbs.db`는 로컬에 보존되어 있습니다.

## 리메이크 v1

[remake-v1](remake-v1/)에는 원본 화면과 버튼을 계승한 첫 리메이크의 소스, GIF 리소스, 테스트, Maven 설정이 있습니다. 실행과 테스트 방법은 [v1 README](remake-v1/README.md)에 있습니다.
