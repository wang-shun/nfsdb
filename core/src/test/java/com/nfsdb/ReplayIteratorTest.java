/*******************************************************************************
 *  _  _ ___ ___     _ _
 * | \| | __/ __| __| | |__
 * | .` | _|\__ \/ _` | '_ \
 * |_|\_|_| |___/\__,_|_.__/
 *
 * Copyright (c) 2014-2016. The NFSdb project and its contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.nfsdb;

import com.nfsdb.iter.ReplayIterator;
import com.nfsdb.iter.TimeSource;
import com.nfsdb.iter.clock.Clock;
import com.nfsdb.iter.clock.MilliClock;
import com.nfsdb.model.Quote;
import com.nfsdb.test.tools.AbstractTest;
import com.nfsdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ReplayIteratorTest extends AbstractTest {
    @Test
    public void testJournalIteratorReplay() throws Exception {
        JournalWriter<Quote> w = factory.writer(Quote.class);
        TestUtils.generateQuoteData(w, 1000);

        ReplayIterator<Quote> replay = new ReplayIterator<>(JournalIterators.bufferedIterator(w), 0.00000001f);
        TestUtils.assertEquals(JournalIterators.bufferedIterator(w), replay);
    }

    @Test
    public void testJournalReplay() throws Exception {
        JournalWriter<Quote> w = factory.writer(Quote.class);
        TestUtils.generateQuoteData(w, 1000);

        ReplayIterator<Quote> replay = new ReplayIterator<>(w, 0.00000001f);
        TestUtils.assertEquals(JournalIterators.bufferedIterator(w), replay);
    }

    @Test
    public void testReplay() throws Exception {

        Clock clock = MilliClock.INSTANCE;

        final long t = clock.getTicks();

        List<Entity> entities = new ArrayList<Entity>() {{
            add(new Entity(t));
            add(new Entity(t + 50));
            add(new Entity(t + 65));
            add(new Entity(t + 250));
            add(new Entity(t + 349));
        }};

        long expected[] = deltas(entities);

        ReplayIterator<Entity> replay = new ReplayIterator<>(entities.iterator(), clock, 1f, new TimeSource<Entity>() {
            @Override
            public long getTicks(Entity object) {
                return object.timestamp;
            }
        });

        List<Entity> list = new ArrayList<>();

        for (Entity e : replay) {
            if (e.timestamp > 0) {
                e.timestamp = clock.getTicks();
            }
            list.add(e);
        }

        long actual[] = deltas(list);

        Assert.assertArrayEquals(expected, actual);

    }

    private long[] deltas(List<Entity> entities) {
        long last = 0;
        long result[] = new long[entities.size()];

        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            result[i] = last == 0 ? 0 : e.timestamp - last;
            last = e.timestamp;
        }

        return result;
    }

    private static class Entity {
        private long timestamp;

        private Entity(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
