# 대규모 트래픽 대응을 위한 EKS 기반 수강신청 시스템 구축

> Terraform 기반 인프라 자동화(IaC), GitHub Actions·ArgoCD 기반 GitOps 배포 파이프라인, Kubernetes 오토스케일링(KEDA/HPA)을 적용하여 안정적인 수강신청 서비스를 구축하고 k6 부하 테스트를 통해 성능과 가용성을 검증한 프로젝트

<br>

---

## 1.  프로젝트 목표

- 수강신청 트래픽 집중 상황 대응
- Kubernetes 기반 자동 확장
- GitOps 기반 무중단 배포
- IaC 기반 인프라 자동 구축
- 부하 테스트를 통한 성능 검증

<br>

---

## 2. 브랜치 전략 & PR 규칙
### 2-1. 커밋 컨벤션

| Prefix | 용도 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 및 에러 수정 |
| `refactor` | 프로덕션 코드 리팩토링 (기능 변경 없음) |
| `chore` | 빌드 업무 수정, 패키지 매니저 설정, 의존성 변경 |
| `docs` | 문서 및 주석 수정 |

<br>

### 2-2. PR 가이드라인

1. `feature/*` → `develop` 방향으로만 PR 생성
2. PR 제목 형식: `[Prefix] 작업 내용` (예: `[feat] 강의 검색 API 구현`)
3. PR 본문 필수 항목 (무엇을/왜/어떻게, 영향 범위, 테스트 결과)
4. 인프라 변경 PR(`infra` 레포)은 `terraform plan` 결과 본문 첨부 필수
5. 팀원 1인 이상 리뷰 후 approve → merge (approve 없이 merge 금지)

<br>

### 2-3. 브랜치 구조 및 배포 책임

```
main
 └── develop
      └── feature/이름/기능명     # 예: feature/yueun/subject_search
```
* `feature/*` → `develop` 방향으로만 PR 생성
* CI는 App 레포, CD는 Config 레포로 책임 분리 (GitOps 표준)

<br>

---

## 3. 레포지토리

| 구분 | URL |
| --- | --- |
| App (소스코드) | https://github.com/CLD-05/team1-app |
| Config (GitOps 매니페스트) | https://github.com/CLD-05/team1-config |
| Infra (Terraform IaC) | https://github.com/CLD-05/team1-infra |

<br>

---

## 4.️ 아키텍처 개요

```
[ 외부 트래픽 분리 구조 (Traffic Flow) ]

사용자 요청 (User Request)
    │
    ├──▶ [FE] Route 53 ──▶ CloudFront (CDN) ──▶ S3 Bucket (정적 호스팅)
    │
    └──▶ [BE] Route 53 ──▶ ALB (AWS Load Balancer Controller)
                                   │
                                   ▼
                           AWS EKS Cluster
                           ├── Ingress ──▶ Spring Boot Pods (수강신청 서비스)
                           └── ArgoCD (GitOps CD) ◀── [team1-config 레포]


[ 데이터베이스 및 인프라 관리 (Data & IaC Backend) ]

Spring Boot Pods ──▶ AWS RDS (MySQL DB)
GitHub Actions   ──▶ AWS ECR (컨테이너 이미지 빌드/푸시)
Terraform        ──▶ S3 + DynamoDB (원격 백엔드 및 Lock 관리)


[ 검증 및 관측성 인프라 (Testing & Observability) ]

k6 (부하 발생기) ──▶ ALB ──▶ Spring Boot Pods
                                   │ (Metrics 수집)
                                   ▼
                       Prometheus & Grafana (대시보드 모니터링)
```

<br>

--- 
## 5. CI/CD 파이프라인

**CI 흐름 (App 레포)**

```
Developer Push / PR Merge 
      ↓ 
GitHub Actions 실행 
      ↓ 
AWS OIDC 인증 
      ↓ 
Spring Boot Build 
      ↓ 
Docker Image Build 
      ↓ 
Amazon ECR Push 
      ↓ 
Config Repository Image Tag 갱신
```

**CD 흐름 (Config 레포)**

```
Config Repository 변경 
      ↓ 
ArgoCD 변경 감지 
      ↓ 
Manifest 동기화(Sync) 
      ↓ 
Amazon EKS 배포 
      ↓ 
Rolling Update 수행 
      ↓ 
신규 Pod 생성 
      ↓ 
Health Check 
      ↓ 
서비스 반영 완료
```
**GitOps Workflow**

```
App Repository 
     ↓ 
GitHub Actions 
     ↓ 
AWS OIDC Role
     ↓ 
Amazon ECR 
     ↓ 
Config Repository 
     ↓  
ArgoCD 
     ↓ 
Amazon EKS 
     ↓ 
Running Pod

```

<br>


--- 

## 6. 기술 스택

### Application

| 분류 | 기술 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.3 |
| Build | Maven |
| DB | MySQL 8.0 (RDS) |
| ORM | Spring Data JPA |
| Auth | JWT (Cookie 기반) |
| Template | Thymeleaf |
| Migration | Flyway |

### Infrastructure

| 분류 | 기술 |
| --- | --- |
| Container | Docker (멀티스테이지), Amazon ECR |
| Orchestration | Amazon EKS (Kubernetes) |
| IaC | Terraform (S3 + DynamoDB Backend) |
| CI | GitHub Actions (OIDC) |
| CD | ArgoCD |
| Ingress | AWS ALB Controller + IRSA |
| Auto Scaling | HPA, KEDA |
| Monitoring | Prometheus, Grafana, CloudWatch, k6 |


<br>

--- 

## 7.  레포지토리 구조

### team1-app (App 레포)

```
src/
├── main/
│   ├── java/com/ops/app/courseregistration/
│   │   ├── auth/
│   │   │   ├── controller/    # 로그인, 로그아웃 API
│   │   │   ├── dto/           # 인증 요청/응답 DTO
│   │   │   ├── jwt/           # JWT 생성 및 검증
│   │   │   └── service/       # 인증 비즈니스 로직
│   │   │
│   │   ├── course/
│   │   │   ├── controller/    # 강의 조회 API
│   │   │   ├── dto/           # 강의 관련 DTO
│   │   │   ├── entity/        # 강의 엔티티
│   │   │   ├── repository/    # 강의 데이터 접근
│   │   │   ├── service/       # 강의 비즈니스 로직
│   │   │   └── metrics/       # 강의 조회 메트릭 수집
│   │   │
│   │   ├── enrollment/
│   │   │   ├── controller/    # 수강신청 API
│   │   │   ├── entity/        # 수강신청 엔티티
│   │   │   ├── repository/    # 수강신청 데이터 접근
│   │   │   └── service/       # 수강신청 비즈니스 로직
│   │   │
│   │   ├── student/
│   │   │   ├── entity/        # 학생 엔티티
│   │   │   └── repository/    # 학생 데이터 접근
│   │   │
│   │   ├── security/          # Spring Security 설정
│   │   │
│   │   └── global/
│   │       ├── exception/     # 공통 예외 처리
│   │       └── metrics/       # 공통 메트릭 수집
│   │
│   └── resources/
│       ├── templates/         # Thymeleaf 템플릿
│       └── db/
│           ├── migration/     # Flyway 마이그레이션
│           └── seed/          # 초기 데이터
│
├── Dockerfile                 # 멀티 스테이지 Docker 빌드
├── docker-compose.yml         # 로컬 개발 환경
└── .github/workflows/         # GitHub Actions CI/CD
```


<br>

---

## 8. 팀원

| 이름  | 담당                                           |
|-----|----------------------------------------------|
| 공통  | 페이지 기획, API 설계, DB 설계                        |
| 정지찬 | 백엔드 개발, 모니터링 (Prometheus, Grafana), k6 부하테스트 |
| 이유은 | 백엔드 개발, 프론트엔드 연결, Terraform IaC, KEDA        |
| 김우연 | 백엔드 개발, Kubernetes 매니페스트                     |
| 배성민 | Terraform IaC, Kubernetes 매니페스트              |
| 전지훈 | CI/CD GitOps (GitHub Actions, ArgoCD)        |
| 이윤범 | CI/CD GitOps (GitHub Actions, ArgoCD)        |
| 유준영 | 모니터링 (Prometheus, Grafana), k6 부하테스트         |

<br>


---

## 9. 환경 변수

| 변수 | 설명 |
|--------|--------|
| `DB_URL` | AWS RDS(MySQL) 접속 URL |
| `DB_USERNAME` | 데이터베이스 계정 |
| `DB_PASSWORD` | 데이터베이스 비밀번호 |
| `JWT_SECRET` | JWT 토큰 서명 키 |
| `SPRING_PROFILES_ACTIVE` | 실행 환경(dev, prod) |

<br>

### Secret 관리

민감 정보(DB 계정, 비밀번호, JWT Secret)는 Git 저장소에 저장하지 않습니다.

- Local 환경: `application-local.yml`
- Kubernetes 환경: `Secret` 또는 `External Secret`
- AWS Secrets Manager 연동
- Pod 실행 시 환경 변수로 주입

```text
AWS Secrets Manager
        ↓
External Secret
        ↓
Kubernetes Secret
        ↓
Spring Boot Pod
```

