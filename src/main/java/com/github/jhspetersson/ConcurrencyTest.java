package com.github.jhspetersson;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.IntResult3;

@JCStressTest
@Outcome(id = "1, 2, 3", expect = Expect.ACCEPTABLE)
@Outcome(id = "1, 3, 2", expect = Expect.ACCEPTABLE)
@Outcome(id = "2, 1, 3", expect = Expect.ACCEPTABLE)
@Outcome(id = "2, 3, 1", expect = Expect.ACCEPTABLE)
@Outcome(id = "3, 1, 2", expect = Expect.ACCEPTABLE)
@Outcome(id = "3, 2, 1", expect = Expect.ACCEPTABLE)
@Outcome(expect = Expect.FORBIDDEN)
@State
public class ConcurrencyTest {

    private int value;

    private final static Object key = new Object();

    private final static EntityLocker<Object> locker = new EntityLocker<>();

    @Actor
    public void actor1(IntResult3 r) {
        locker.lock(key);

        r.r1 = ++value;

        locker.unlock(key);
    }

    @Actor
    public void actor2(IntResult3 r) {
        locker.lock(key);

        r.r2 = ++value;

        locker.unlock(key);
    }

    @Actor
    public void actor3(IntResult3 r) {
        locker.lock(key);

        r.r3 = ++value;

        locker.unlock(key);
    }
}
