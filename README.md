# 시차 (seecha)

Java 17, Maven, Tomcat 11, JSP/JSTL, Bootstrap으로 구성한 v1.0 개발 프로젝트입니다. 기본 화면은 Servlet에서 JSP로 전달하며, 빌드에 MySQL JDBC 드라이버가 포함됩니다.

## 로컬 실행

프로젝트 폴더에서 다음 명령을 실행합니다.

```sh
./scripts/run-local.sh
```

브라우저에서 [http://127.0.0.1:8081/seecha/](http://127.0.0.1:8081/seecha/)를 엽니다. 종료는 같은 터미널에서 Ctrl+C입니다. Mac에서는 Maven이 PATH에 없으면 IntelliJ에 포함된 Maven을 사용합니다. 설치 위치가 다르면 `MAVEN_BIN`과 `CATALINA_HOME`으로 지정할 수 있습니다.

WAR 파일만 만들려면 `./scripts/build.sh`를 실행합니다. 결과는 `target/seecha.war`입니다. Eclipse에서는 **File → Import → Maven → Existing Maven Projects**로 이 폴더를 열고 Java 17 및 Tomcat 11을 선택합니다. Bootstrap 스타일은 CDN에서 불러옵니다.

## MySQL

로컬 MySQL을 실행한 뒤, 데이터베이스 생성 권한이 있는 계정으로 Workbench에서 `db/init.sql`을 실행합니다. JDBC 연결 대상은 `jdbc:mysql://127.0.0.1:3306/seecha`입니다. 기본 화면은 DB 연결 없이 실행됩니다.

회원가입을 테스트하려면 DB 관리자 계정으로 초기화 SQL을 실행하고, `seecha.members`에 INSERT 권한을 가진 개발용 MySQL 계정을 사용합니다. Eclipse의 Tomcat 실행 구성 또는 로컬 실행 터미널에 다음 환경 변수를 지정하세요.

- `SEECHA_DB_USER` (기본값 `seecha_app`)
- `SEECHA_DB_PASSWORD` (필수)
- `SEECHA_DB_URL` (기본값 `jdbc:mysql://127.0.0.1:3306/seecha`)

홈에서 **회원가입**을 누르면 입력 검증과 성인 확인을 거쳐 계정을 저장합니다. 비밀번호는 bcrypt 해시로 저장합니다.

## 채팅 테스트와 AI 연동

Eclipse의 Tomcat이 실행 중일 때 Chrome 일반 창과 시크릿 창에서 서로 다른 계정으로 로그인합니다. 각각 매칭을 신청하고 대기실에서 준비 완료를 누르면 같은 채팅방에 들어갑니다.

- `GEMINI_API_KEY`를 Tomcat 환경 변수에 추가하면 서버가 주기적으로 상황과 보조 질문을 생성해 MySQL에 저장합니다. 키가 없거나 저장된 주제가 없으면 기본 주제 세트를 사용합니다. 필요하면 `SEECHA_GEMINI_MODEL`로 모델을 지정할 수 있습니다.
- `OPENAI_API_KEY`를 설정하면 메시지 본문을 OpenAI Moderation API로 검사합니다. 키가 없는 로컬 개발 환경에서는 기본 표현·연락처 필터를 적용합니다. 키가 설정된 상태에서 검사 서비스가 실패하면 해당 메시지 전송을 중단합니다.
- API 키는 Eclipse의 Tomcat 실행 설정에서 넣고 서버를 재시작합니다. 키는 소스 코드나 Git에 추가하지 않습니다.
- 채팅 메시지는 메모리에만 보관하고 종료 시 삭제합니다. 서버를 재시작하면 진행 중이던 대기실과 채팅방도 초기화됩니다.
