package com.github.jhspetersson;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.IntResult2;

@JCStressTest
@Outcome(id = "1, 2", expect = Expect.ACCEPTABLE)
@Outcome(id = "2, 1", expect = Expect.ACCEPTABLE)
@Outcome(expect = Expect.FORBIDDEN)
@State
public class ReenterabilityTest {

    private int value;

    private final static Object key = new Object();

    private final static EntityLocker<Object> locker = new EntityLocker<>();

    @Actor
    public void actor1(IntResult2 r) {
        locker.lock(key);
        locker.lock(key);
        locker.lock(key);

        r.r1 = ++value;

        locker.unlock(key);
        locker.unlock(key);
        locker.unlock(key);
    }

    @Actor
    public void actor2(IntResult2 r) {
        locker.lock(key);
        locker.lock(key);
        locker.lock(key);

        r.r2 = ++value;

        locker.unlock(key);
        locker.unlock(key);
        locker.unlock(key);
    }
}
