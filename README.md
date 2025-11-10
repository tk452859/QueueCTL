# QueueCTL - Background Job Queue System

## Features Demonstrated ✅
- ✅ CLI-based job queue (`java QueueCTL enqueue "command"`)
- ✅ Multiple worker processes (`worker start --count 3`) 
- ✅ Exponential backoff retry mechanism (2s → 4s → 8s)
- ✅ Dead Letter Queue for failed jobs (after max retries)
- ✅ Persistent storage across restarts (jobs.json)
- ✅ Full job lifecycle (pending → processing → completed/dead)

## Quick Start
```bash
# Compile
javac QueueCTL.java

# Enqueue jobs
java QueueCTL enqueue "echo 'Hello World'"
java QueueCTL enqueue "dir"

# Start workers
java QueueCTL worker start --count 2

# Monitor
java QueueCTL status
java QueueCTL list
java QueueCTL dlq list

All jobs are stored in jobs.json with full specification:

{
  "id": "uuid",
  "command": "echo 'test'", 
  "state": "completed|failed|dead",
  "attempts": 0,
  "maxRetries": 3
}
