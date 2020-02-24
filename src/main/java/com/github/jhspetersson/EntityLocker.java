package com.github.jhspetersson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * EntityLocker provides a thin wrapper around reentrant locks for exclusive access
 * to some entity set addressed by a given key type.
 *
 * T can be of any type, but for the sake of deadlock safety we strongly suggest,
 * that it implements Comparable, and that locks on multiple keys are obtained
 * in a predefined sorted order.
 *
 * @param <T> type of the key
 */
public class EntityLocker<T> {

    // Keeps lock for a given key
    private final Map<T, Lock> locks = new HashMap<>();

    // Keeps count of nonglobal locks per thread. Guarded by a main locks map
    private final Map<Long, Integer> lockCountPerThread = new HashMap<>();

    // After reaching this count of nonglobal locks thread's lock escalates to a global one
    private final int lockEscalationThreshold;

    private final ReadWriteLock globalLock = new ReentrantReadWriteLock();

    private final static int DEFAULT_LOCK_ESCALATION_THRESHOLD = Integer.MAX_VALUE;

    public EntityLocker() {
        this(DEFAULT_LOCK_ESCALATION_THRESHOLD);
    }

    /**
     * @param lockEscalationThreshold should be not less than 2, use exclusive locking otherwise
     */
    public EntityLocker(int lockEscalationThreshold) {
        if (lockEscalationThreshold < 2) {
            throw new IllegalArgumentException("Lock escalation threshold is too small");
        }

        this.lockEscalationThreshold = lockEscalationThreshold;
    }

    /**
     * Acquire lock on the entity addressed by the key.
     */
    public void lock(T key) {
        getLock(key).lock();
    }

    /**
     * Acquire lock on the entity addressed by the key with a timeout.
     * Method's semantics mirror the same of the Lock::tryLock method.
     */
    public boolean tryLock(T key, long time, TimeUnit unit) throws InterruptedException {
        return getLock(key).tryLock(time, unit);
    }

    private Lock getLock(T key) {
        globalLock.readLock().lock();

        long threadId = Thread.currentThread().getId();

        synchronized (locks) {
            int count = lockCountPerThread.getOrDefault(threadId, 0);

            Lock lock = locks.get(key);

            if (lock == null || !isHeldByCurrentThread(lock)) {
                lock = count <= lockEscalationThreshold
                        ? (lock == null ? new ReentrantLock() : lock)
                        : globalLock.writeLock();

                locks.put(key, lock);

                if (lock != globalLock.writeLock()) {
                    lockCountPerThread.put(threadId, ++count);
                }
            }

            return lock;
        }
    }

    private static boolean isHeldByCurrentThread(Lock lock) {
        if (lock instanceof ReentrantLock) {
            return ((ReentrantLock) lock).isHeldByCurrentThread();
        } else if (lock instanceof ReentrantReadWriteLock.WriteLock) {
            return ((ReentrantReadWriteLock.WriteLock) lock).isHeldByCurrentThread();
        }

        throw new IllegalArgumentException("Unrecognized lock type");
    }

    /**
     * Acquire exclusive lock on complete entity set.
     *
     * This method will block until all per-key operations are done.
     */
    public void lockExclusive() {
        globalLock.writeLock().lock();
    }

    /**
     * Release exclusive lock on complete entity set.
     */
    public void unlockExclusive() {

        // prevent internal state from inflation with possibly unneeded entries
        synchronized (locks) {
            locks.clear();
            lockCountPerThread.clear();
        }

        globalLock.writeLock().unlock();
    }

    /**
     * Release the lock.
     */
    public void unlock(T key) {
        synchronized (locks) {
            Lock lock = locks.get(key);
            if (lock != null) {
                lock.unlock();

                if (lock == globalLock.writeLock()) {
                    locks.remove(key);
                } else {
                    long threadId = Thread.currentThread().getId();
                    lockCountPerThread.merge(threadId, -1, Integer::sum);
                }

                globalLock.readLock().unlock();
            }
        }
    }
}
