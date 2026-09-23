# File Processing Pipeline — Dynatrace Business Flow POC

A three-microservice file processing pipeline built with **Java 17 + Spring Boot 3.2**, designed to demonstrate **Dynatrace Business Flow** with **zero application-level Dynatrace code**. Business events are captured automatically via **OneAgent Capture Rules**.

## Architecture

```
User → Upload Service (:3001) → Processing Service (:3002) → Validation Service (:3003)
         [Spring Boot]            [Spring Boot]                 [Spring Boot]
                                            ↑
                                    Dynatrace OneAgent
                              (intercepts HTTP traffic and
                             captures business events via
                           rules configured in Dynatrace UI)
```

## Key Point

> **There is NO Dynatrace SDK, dependency, or API call anywhere in this code.**
> All business event capture is configured in the Dynatrace UI using OneAgent Capture Rules.
> This satisfies the banking company requirement of not sending business events from the application.

## Prerequisites

- **Java 17+** installed (`java -version`)
- **Maven 3.8+** installed (`mvn -version`) — or use the Maven Wrapper (`mvnw`) included

## Quick Start

### 1. Build all services

```powershell
# Terminal 1 - Upload Service
cd upload-service
mvnw.cmd spring-boot:run

# Terminal 2 - Processing Service  
cd processing-service
mvnw.cmd spring-boot:run

# Terminal 3 - Validation Service
cd validation-service
mvnw.cmd spring-boot:run
```

Or if you have Maven installed globally:

```powershell
cd upload-service && mvn spring-boot:run
cd processing-service && mvn spring-boot:run
cd validation-service && mvn spring-boot:run
```

### 2. Open the UI

Open **http://localhost:3001** in your browser.

### 3. Test the pipeline

1. Drag & drop a file (any file — CSV, PDF, image, etc.)
2. Optionally enter an Account ID
3. Click **"Upload & Process"**
4. Watch the pipeline progress: Upload → Processing → Validation
5. Check the **Activity Log** for detailed JSON responses
6. Use **Check Status** with the File ID

## Services

| Service | Port | Description |
|---------|------|-------------|
| **Upload Service** | 3001 | Accepts file uploads via multipart/form-data, stores metadata, triggers processing |
| **Processing Service** | 3002 | Simulates file processing (parsing, extraction, analysis) |
| **Validation Service** | 3003 | Validates processing results (integrity, compliance, data quality checks) |

## API Endpoints

### Upload Service (`:3001`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/files/upload` | Upload a file (multipart/form-data) |
| `GET` | `/api/files/{fileId}` | Get file status |
| `GET` | `/api/files` | List all files |
| `GET` | `/health` | Health check |

### Processing Service (`:3002`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/process` | Process a file (called by Upload Service) |
| `GET` | `/api/process/{processId}` | Get processing details |
| `GET` | `/health` | Health check |

### Validation Service (`:3003`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/validate` | Validate processing results (called by Processing Service) |
| `GET` | `/api/validate/{validationId}` | Get validation details |
| `GET` | `/api/validate/status/{fileId}` | End-to-end file status |
| `GET` | `/health` | Health check |

## Dynatrace Integration

See **[DYNATRACE_SETUP_GUIDE.md](./DYNATRACE_SETUP_GUIDE.md)** for complete step-by-step instructions on:

1. Getting a free Dynatrace trial
2. Installing OneAgent on Windows
3. Enabling Business Events for Java
4. Configuring capture rules for each endpoint
5. Setting up Business Flow visualization
6. DQL queries to explore captured events
7. Troubleshooting
