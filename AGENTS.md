# 저장소 가이드라인

## 프로젝트 구조와 모듈 구성

이 저장소는 `acttub-backend`라는 Spring Boot 백엔드입니다. 메인 Java 소스는 `src/main/java/com/loading/acttub_backend/` 아래에 있으며, 애플리케이션 진입점은 `ActtubBackendApplication.java`입니다. 런타임 설정은 `src/main/resources/`에 둡니다. 테스트는 `src/test/java/com/loading/acttub_backend/` 아래에서 메인 패키지 구조를 따라갑니다.

새 코드는 기준 패키지 아래에서 기능 또는 레이어 기준으로 정리합니다. 예를 들어 책임에 따라 `controller`, `service`, `repository`, `domain`, `config` 같은 패키지를 사용할 수 있습니다. `build/`는 생성 산출물이므로 직접 수정하거나 커밋하지 않습니다.

## 빌드, 테스트, 개발 명령

항상 Gradle Wrapper를 사용합니다.

- `./gradlew bootRun`: 로컬에서 Spring Boot 애플리케이션 실행
- `./gradlew test`: JUnit 테스트 실행
- `./gradlew build`: 컴파일, 테스트, 패키징 실행
- `./gradlew clean`: 생성된 빌드 산출물 제거

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용합니다.

## 코드 스타일과 이름 규칙

이 프로젝트는 Java 21과 Spring Boot 3.5.x를 사용합니다. 기존 Java 스타일을 따릅니다. Java 파일은 탭 들여쓰기를 사용하고, 중괄호는 같은 줄에 둡니다. 클래스 이름은 `PascalCase`, 메서드/필드/지역 변수는 `camelCase`를 사용합니다. 패키지 이름은 소문자로 작성합니다.

Spring 컴포넌트는 생성자 주입을 우선합니다. Lombok은 가독성을 높일 때만 사용하고, 중요한 동작을 생성 코드 뒤에 숨기지 않습니다. YAML 설정 키는 소문자로 작성하고 하위 시스템별로 묶습니다.

## 테스트 가이드라인

테스트는 Spring Boot Test와 JUnit Platform을 사용합니다. 테스트 파일은 `src/test/java` 아래에 두고, 테스트 대상 코드와 같은 패키지 구조를 사용합니다. 테스트 클래스 이름은 `Tests` 접미사를 사용합니다. 예: `ActtubBackendApplicationTests`.

변경 제출 전 `./gradlew test`를 실행합니다. 새 컨트롤러, 서비스, 설정, 예외 처리에는 집중된 테스트를 추가합니다. 단위 테스트에서는 외부 서비스에 의존하지 말고 mock 또는 test slice를 우선 사용합니다.

## 커밋과 PR 가이드라인

브랜치, 커밋, PR, Codex 리뷰 관련 규칙은 [Git 워크플로우](docs/git-workflow.md)를 따릅니다.

커밋 메시지는 한글 Conventional Commits 형식으로 작성합니다. 예:

- `feat(coaching): 코칭 API 구현`
- `fix(coaching): Gemini 파일 처리 대기 추가`
- `docs: Git 워크플로우 문서화`

PR 제목/본문/리뷰 응답은 한글로 작성합니다. PR 본문에는 변경 요약, 테스트 결과, 관련 이슈를 포함합니다. API 동작이 바뀌면 요청/응답 예시를 포함합니다.

Codex 리뷰 요청과 응답도 한글로 작성합니다. Codex 리뷰는 스타일 취향보다 실제 버그, 운영 리스크, 아키텍처 경계, DB 마이그레이션 안정성, API 계약, 누락된 테스트를 우선하도록 요청합니다.

## 보안과 설정

secret, credential, 로컬 DB dump, 생성 로그를 커밋하지 않습니다. 환경별 설정은 가능하면 추적 파일 밖에 두고, 필요한 설정은 PR 또는 README에 문서화합니다.

## 에이전트 전용 지침

사용자가 `ㄱㄱ`라고 말하면 현재 제안한 다음 단계에 대한 승인으로 간주합니다.
