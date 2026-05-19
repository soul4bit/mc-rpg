package ru.mcrpg.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class ParallelIo {

    private static final int MAX_PARALLELISM = 8;

    private ParallelIo() {
    }

    static <T> List<T> run(String threadNamePrefix, List<? extends Callable<T>> tasks) throws IOException {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<T>();
        }
        if (tasks.size() == 1) {
            return runSingle(tasks.get(0));
        }

        int parallelism = Math.min(tasks.size(), defaultParallelism());
        ExecutorService executor = Executors.newFixedThreadPool(
            parallelism,
            new NamedThreadFactory(threadNamePrefix)
        );
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean completed = false;

        try {
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(task));
            }

            List<T> results = new ArrayList<T>(tasks.size());
            for (Future<T> future : futures) {
                results.add(get(future));
            }
            completed = true;
            return results;
        } finally {
            if (completed) {
                executor.shutdown();
            } else {
                for (Future<T> future : futures) {
                    future.cancel(true);
                }
                executor.shutdownNow();
            }
        }
    }

    private static int defaultParallelism() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(MAX_PARALLELISM, processors));
    }

    private static <T> List<T> runSingle(Callable<T> task) throws IOException {
        List<T> result = new ArrayList<T>(1);
        try {
            result.add(task.call());
            return result;
        } catch (Exception exception) {
            throw asIOException(exception);
        }
    }

    private static <T> T get(Future<T> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Parallel I/O operation was interrupted.", exception);
        } catch (ExecutionException exception) {
            throw asIOException(exception.getCause());
        }
    }

    private static IOException asIOException(Throwable throwable) {
        if (throwable instanceof IOException) {
            return (IOException) throwable;
        }
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        return new IOException("Parallel I/O operation failed.", throwable);
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix == null || prefix.trim().isEmpty() ? "parallel-io" : prefix.trim();
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
