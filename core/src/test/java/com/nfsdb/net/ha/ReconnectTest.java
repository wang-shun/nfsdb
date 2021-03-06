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

package com.nfsdb.net.ha;

import com.nfsdb.Journal;
import com.nfsdb.JournalWriter;
import com.nfsdb.ex.JournalException;
import com.nfsdb.ex.JournalRuntimeException;
import com.nfsdb.model.Quote;
import com.nfsdb.net.ha.config.ClientConfig;
import com.nfsdb.net.ha.config.ServerConfig;
import com.nfsdb.store.TxListener;
import com.nfsdb.test.tools.AbstractTest;
import com.nfsdb.test.tools.TestUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ReconnectTest extends AbstractTest {
    private JournalClient client;

    @Before
    public void setUp() {
        client = new JournalClient(
                new ClientConfig("localhost") {{
                    getReconnectPolicy().setLoginRetryCount(3);
                    getReconnectPolicy().setRetryCount(5);
                    getReconnectPolicy().setSleepBetweenRetriesMillis(TimeUnit.SECONDS.toMillis(1));
                }}
                , factory
        );
    }

    @Test
    public void testServerRestart() throws Exception {
        int size = 100000;
        JournalWriter<Quote> remote = factory.writer(Quote.class, "remote", 2 * size);

        // start server #1
        JournalServer server = newServer();
        server.publish(remote);
        server.start();

        // subscribe client, waiting for complete set of data
        // when data arrives client triggers latch

        final CountDownLatch latch = new CountDownLatch(1);
        final Journal<Quote> local = factory.reader(Quote.class, "local");
        client.subscribe(Quote.class, "remote", "local", 2 * size, new TxListener() {
            @Override
            public void onCommit() {
                try {
                    if (local.refresh() && local.size() == 200000) {
                        latch.countDown();
                    }
                } catch (JournalException e) {
                    throw new JournalRuntimeException(e);
                }
            }

            @Override
            public void onError() {

            }
        });


        client.start();

        // generate first batch
        TestUtils.generateQuoteData(remote, size, System.currentTimeMillis(), 1);
        remote.commit();

        // stop server
        server.halt();

        // start server #2
        server = newServer();
        server.publish(remote);
        server.start();

        // generate second batch
        TestUtils.generateQuoteData(remote, size, System.currentTimeMillis() + 2 * size, 1);
        remote.commit();

        // wait for client to get full set
        latch.await();

        // stop client and server
        client.halt();
        server.halt();

        // assert client state
        TestUtils.assertDataEquals(remote, local);
    }

    private JournalServer newServer() {
        return new JournalServer(new ServerConfig() {{
            setHeartbeatFrequency(TimeUnit.MILLISECONDS.toMillis(100));
            setEnableMultiCast(false);
        }}, factory);
    }
}
