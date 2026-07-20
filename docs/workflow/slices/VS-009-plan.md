# VS-009 AI 없는 Idea Draft 생성·조회

## 사용자 결과

로그인 사용자는 AI 생성 절차 없이 아이디어 내용을 Draft로 저장하고, 본인이 만든 Draft를 다시 조회할 수 있다.

## 근거와 선행 조건

- 백로그: VS-009, 선행 VS-002, resource lock `idea`
- 선행 PR #17이 2026-07-20에 Merge됐고 Merge 커밋 `d0e2c556c2add76f6ff2a1634ab5cbf0e120f737`이 현재 기준 커밋의 조상임을 확인했다.
- 공개 범위 확정·게시·최초 버전·10P 가격·게시 보상은 VS-010 이후 범위다.
- 공개 범위별 Guest/User/Author/Company 상세 필드 정책은 VS-011 범위다.
- AI Job과 AI 후보는 VS-018 이후 범위다.

## 범위

### 포함

- `POST /api/v1/ideas/drafts`
- `GET /api/v1/ideas/{ideaId}`의 Draft 소유자 조회
- 제목, 카테고리, 한 줄 요약, 문제, 대상 고객, 해결책, 수익 모델 저장
- `DRAFT` 상태, 내부 작성자 ID, 생성·수정 시각 저장
- Bearer Access Token과 활성 세션 기반 인증
- PostgreSQL Migration, JPA 매핑, OpenAPI와 ERD 갱신

### 제외

- 게시, 공개 범위와 비작성자·Guest 상세 조회
- 검증 질문, 최초 가격, 포인트 보상, 버전·타임라인
- 수정·삭제·보관·목록
- AI Job, AI 후보, AI 결과 연결

## API와 데이터 계약

- 생성 요청의 `title`, `category`, `problem`은 필수다.
- `title`은 1~100자, `category`는 1~50자다.
- `summary`는 최대 200자, `problem`·`solution`·`businessModel`은 최대 2,000자, `targetCustomer`는 최대 1,000자다.
- 텍스트 앞뒤 공백은 제거한다. 선택 필드의 빈 문자열은 `null`로 저장한다.
- 생성은 `201 Created`와 `Location`을 반환한다.
- Draft 조회는 작성자에게만 허용한다. 존재하지 않거나 다른 사용자의 Draft는 리소스 존재를 노출하지 않도록 동일한 `404 IDEA_NOT_FOUND`로 응답한다.
- 인증 누락·위조·폐기 세션은 `401 INVALID_ACCESS_TOKEN`으로 응답한다.

## 인수 조건과 테스트 목록

- 유효한 로그인 사용자가 Draft를 생성하면 요청 내용, 작성자, `DRAFT`, 생성·수정 시각이 저장된다.
- 생성 응답의 `Location`과 Draft 조회 결과가 같은 리소스를 가리킨다.
- 선택 입력을 생략하거나 공백으로 입력하면 `null`로 정규화된다.
- 필수 입력 누락·공백 및 길이 초과는 `400 VALIDATION_ERROR`다.
- 인증 없는 생성·조회는 `401 INVALID_ACCESS_TOKEN`이다.
- 다른 사용자는 Draft를 조회할 수 없고 `404 IDEA_NOT_FOUND`를 받는다.
- 응답에 작성자 이메일, 인증 토큰, 세션 ID, 공개 범위, AI 정보가 없다.
- OpenAPI에 생성·조회·Bearer 인증·201/200/400/401/404 계약이 노출된다.
- PostgreSQL Testcontainers에서 새 Migration과 `ddl-auto: validate`가 통과한다.
- 집중 테스트와 `./gradlew clean test` 전체 회귀 테스트가 통과한다.

## 미결정 사항

없음. 카테고리 taxonomy는 기획에서 확정되지 않았으므로 이번 슬라이스에서는 제한 길이 문자열로 저장하고, 임의 enum을 만들지 않는다.
