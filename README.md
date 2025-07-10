# Content Storage Service

A Spring Boot 3.5.3 file storage service with Google Drive-style tagging, built for multi-pod Kubernetes deployment.

## Features

- **Two-phase uploads**: Reserve → Upload → Finalize pattern for resilient multi-pod operation
- **Object storage**: MinIO S3-compatible backend
- **Metadata storage**: MongoDB with optimized indexes
- **Tags (Drive-style)**: 0-5 tags per file, case-insensitive, open vocabulary
- **Visibility control**: Public/Private files with secure download links
- **State management**: PENDING → READY file state machine
- **Janitor service**: Automatic cleanup of orphaned uploads

## Quick Start with Docker

### Prerequisites
- Docker and Docker Compose
- MongoDB instance
- MinIO instance

### Build and Run

```bash
# Clone the repository
git clone <repository-url>
cd content-storage

# Build the Docker image
docker build -t content-storage:latest .

# Run with environment variables
docker run -p 8080:8080 \
  -e SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/content-storage \
  -e MINIO_ENDPOINT=http://localhost:9000 \
  -e MINIO_ACCESS_KEY=minioadmin \
  -e MINIO_SECRET_KEY=minioadmin \
  content-storage:latest
```

### Using Docker Compose (Development)

```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - mongodb
      - minio
    environment:
      SPRING_DATA_MONGODB_URI: mongodb://mongodb:27017/content-storage
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin

  mongodb:
    image: mongo:7.0
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

volumes:
  mongodb_data:
  minio_data:
```

## API Documentation

### File Operations

#### Upload File
```bash
POST /api/files
Content-Type: multipart/form-data
X-User-Id: user123

# Form data:
file: <binary-data>
meta: {
  "fileName": "document.pdf",
  "visibility": "PUBLIC",
  "tags": ["invoice", "tax", "2024"]
}
```

#### List Files
```bash
# List user's files
GET /api/files
X-User-Id: user123

# List public files
GET /api/files/public

# Filter by tag (case-insensitive)
GET /api/files/public?tag=invoice

# Pagination and sorting
GET /api/files?page=0&size=10&sort=uploadTs,desc
```

#### Download File
```bash
GET /api/files/{fileId}/download
X-User-Id: user123

# Public file download (no auth required)
GET /api/files/{fileId}/download/{secret}
```

#### Rename File
```bash
PUT /api/files/{fileId}/rename
X-User-Id: user123
Content-Type: application/json

{
  "newFileName": "renamed-document.pdf"
}
```

#### Delete File
```bash
DELETE /api/files/{fileId}
X-User-Id: user123
```

### Tags (Drive-style)

#### Get All Tags
```bash
GET /api/tags
```

**Features:**
- **0-5 tags per file**: Maximum 5 tags, minimum 0
- **Case-insensitive**: "Invoice", "INVOICE", "invoice" are treated as same tag
- **Open vocabulary**: No predefined tag list, tags created on-demand
- **Validation**: Tags must match `^[a-zA-Z0-9_-]{1,30}$`
- **Normalization**: Tags stored in lowercase canonical form
- **Deduplication**: Duplicate tags (case-insensitive) automatically removed
- **404 on unknown**: Filtering by non-existent tag returns 404

## Development

### Local Development Setup

```bash
# Start dependencies
docker-compose up -d mongodb minio

# Run tests with coverage
./gradlew clean test jacocoTestReport

# Run the application
./gradlew bootRun
```

### Testing

The project includes comprehensive test suites:

- **Unit tests**: Individual component testing
- **Integration tests**: Full Spring context with Testcontainers
- **Coverage target**: ≥85% line / 80% branch coverage (JaCoCo)

```bash
# Run all tests
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport

# Check coverage thresholds
./gradlew jacocoTestCoverageVerification
```

### Build

```bash
# Build JAR
./gradlew bootJar

# Build Docker image
docker build -t content-storage:latest .
```

## CI/CD Pipeline

The project includes a GitHub Actions workflow that:

1. **Runs tests** with JaCoCo coverage on every PR/push
2. **Builds and pushes** Docker images to GitHub Container Registry on main branch
3. **Caches** Gradle dependencies for faster builds
4. **Reports coverage** to Codecov (optional)

### GitHub Container Registry

Images are automatically published to:
```
ghcr.io/<github-username>/content-storage:latest
ghcr.io/<github-username>/content-storage:main-<sha>
```

### Using Published Images

```bash
# Pull and run latest image
docker pull ghcr.io/<github-username>/content-storage:latest
docker run -p 8080:8080 ghcr.io/<github-username>/content-storage:latest
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATA_MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/content-storage` |
| `MINIO_ENDPOINT` | MinIO server URL | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | MinIO access key | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO secret key | `minioadmin` |
| `MINIO_BUCKET_NAME` | S3 bucket name | `content-storage` |
| `FILE_JANITOR_CLEANUP_INTERVAL` | Cleanup interval | `PT1H` (1 hour) |

### Health Checks

The application exposes health check endpoints:

```bash
# Application health
GET /actuator/health

# Detailed health with dependencies
GET /actuator/health/mongo
GET /actuator/health/minio
```

## Architecture

### Two-Phase Upload Process
1. **Reserve**: `POST /api/files` → Returns upload URL + metadata
2. **Upload**: Client uploads file to presigned S3 URL
3. **Finalize**: Automatic transition from PENDING → READY state

### Database Indexes

**MongoDB Collections:**
- `files`: Compound indexes on owner+name, tags, visibility, state+uploadTs
- `tags`: Unique index on name (case-insensitive)

**Optimizations:**
- Partial index for READY files only
- Compound indexes for efficient filtering and sorting
- Automatic index creation on startup

## License

[MIT License](LICENSE)