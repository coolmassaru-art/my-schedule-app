# 나만의 일정 Android 앱

카카오톡 메시지/스크린샷을 Android 공유 메뉴를 통해 일정으로 저장할 수 있는 간단한 개인 일정 앱입니다.

## APK 자동 빌드
이 저장소의 파일을 GitHub에 올리면 GitHub Actions가 자동으로 APK를 빌드합니다.

1. 저장소의 **Add file → Upload files**
2. 이 프로젝트의 모든 파일/폴더를 업로드
3. **Commit changes**
4. 저장소의 **Actions** 탭 → **Build Android APK**
5. 빌드가 초록색 체크가 되면 실행 결과 하단 **Artifacts**
6. `my-schedule-app-apk` 다운로드
7. ZIP 압축을 풀고 `app-debug.apk`를 Android 휴대폰에 설치

## 카카오톡 사용
- 텍스트: 카카오톡 메시지의 공유 기능 → `나만의 일정`
- 이미지: 스크린샷을 갤러리에서 공유 → `나만의 일정`

공유된 내용이 일정 등록창에 들어오고 날짜/시간을 확인한 뒤 저장할 수 있습니다.
