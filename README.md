# 말해봐요 by 피피 (VoiceWordGame)

그림을 보고 단어를 말하면 점수가 쌓이는 가족 단어 게임입니다.
아이(3·6세)와 부모가 한 화면에서 함께 하도록 만들었습니다.

- 앱 이름: **말해봐요 by 피피**
- 패키지: `com.pipi.voicewordgame`
- 버전 규칙: `v1.0_MMDD_HHmm` (빌드 시점 KST). 예) `v1.0_1121_1605`
- minSdk 24 · targetSdk 35 · compileSdk 35
- 토스트 팝업 없음 — 모든 안내는 화면 안 상태 배너/배지로 표시합니다.

## 기능

| 영역 | 내용 |
| --- | --- |
| 단어 | 10개 카테고리 × 25개 = **250단어** (한글·영문·허용 유사어) |
| 그림 | 단어마다 Noto Emoji 3D 컬러 그림(512×512 WebP) **250장 내장**, 오프라인 즉시 표시 |
| 가족 | 최대 6명 추가/삭제/이름 입력/아바타 선택, 점수판 |
| 주변 소음 | 2초 측정으로 기준선(dBFS) 저장, 게임 중 이보다 큰 소리만 반응 |
| 목소리 등록 | 2.5초 녹음 → 음높이(F0)·스펙트럼 중심·13밴드 에너지로 목소리 지문 저장 |
| 화자 판별 | 정답 순간 목소리를 등록된 지문과 비교 → 가장 가까운 가족에게 +1점 |
| 점수 보정 | 점수판의 다른 가족 칩을 누르면 방금 점수를 그 사람에게 옮김 |
| 게임 | 한글/ENG 토글, 카테고리 필터, 힌트(첫 글자+글자 수), 건너뛰기, 보조 '정답 처리' |
| 정답 연출 | 하트·별 폭죽 파티클 + TTS 발음(ko-KR/en-US) → 1.8초 뒤 다음 그림 |

## 마이크 충돌 대응 (중요)

`AudioRecord` 와 `SpeechRecognizer` 가 마이크를 동시에 잡으면 기기(특히 삼성)에서
인식이 끊깁니다. 그래서 아래 3단계로 동작합니다.

1. **PIPE 모드 (Android 13 / API 33 이상)**
   `AudioRecord` 하나만 마이크를 잡고, 읽은 16kHz PCM 을 `ParcelFileDescriptor` 파이프로
   `RecognizerIntent.EXTRA_AUDIO_SOURCE` 에 먹입니다. 같은 소리를 우리 쪽에서도 계속
   갖고 있으므로 **화자 판별과 음량 측정이 동시에** 가능합니다.
2. **DIRECT 모드** — 파이프가 안 되는 기기에서는 마이크를 `SpeechRecognizer` 에 넘기고
   우리 녹음은 멈춥니다. 화자 구분은 쉬고, 점수는 '받을 사람 누르기'로 정합니다.
3. **MANUAL 모드** — 음성인식 자체가 없는 기기(에뮬레이터 등)에서도 화면 아래
   **정답 처리** 버튼으로 게임이 끝까지 진행됩니다.

오류 코드에 따라 1→2→3 으로 자동 강등되며, 화면의 마이크 배지가 지금 모드를 보여 줍니다.

## 화자 구분 방식

`audio/VoiceFeatureExtractor.kt`
- 16kHz PCM → 32ms 프레임(16ms hop)
- 음성 구간만 골라(최대 음량 -14dB 이상) 프레임별
  - **F0**: 정규화 자기상관 + 배음 오류 방지(최대 상관의 90% 이상인 가장 짧은 주기) + 포물선 보정
  - **스펙트럼 중심**: 60~4000Hz 가중 평균
  - **13밴드 에너지**: 100~5000Hz 로그 간격, log1p 후 단위벡터 정규화
- `audio/SpeakerMatcher.kt`: 피치 0.55 + 중심 0.25 + 밴드 코사인 0.20,
  0.62 이상이고 2위와 0.035 이상 차이날 때만 확정. 아니면 '받을 사람 누르기'로 넘김.

## 프로젝트 구조

```
app/src/main/
  java/com/pipi/voicewordgame/
    MainActivity.kt            화면·게임 진행·점수·권한
    game/WordData.kt           250단어/카테고리/아바타 로더 (assets/words.json)
    game/AnswerMatcher.kt      조사·띄어쓰기 무시 정답 판정(+같은 그림 낱말 인정)
    game/GameSession.kt        문제 큐·점수·점수 옮기기
    game/VoiceProfile.kt       목소리 지문(JSON 직렬화)
    audio/MicCapture.kt        AudioRecord + 파이프 공급 + 음량
    audio/VoiceFeatureExtractor.kt  F0·중심·밴드 추출
    audio/SpeakerMatcher.kt    화자 매칭
    audio/Fft.kt               radix-2 FFT
    speech/AnswerRecognizer.kt SpeechRecognizer 래퍼
    tts/Speaker.kt             TTS(ko-KR/en-US)
    ui/ParticleView.kt         하트·별 폭죽
    ui/LevelMeterView.kt       음량 막대 + 기준선
    util/Prefs.kt, ImageStore.kt
  assets/words.json            250단어 메타(한글·영문·유사어·그림 경로)
  assets/words/001~250.webp    512×512 컬러 그림
  assets/avatars/*.webp        14개 아바타
  assets/categories/*.webp     10개 카테고리 그림
  res/layout, res/drawable, res/values …
app/src/test/java/…            정답 판정·음높이/화자 매칭 단위 테스트 16개
tools/word_data.py             250단어 원본 표(그림 코드 포함)
tools/prepare_assets.py        Noto Emoji 3D 512px → WebP 변환 + words.json + 검증
tools/make_icons.py            런처 아이콘 생성
WORKFLOW_YAML.txt              GitHub Actions 워크플로우 원문(웹에서 직접 추가용)
```

## 빌드

### 로컬(JDK 17 + Android SDK 35)
```bash
./gradlew writeVersionFile assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
# 버전: app/build/outputs/version.txt
```

### 단위 테스트
```bash
./gradlew testDebugUnitTest
```

### GitHub Actions
`.github/workflows/build-apk.yml` (저장소에는 GitHub App 권한 때문에 아직 없음 →
`WORKFLOW_YAML.txt` 내용을 웹에서 `.github/workflows/build-apk.yml` 로 추가)
- push(main) 또는 수동 실행 → JDK 17, Android SDK, Gradle 캐시
- `./gradlew writeVersionFile assembleDebug`
- 산출물: `malhaebwayo-v1.0_MMDD_HHmm.apk` (artifact `malhaebwayo-apk`)

## 데이터/그림 출처

- 그림: [Noto Emoji](https://github.com/googlefonts/noto-emoji) 3D 세트(512px PNG)를
  WebP 512×512(q90)로 변환해 내장. 라이선스 CC BY 4.0 / Apache 2.0 (Noto Emoji 프로젝트 표기).
- 소리: 기기 밖으로 보내지 않습니다. 단, 음성인식은 OS의 인식 서비스(구글 등)를 쓰므로
  해당 서비스 정책에 따라 처리될 수 있습니다.

## 알려진 한계

- 이모지에 전용 그림이 없는 낱말(불가사리·매미·메뚜기·냉장고·세탁기·유모차·포크레인·크레인 등)은
  가장 비슷한 이모지를 씁니다. 이 중 **12쌍 26개 낱말은 그림을 공유**하므로
  (`강/호수/공원`, `침대/이불`, `의자/책상`, `그네/미끄럼틀/놀이터`, `학교/유치원`,
  `경찰서/소방서`, `치마/원피스`, `국수/스파게티`, `오렌지/귤`, `포크레인/크레인`,
  `열기구/풍선`, `매미/메뚜기`) **같은 그림을 쓰는 낱말끼리는 서로 정답으로 인정**합니다.
- 화자 구분은 기기 마이크·거리·주변 소음에 영향을 받습니다. 확신이 낮으면 점수를 바로 주지 않고
  "점수 받을 사람을 눌러 주세요"로 넘겨, 아이가 억울하게 0점이 되지 않게 했습니다.
- 두 사람의 목소리가 아주 비슷하면(부모/자녀) 오인식할 수 있어 점수 옮기기 기능을 넣었습니다.
- 기준 소음보다 작은 소리는 무시하지만, 같은 답이 3번 연속 막히면 통과시켜 게임이 멈추지 않게 했습니다.
