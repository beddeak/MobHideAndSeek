# MobHideAndSeek

플레이어가 몹으로 변신해 숨는 술래잡기입니다. LibsDisguises와 PacketEvents가 필요합니다.

[다운로드](https://github.com/beddeak/MobHideAndSeek/releases/latest)

## 설치 및 설정

Paper 1.21.4 · Java 21. 새 서버는 서버팩 ZIP을 빈 폴더에 풀고 Windows에서 `start.bat`을 실행하세요. 첫 실행 후 생성된 `eula.txt`를 읽고 동의하면 값을 변경한 뒤 다시 실행하세요. 기존 서버는 정지 후 `MobHideAndSeek-1.0.0.jar`을 `plugins/`에 넣고 다시 시작하세요.

첫 실행 후 `plugins/MobHideAndSeek/config.yml`을 편집하세요. 서버를 정상 종료하고 편집한 뒤 다시 시작하면 새 설정을 읽습니다. [기본 설정과 항목별 설명](src/main/resources/config.yml)

서버팩에는 노을빛 마을·사막 시장·눈 덮인 벌목장·농장·수정빛 폐광 맵이 들어 있습니다. 수동 설치 시에는 LibsDisguises 26.9.26과 PacketEvents 2.14.0이 필요합니다. `/mh maps`로 목록을 확인하고 `/mh test`로 혼자 연습하거나 `/mh start`로 경기를 시작하세요.

## 명령어

`/mobhide`와 `/mh`는 같습니다. 관리자 권한은 `mobhide.admin`이며 기본 OP입니다.

| 명령어 | 역할 |
| --- | --- |
| `/mh admin` | 관리자 GUI |
| `/mh maps` | 랜덤 맵 목록과 현재 맵 |
| `/mh lobby` | 게임에서 나가고 로비로 이동 |
| `/mh mapvisit <ID>` | 관리자 맵 미리보기 (게임 종료 후) |
| `/mh mapsave <ID> <이름>` | 관리자가 편집한 경기장·소환 높이를 맵 목록에 저장 |
| `/mh mapcheck` | 관리자/콘솔: 맵 시작점·소환 가능 위치 점검 |
| `/mh wand`, `/mh pos1`, `/mh pos2` | 전용 도끼 받기 / 현재 위치를 모서리로 선택 |
| `/mh expandvert`, `/mh selclear` | 선택 높이 확장 / 현재 선택 초기화 |
| `/mh setarena` | 두 지점 영역과 현재 술래 시작점 저장. 선택이 없으면 원형 저장 |
| `/mh setarena <반경>`, `/mh setradius <반경>` | 원형 경기장 설정 |
| `/mh start` | 자동 참가 일반 경기. 한 명이면 변신 테스트 |
| `/mh sandbox` | 자동 참가 무제한 샌드박스 |
| `/mh test [몹]` | 실행한 관리자 한 명만 변신 테스트. 지정 종류로 자신과 AI 배치 |
| `/mh test seeker` | 혼자 술래가 되어 AI 오답 HP 테스트 |
| `/mh sethits <1~20>` | 대기 중 술래의 기본 오답 한도 저장 |
| `/mh stop` | 관리자 중단 및 원래 상태 복구 |
| `/mh view` | 자기 변신 표시 전환 |
| `/mh taunt` | 숨는 팀 폭죽 도발 (F와 같음) |
| `/mh tauntfx` | 관리자 혼자 검증용. 바라보는 블록(160블록 이내)에 도발 폭죽과 발광 기둥만 표시, 점수 없음 |
| `/mh status` | 현재 상태와 남은 초·기회 확인 |
| `/mh top` | 누적 점수 TOP 10 |
| `/mh leave` | 현재 게임에서 나가기. 다음 시작에는 자동 참가 |

몹 이름: `cow`, `pig`, `sheep`, `chicken`, `rabbit`, `zombie`, `skeleton`, `creeper`, `villager`, `iron_golem`, `wolf`, `cat`, `mooshroom`, `husk`, `stray`.

토끼·닭 비교: `/mh test rabbit` → F5 → 걷기·멈추기·점프 → `/mh stop` → `/mh test chicken`. 술래 HP 테스트: `/mh sethits 20` → `/mh test seeker`. 개인 피격 기회를 지정했다면 먼저 관리자 패널에서 개인 설정을 초기화하세요.
