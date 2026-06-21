# Acttub Backend 문서

이 디렉터리는 API 계약, 데이터 구조, 배포/네트워크 판단, 개발 프로세스 문서를 모아둡니다. 코드가 바뀌어 API 응답, DB 스키마, 운영 설정이 달라지면 관련 문서를 같이 갱신합니다.

## API와 데이터

- [현재 API 요청/응답 명세](specs/current-api-contract.html): Controller, DTO, 예외 매핑 기준 요청/응답 계약
- [테이블 구조](specs/database-schema.html): PostgreSQL/Flyway 기준 테이블과 API 필드 매핑
- [코칭 카드 응답 예시](specs/coach-card-response-example.html): 코칭 카드 응답 JSON과 화면 렌더링 예시

## 아키텍처

- [레이어 흐름](architecture/layer-flow.html): 코칭 패키지의 레이어, 의존 관계, 클린 아키텍처 관점 정리

## 프로세스

- [Git 워크플로우](git-workflow.md): 브랜치 흐름, 커밋 메시지, PR, Codex 리뷰 규칙
