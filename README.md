# SSU_MP_2team
숭실대학교 2026년 2학기 모바일프로그래밍 2조 팀프로젝트

## 앱 이름: Just Snap It

Kotlin / Jetpack Compose 기반 사진 스티커 수집 앱 (패키지: `com.ssu.mp.justsnapit`)

## 파일 구조

```text
app/src/main/java/com/ssu/mp/justsnapit/
├── SnapApp.kt
├── data/
│   └── StickerStore.kt
├── ui/
│   ├── camera/
│   │   └── CameraScreen.kt
│   ├── collection/
│   │   ├── CollectionScreen.kt
│   │   ├── StickerJar.kt
│   │   └── StickerDetail.kt
│   ├── editor/
│   │   └── StickerEditor.kt
│   └── components/
│       ├── RoundButton.kt
│       ├── ModeButton.kt
│       └── SnapIcon.kt
└── physics/
    └── StickerPhysics.kt
```

| 파일 | 설명 |
|---|---|
| [SnapApp.kt](app/src/main/java/com/ssu/mp/justsnapit/SnapApp.kt) | 앱 상태와 화면 전환, 사진 선택 및 스티커 저장·삭제 흐름을 관리합니다. |
| [StickerStore.kt](app/src/main/java/com/ssu/mp/justsnapit/data/StickerStore.kt) | 스티커 데이터 저장·조회·삭제와 배경 제거·테두리·지우개 처리를 담당합니다. |
| [CameraScreen.kt](app/src/main/java/com/ssu/mp/justsnapit/ui/camera/CameraScreen.kt) | 카메라 권한, 미리보기, 촬영 및 전후면 전환을 처리합니다. |
| [CollectionScreen.kt](app/src/main/java/com/ssu/mp/justsnapit/ui/collection/CollectionScreen.kt) | 수집함 메뉴와 스티커 목록, 격자 보기 및 빈 상태 안내를 표시합니다. |
| [StickerJar.kt](app/src/main/java/com/ssu/mp/justsnapit/ui/collection/StickerJar.kt) | 스티커를 화면에 배치하고 낙하 애니메이션과 드래그·클릭 입력을 연결합니다. |
| [StickerDetail.kt](app/src/main/java/com/ssu/mp/justsnapit/ui/collection/StickerDetail.kt) | 확대 이미지와 이름, 기기 현지 시간대의 생성 시각 및 닫기·삭제 버튼을 표시합니다. |
| [StickerEditor.kt](app/src/main/java/com/ssu/mp/justsnapit/ui/editor/StickerEditor.kt) | 스티커 편집 이미지와 지우개 크기 조절, 원본 복구 및 저장 도구를 표시합니다. |
| [RoundButton.kt](app/src/main/java/com/ssu/mp/justsnapit/ui/components/RoundButton.kt) | 아이콘, 크기, 강조 여부와 클릭 동작을 지정하는 공통 원형 버튼입니다. |
| [ModeButton.kt](app/src/main/java/com/ssu/mp/justsnapit/ui/components/ModeButton.kt) | 카메라와 수집함 모드를 전환하고 선택 상태를 표시하는 버튼입니다. |
| [SnapIcon.kt](app/src/main/java/com/ssu/mp/justsnapit/ui/components/SnapIcon.kt) | 선과 도형으로 공통 아이콘을 그립니다. |
| [StickerPhysics.kt](app/src/main/java/com/ssu/mp/justsnapit/physics/StickerPhysics.kt) | 스티커의 위치·속도·중력·드래그 및 바닥·벽 충돌을 계산합니다. |

## 기본 구현 범위

- 기본 UI, 카메라와 수집함 전환
- Photo Picker 사진 선택, CameraX 촬영과 전후면 전환
- ML Kit 피사체 배경 제거, 지우개 크기 조절 및 원본 복구
- 투명 여백 자르기와 흰색 스티커 테두리 생성
- 상단 낙하, 바닥/벽 반사, 드래그와 놓기, 클릭 상세 보기
- 격자 보기, 생성일 확인, 삭제, 앱 재실행 후 로컬 수집 유지

스티커 PNG는 앱 내부 files/stickers 폴더에, 메타데이터는 SharedPreferences에 저장합니다.
이미지 파일명은 `SnappedAt20261102053025-UTC-a8f31c92.png` 형식입니다. UTC(협정 세계시) 저장 시각과 8자리 소문자 16진수 난수를 사용하며, 동일한 파일명이나 식별자가 있으면 난수를 다시 생성하여 재시도합니다.
이 형식의 식별자와 일치하는 파일만 불러옵니다. 파일명과 화면 표시 이름은 별개이며 사용자가 원하는 이름으로 수정할 수 있습니다.
기본 단계의 작은 수집함을 위한 구조이며, 큰 컬렉션으로 확장할 때 Room과 이미지 캐시로 전환할 수 있습니다.

## 추가 할 사항

- ML Kit Subject Segmentation은 베타이며 Google Play services가 필요합니다. 최초 모델 다운로드에는 인터넷이 필요합니다.
- 배경 제거 결과는 사진에 따라 다릅니다. 모델 다운로드/처리 실패 시 안내를 표시합니다.
- 낙하와 드래그는 단순 물리 구현입니다. 스티커 간 충돌, 기울기 센서, 위치 영구 저장은 추후 구현해야 합니다.
- 폴더(또는 태그), 공유/내보내기, 편집 확대/축소, 복원 브러시와 게임 기능은 후속 범위입니다.

## 협업

`main`/`master`에서 직접 개발하지 않고 작업 브랜치에서 변경한 뒤 push하며, PR(Pull Request)로 통합합니다.
