package com.tongji.counter.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterKeysEpochTest {

    @Test
    void epochAndRebuildLockKeysAreEntityScoped() {
        assertEquals("cnt:epoch:knowpost:99", CounterKeys.epochKey("knowpost", "99"));
        assertEquals("lock:sds-rebuild:knowpost:99", CounterKeys.rebuildLockKey("knowpost", "99"));
    }
}
