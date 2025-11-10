import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QueueCTL {
    private static final String DATA_FILE = "jobs.json";
    private static final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private static ExecutorService workerPool;
    private static volatile boolean running = false;
    private static final AtomicInteger activeWorkers = new AtomicInteger(0);

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java QueueCTL <command> [options]");
            System.out.println("Commands: enqueue, worker start, worker stop, list, status, dlq list");
            return;
        }

        loadJobs();

        switch (args[0]) {
            case "enqueue":
                if (args.length < 2) {
                    System.out.println("Usage: java QueueCTL enqueue \"command\"");
                    return;
                }
                enqueueJob(args[1]);
                break;
            case "worker":
                if (args.length < 2) {
                    System.out.println("Usage: java QueueCTL worker <start|stop> [--count N]");
                    return;
                }
                if ("start".equals(args[1])) {
                    int count = 2;
                    if (args.length > 3 && "--count".equals(args[2])) {
                        count = Integer.parseInt(args[3]);
                    }
                    startWorkers(count);
                } else if ("stop".equals(args[1])) {
                    stopWorkers();
                }
                break;
            case "list":
                String stateFilter = null;
                if (args.length > 2 && "--state".equals(args[1])) {
                    stateFilter = args[2];
                }
                listJobs(stateFilter);
                break;
            case "status":
                showStatus();
                break;
            case "dlq":
                if (args.length > 1 && "list".equals(args[1])) {
                    listDLQ();
                }
                break;
            default:
                System.out.println("Unknown command: " + args[0]);
        }

        saveJobs();
    }

    static class Job {
        String id;
        String command;
        String state; // "pending", "processing", "completed", "failed", "dead"
        int attempts;
        int maxRetries = 3;
        Instant createdAt;
        Instant updatedAt;
        Instant nextRetryAt;

        Job(String command) {
            this.id = UUID.randomUUID().toString();
            this.command = command;
            this.state = "pending";
            this.attempts = 0;
            this.createdAt = Instant.now();
            this.updatedAt = Instant.now();
        }

        boolean canRetry() {
            return attempts < maxRetries && "failed".equals(state);
        }

        void incrementAttempts() {
            attempts++;
            updatedAt = Instant.now();
        }
    }

    private static void enqueueJob(String command) {
        Job job = new Job(command);
        jobs.put(job.id, job);
        System.out.println("✅ Enqueued job: " + job.id + " - " + command);
    }

    private static void startWorkers(int count) {
        if (running) {
            System.out.println("❌ Workers are already running");
            return;
        }

        workerPool = Executors.newFixedThreadPool(count);
        running = true;

        for (int i = 0; i < count; i++) {
            workerPool.submit(() -> {
                activeWorkers.incrementAndGet();
                while (running) {
                    try {
                        Job job = findPendingJob();
                        if (job != null) {
                            processJob(job);
                        }
                        Thread.sleep(1000); // Check for new jobs every second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("Worker error: " + e.getMessage());
                    }
                }
                activeWorkers.decrementAndGet();
            });
        }

        System.out.println("👷 Started " + count + " workers");
    }

    private static void stopWorkers() {
        if (!running) {
            System.out.println("❌ No workers are running");
            return;
        }

        running = false;
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
            }
        }
        System.out.println("🛑 Stopping workers...");
    }

    private static Job findPendingJob() {
        return jobs.values().stream()
                .filter(job -> "pending".equals(job.state) ||
                        ("failed".equals(job.state) && job.canRetry() &&
                                (job.nextRetryAt == null || Instant.now().isAfter(job.nextRetryAt))))
                .findFirst()
                .orElse(null);
    }

    private static void processJob(Job job) {
        job.state = "processing";
        job.updatedAt = Instant.now();
        saveJobs();

        System.out.println("🔄 Processing job: " + job.id + " - " + job.command);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd.exe", "/c", job.command);
            } else {
                pb.command("bash", "-c", job.command);
            }

            Process process = pb.start();

            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + job.id + "] OUT: " + line);
            }

            // Read errors
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                System.err.println("[" + job.id + "] ERR: " + line);
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                job.state = "completed";
                System.out.println("✅ Completed job: " + job.id);
            } else {
                job.incrementAttempts();
                if (job.attempts >= job.maxRetries) {
                    job.state = "dead";
                    System.out.println("💀 Job moved to DLQ: " + job.id);
                } else {
                    job.state = "failed";
                    // Exponential backoff: 2^attempts seconds
                    long delaySeconds = (long) Math.pow(2, job.attempts);
                    job.nextRetryAt = Instant.now().plusSeconds(delaySeconds);
                    System.out.println("❌ Job failed, will retry: " + job.id +
                            " (attempt " + job.attempts + ", retry in " + delaySeconds + "s)");
                }
            }

        } catch (Exception e) {
            job.incrementAttempts();
            job.state = "failed";
            System.err.println("❌ Error processing job " + job.id + ": " + e.getMessage());
        }

        job.updatedAt = Instant.now();
        saveJobs();
    }

    private static void listJobs(String stateFilter) {
        System.out.println("📋 Jobs:");
        System.out.println("ID                                   STATE       ATTEMPTS  COMMAND");
        System.out.println("--------------------------------------------------------------------------------");

        jobs.values().stream()
                .filter(job -> stateFilter == null || job.state.equals(stateFilter))
                .forEach(job -> {
                    System.out.printf("%-36s %-12s %-9d %s%n",
                            job.id, job.state, job.attempts, job.command);
                });
    }

    private static void listDLQ() {
        System.out.println("💀 Dead Letter Queue:");
        jobs.values().stream()
                .filter(job -> "dead".equals(job.state))
                .forEach(job -> {
                    System.out.printf("%s - %s (failed after %d attempts)%n",
                            job.id, job.command, job.attempts);
                });
    }

    private static void showStatus() {
        System.out.println("🚀 QueueCTL Status");
        System.out.println("==================");
        System.out.println("Workers: " + (running ? "✅ RUNNING (" + activeWorkers.get() + " active)" : "❌ STOPPED"));
        System.out.println();
        System.out.println("Job Counts:");

        Map<String, Long> counts = jobs.values().stream()
                .collect(Collectors.groupingBy(job -> job.state, Collectors.counting()));

        for (String state : Arrays.asList("pending", "processing", "completed", "failed", "dead")) {
            System.out.printf("  %-12s: %d%n", state, counts.getOrDefault(state, 0L));
        }
    }

    private static void loadJobs() {
        try {
            if (!Files.exists(Paths.get(DATA_FILE))) return;

            String content = new String(Files.readAllBytes(Paths.get(DATA_FILE)));
            // Simple JSON parsing (for demo - in real project use Jackson)
            if (content.trim().isEmpty()) return;

            // Basic JSON parsing - in real implementation use proper JSON library
            String[] jobStrings = content.split("\\},\\s*\\{");
            for (String jobStr : jobStrings) {
                // Simplified parsing - actual implementation would use Jackson
                if (jobStr.contains("\"command\"")) {
                    // Extract command and create basic job
                    String command = extractValue(jobStr, "command");
                    if (command != null) {
                        Job job = new Job(command);
                        jobs.put(job.id, job);
                    }
                }
            }
            System.out.println("📂 Loaded " + jobs.size() + " jobs from storage");
        } catch (Exception e) {
            System.err.println("Error loading jobs: " + e.getMessage());
        }
    }

    private static void saveJobs() {
        try {
            // Simple JSON serialization (for demo)
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Job job : jobs.values()) {
                if (!first) sb.append(",");
                sb.append(String.format(
                        "{\"id\":\"%s\",\"command\":\"%s\",\"state\":\"%s\",\"attempts\":%d,\"maxRetries\":%d}",
                        job.id, job.command.replace("\"", "\\\""), job.state, job.attempts, job.maxRetries));
                first = false;
            }
            sb.append("]");

            Files.write(Paths.get(DATA_FILE), sb.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error saving jobs: " + e.getMessage());
        }
    }

    private static String extractValue(String json, String key) {
        // Simple JSON value extraction
        String pattern = "\"" + key + "\":\"(.*?)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}