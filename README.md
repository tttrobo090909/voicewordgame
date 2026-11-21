# 말해봐요 by 피피 (VoiceWordGame) v2.1

그림을 보고 단어를 말하면 맞힌 문제가 쌓이는 가족 단어 놀이 앱입니다.
v2.0에서 **가족 등록·목소리 등록·소음 측정·화자 판별을 모두 걷어내고**,
아이 혼자서도 바로 시작할 수 있는 단순한 게임으로 다시 만들었습니다.

- 앱 이름: **말해봐요 by 피피** · 패키지 `com.pipi.voicewordgame`
- 버전 규칙: `v2.1_MMDD_HHmm` (빌드 시점 KST). 예) `v2.1_1121_2255`
- minSdk 24 · targetSdk 35 · compileSdk 35
- 토스트 팝업 0건 — 모든 안내는 화면 안 배너/배지로 표시합니다.

## v2.1에서 달라진 것

| 영역 | v2.0 | v2.1 |
| --- | --- | --- |
| 상·하단 여유 | 시스템 바 인셋만 | 첫 화면·게임 화면·관리자 화면 모두 **시스템 바 + 14dp** 여유 |
| 관리자 진입(제목 3번 탭) | 리플·눌림 표시 있음 | **리플·포커스·하이라이트·탭 소리·진동 없음** (배경 `null`) |
| 정답 발음 | 두 번 모두 0.92 | **1번째 0.72(천천히) → 350ms 쉼 → 2번째 0.95(보통)**, 2번째 완료 +500ms 뒤 다음 문제 (안전 타이머 4초) |
| 병아리 배너 | 96dp · 병아리 76dp, 정지 | **116dp · 병아리 92dp(+20%)**, 첫 화면에서 좌우 ±10dp로 **1.4초 왕복 살랑살랑** |
| 카테고리 배치 | 전체 무작위가 맨 위, 타일 68dp | **6개 카테고리 2열×3행(78dp, +15%) 먼저 → 전체 무작위는 맨 아래** |
| 선택 표시 | 2dp 노란 테두리 | **3dp 진한 코랄-오렌지(#E05A3F) 테두리 + 살짝 진한 배경**(노란 전체 무작위도 확실히 구분) |
| 1글자 한글 힌트 | 글자 그대로 노출(`손`) | **초성만 노출(`ㅅ (1글자)`)** — 42개 1글자 단어의 글자 스포일러 방지 (영문은 기존 방식) |

## v2.0에서 달라진 것

| 영역 | v1.x | v2.0 |
| --- | --- | --- |
| 첫 화면 | 가족 등록·목소리 등록·소음 측정 카드 | **슬림 배너(약 94dp, 화면 높이 10.5%)** + 전체 무작위 + 6개 카테고리 |
| 카테고리 | 10개 | **6개** — 동물(51) · 음식·과일(50) · 탈것(25) · 사물·집(69) · 자연(24) · 사람·몸(31) |
| 점수 | 가족별 점수판·화자 판별 | **맞힌 문제 N개** 하나 |
| 아이콘 | 마이크 이모지 | **3D 노란 병아리**(앱 아이콘) + 배너용 병아리 마스코트 |
| 정답 연출 | 단어 1번 읽고 1.8초 뒤 이동 | **단어를 두 번 읽고**, 두 번째 발음이 끝나고 500ms 뒤 이동 |
| 관리자 | 없음 | 제목 3번 탭 → **랜덤 구구단 잠금** → 문제 관리(검색·추가·수정·삭제·기본값 복원) |

## 게임 흐름

1. 첫 화면에서 **전체 무작위 (250)** 또는 6개 카테고리 중 하나를 고르고 **게임 시작!**
2. 그림이 뜨면 단어를 말합니다. 음성인식이 맞히면 `맞힌 문제 N개`가 올라갑니다.
3. **힌트** → 그림 카드 위쪽에 큰 노란 배지 `힌트: 인○ (2글자)` (28sp)
4. **건너뛰기** → 정답을 보여 주고 똑같이 두 번 읽어 줍니다.
5. **정답 처리** → 음성인식이 어려울 때 대신 눌러 주는 버튼
6. **그만하기** → 맞힌 문제 수 요약

## 음성인식·TTS (실기기 검증 방식 고정)

`dictationtest v1.2`에서 실기기로 확인한 방식 그대로입니다.

- **인식기**: `com.google.android.tts/...GoogleTTSRecognitionService` 를 ComponentName 으로 우선 지정,
  기기에 없으면 시스템 기본 인식기로 자동 대체 (`speech/RecognitionEngines.kt`)
- **마이크**: DIRECT(일반 마이크) 고정 — 오디오 파이프를 쓰지 않습니다.
- **결과 읽기**: `SpeechRecognizer.RESULTS_RECOGNITION` 1순위 → `EXTRA_RESULTS` 폴백
  (`speech/ResultBundles.kt`, 회귀 테스트 `ResultBundlesTest`)
- **침묵 감지 extra**(`EXTRA_SPEECH_INPUT_*`)는 보내지 않습니다 — 일부 인식기의 빈 결과·조기 종료 원인
- `AndroidManifest.xml` `<queries>` 에 `com.google.android.tts` 패키지 추가
- **TTS**: 구글 TTS 엔진 우선 연결, `pitch 1.05`,
  1번째 발음 **0.72(천천히)** → 350ms 쉼 → 2번째 발음 **0.95(보통)** → 완료 후 500ms → 다음 문제.
  TTS 가 없거나 오류면 **4초 안전 타이머**가 대신 다음 문제로 넘깁니다 (`tts/Speaker.kt`)

## 숨은 관리자 화면

- 첫 화면 왼쪽 위 **`말해봐요!` 를 1.2초 안에 3번 연속 탭** → 랜덤 구구단(2~9단) 다이얼로그
- 틀리면 **"틀렸어요"** 를 보여 주고 닫힙니다(진입 불가). 맞히면 문제 관리 화면.
- 문제 관리: 단어 검색 · 6개 카테고리 필터 · 문제 목록(그림/한글·영문 정답/힌트/유사어)
  · **＋ 새 문제** · **수정**(그림: 기기 갤러리 `GetContent` → 앱 내부 WebP 저장, 또는 내장 3D 그림 선택 /
  한글·영문 정답 · 유사어 · 커스텀 힌트) · **삭제** · **기본값 복원**
- 변경 내용은 앱 내부 `filesDir/words.json` 에 즉시 저장되고, 게임 화면에 바로 반영됩니다.
  기본 데이터 버전(`appVersion`)이 바뀌면 새 기본값으로 다시 시작합니다.

## 정답 판정

`game/AnswerMatcher.kt` — 조사·어미·띄어쓰기·문장부호를 무시하고 정답/유사어와 비교합니다.
같은 그림을 쓰는 낱말은 함께 인정합니다.

## 화면 캡처

`ScreenshotTest` 가 Robolectric 으로 실제 화면을 그려 `app/build/ui/` 에 남깁니다.

- `01_first.png` 첫 화면 · `02_game.png` 게임(힌트 배지) · `03_admin.png` 문제 관리

## 테스트

```
./gradlew testDebugUnitTest
```

- `WordDataTest` 250문제 → 6카테고리 매핑·그림 존재·힌트 문구
- `WordStoreTest` 추가·수정·삭제·기본값 복원 영구 저장
- `ResultBundlesTest` 결과 키 우선순위(회귀)
- `MainActivitySmokeTest` 첫 화면 구성·게임 시작·정답 카운트·힌트 배지·2번 읽고 넘어가기·구구단 잠금 3종
- `V21DesignTest` 카테고리 2열×3행·무작위 맨 아래·78dp 타일·3dp 코랄 선택 테두리·제목 탭 무반응·116dp 배너·병아리 살랑살랑
- `SpeakerTest` 1번째 0.72 → 350ms → 2번째 0.95 속도 차등과 4초 안전 타이머 값
- `SystemBarInsetsTest` 상태바·내비바·키보드 인셋 + 상·하단 14dp 여유가 루트 여백으로 적용
- `ScreenshotTest` 화면 3장 캡처

## 빌드

```
./gradlew :app:assembleDebug          # 덮어쓰기 설치용 debug APK
./gradlew :app:testDebugUnitTest      # 테스트 + 화면 캡처
```

## 프로젝트 구조

```
app/src/main/java/com/pipi/voicewordgame/
  MainActivity.kt            첫 화면·게임 진행·숨은 관리자 잠금
  admin/AdminActivity.kt     문제 관리(검색·필터·CRUD·기본값 복원)
  admin/AdminLock.kt         3번 탭 카운터 + 랜덤 구구단
  admin/ProblemAdapter.kt    문제 목록 어댑터
  game/WordData.kt           Word/카테고리 모델과 words.json 파서
  game/WordStore.kt          내부 JSON 저장소(기본값 복사·CRUD)
  game/GameSession.kt        문제 큐·맞힌 문제 수
  game/AnswerMatcher.kt      정답 판정
  game/Hints.kt              첫 글자·1글자 초성 힌트/커스텀 힌트
  speech/AnswerRecognizer.kt 인식기 생성·DIRECT 마이크
  speech/RecognitionEngines.kt 구글 인식기 우선 선택(순수 로직)
  speech/ResultBundles.kt    결과 번들 읽기(1순위/폴백)
  tts/Speaker.kt             천천히/보통 두 번 읽기·안전 타이머
  ui/ParticleView.kt         정답 축하 파티클
  util/ImageStore.kt         assets·내부 파일 그림 로더
  util/Prefs.kt              언어·마지막 카테고리
```

그림은 Noto Emoji(CC-BY 4.0) 3D 일러스트 250장을 내장해 오프라인으로 표시합니다.
소리는 기기 밖으로 보내지 않습니다(음성인식 제외).
