# 나만의 일정 - 홈 화면 위젯 + 업데이트 설치 개선

## 포함 기능
- 홈 화면 '오늘 일정' 위젯
- 오늘 최대 4개 + 내일 최대 2개 일정 표시
- 위젯을 누르면 앱 실행
- 일정 저장/수정/삭제 시 위젯 자동 갱신
- 30분 주기 자동 갱신
- GitHub Actions의 versionCode를 빌드마다 자동 증가
- 고정 서명키를 GitHub Secrets로 사용하여 이후 APK를 삭제 없이 업데이트 가능

## 저장소에 반영할 파일
압축을 풀고 저장소에 동일한 경로/이름으로 업로드하세요.
- MainActivity.java (교체)
- ScheduleWidgetProvider.java (추가)
- AndroidManifest.xml (교체)
- strings.xml (교체)
- widget_schedule.xml (추가)
- schedule_widget_info.xml (추가)
- widget_bg.xml (추가)
- .github/workflows/build-apk.yml (교체)

## 업데이트 설치가 되게 하는 필수 설정
GitHub 저장소 → Settings → Secrets and variables → Actions → New repository secret

별도로 받은 `github-signing-secrets.txt`의 4개 값을 각각 등록하세요:
- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

중요:
- `github-signing-secrets.txt`나 `.jks` 파일을 Public 저장소에 올리지 마세요.
- 서명키 적용 후 처음 한 번은 기존 디버그 APK와 서명이 달라 기존 앱을 삭제 후 설치해야 합니다.
- 그 다음 빌드부터는 같은 서명 + 자동 증가 versionCode를 사용하므로 APK를 지우지 않고 업데이트 설치할 수 있습니다.

## 위젯 추가
새 APK 설치 후 홈 화면 빈 곳 길게 누르기 → 위젯 → '나만의 일정' → 오늘 일정 위젯 추가
