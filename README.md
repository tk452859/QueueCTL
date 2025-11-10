QueueCTL - Production-Grade Background Job Queue System
🚀 A high-performance, persistent job queue system with worker pools, exponential backoff retries, and dead letter queue management.

📋 Features
✅ CLI-based job queue with simple command interface

✅ Multiple worker processes with configurable pool size

✅ Exponential backoff retry mechanism (2^attempts seconds)

✅ Dead Letter Queue for permanently failed jobs

✅ Persistent storage across application restarts

✅ Full job lifecycle management (pending → processing → completed/dead)

✅ Thread-safe concurrent operations

✅ Real command execution with output capture

🛠️ Tech Stack
Pure Java - No external dependencies, minimal footprint

Java Concurrency - ExecutorService, ConcurrentHashMap for thread safety

Java NIO - File-based persistence

Process Builder - System command execution

🚀 Setup Instructions
Prerequisites
Java 17 or higher

Maven (optional) or direct Java compiler

Quick Start
bash
# Clone the repository
git clone https://github.com/tk452859/QueueCTL.git
cd QueueCTL/src

# Compile the application
javac QueueCTL.java

# Start using QueueCTL
java QueueCTL enqueue "echo 'Hello World'"
💻 Usage Examples
Basic Job Management
bash
# Enqueue jobs
java QueueCTL enqueue "echo 'Processing started'"
java QueueCTL enqueue "sleep 2"
java QueueCTL enqueue "dir"

# List all jobs
java QueueCTL list

# List jobs by state
java QueueCTL list --state pending
java QueueCTL list --state completed
Worker Management
bash
# Start 3 workers
java QueueCTL worker start --count 3

# Check system status
java QueueCTL status

# Stop workers gracefully
java QueueCTL worker stop
Monitoring and DLQ
bash
# View system status with job counts
java QueueCTL status

# Check Dead Letter Queue for failed jobs
java QueueCTL dlq list
Example Output
text
📋 Jobs:
ID                                   STATE       ATTEMPTS  COMMAND
--------------------------------------------------------------------------------
550e8400-e29b-41d4-a716-446655440000 pending     0         echo 'Hello World'
750e8400-e29b-41d4-a716-446655440001 completed   0         dir

🚀 QueueCTL Status
==================
Workers: ✅ RUNNING (2 active)

Job Counts:
  pending     : 1
  processing  : 0
  completed   : 3
  failed      : 1
  dead        : 2
🏗️ Architecture Overview
Job Lifecycle
text
pending → processing → completed
                    → failed → (retry with backoff) → dead
Core Components
Job Specification
Each job contains:

json
{
  "id": "unique-uuid",
  "command": "echo 'Hello World'",
  "state": "pending|processing|completed|failed|dead",
  "attempts": 0,
  "maxRetries": 3,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
Worker System
Fixed-size thread pool using ExecutorService

Concurrent job polling with thread-safe operations

Graceful shutdown - completes current jobs before exit

Retry Mechanism
Exponential backoff: delay = 2^attempts seconds

Automatic retries for failed commands

Configurable max attempts (default: 3)

Persistence Layer
JSON file storage (jobs.json)

Automatic save/load on application start/stop

Atomic operations to prevent data corruption

🤔 Assumptions & Trade-offs
Design Decisions
Pure Java Implementation
Choice: Used pure Java instead of Spring Boot
Reason: Better performance for CLI tools, faster startup, smaller footprint
Trade-off: Manual dependency management vs auto-configuration

File-based Persistence
Choice: JSON file storage instead of database
Reason: Simplicity, no external dependencies, suitable for single-node deployment
Trade-off: Limited scalability vs immediate persistence needs

ConcurrentHashMap Storage
Choice: In-memory storage with file persistence
Reason: High performance, thread safety, simple implementation
Trade-off: Memory limits vs distributed storage complexity

Simple CLI Interface
Choice: Direct Java execution vs packaged executable
Reason: Cross-platform compatibility, no native compilation needed
Trade-off: Slightly longer command vs single binary

Simplifications
No JSON input parsing - Uses direct command strings instead of JSON objects

Hardcoded configuration - maxRetries and baseDelay are compile-time constants

Single-node architecture - Designed for single machine deployment

Basic error handling - Focus on core functionality over edge cases

🧪 Testing Instructions
Manual Verification
Test 1: Basic Job Lifecycle
bash
# Clean start
cd src
del jobs.json 2>nul
javac QueueCTL.java

# Test successful job
java QueueCTL enqueue "echo 'Test Success'"
java QueueCTL worker start --count 1
timeout 3
java QueueCTL list
# Should show: state=completed
Test 2: Retry Mechanism
bash
# Test failed job with retries
java QueueCTL enqueue "invalid-command"
java QueueCTL worker start --count 1
timeout 10
java QueueCTL list
java QueueCTL dlq list
# Should show: attempts=3, state=dead in DLQ
Test 3: Persistence
bash
# Test data survives restart
java QueueCTL enqueue "echo 'Persistent job'"
java QueueCTL list
# Restart application
java QueueCTL list
# Jobs should still be present
Test 4: Concurrent Processing
bash
# Test multiple workers
java QueueCTL enqueue "timeout 2 >nul"
java QueueCTL enqueue "timeout 2 >nul"
java QueueCTL enqueue "timeout 2 >nul"
java QueueCTL worker start --count 3
java QueueCTL status
# Should show multiple active workers
Expected Test Outcomes
✅ Successful jobs complete with state=completed

✅ Failed jobs retry with exponential delays (2s → 4s → 8s)

✅ Permanent failures move to DLQ after max retries

✅ Multiple workers process jobs concurrently

✅ Job data persists across application restarts

✅ System recovers from invalid commands gracefully

Verification Script
Create test.bat (Windows) or test.sh (Linux) for automated testing:

batch
@echo off
cd src
javac QueueCTL.java
java QueueCTL enqueue "echo 'Automated Test'"
java QueueCTL worker start --count 2
timeout 5
java QueueCTL list
java QueueCTL worker stop
📁 Project Structure
text
QueueCTL/
├── src/
│   └── QueueCTL.java          # Main application
├── jobs.json                  # Persistent job storage (auto-generated)
└── README.md                  # This documentation


🎯 System Requirements
Java 17+

Windows/Linux/macOS

Basic system commands available (echo, dir, etc.)

📞 Support
For issues or questions, please open an issue on the GitHub repository.

QueueCTL - Built with pure Java for performance and reliability. 🚀

