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

package com.nfsdb.net.ha.mcast;

import com.nfsdb.ex.JournalNetworkException;
import com.nfsdb.ex.JournalRuntimeException;
import com.nfsdb.log.Log;
import com.nfsdb.log.LogFactory;
import com.nfsdb.misc.ByteBuffers;
import com.nfsdb.net.ha.config.DatagramChannelWrapper;
import com.nfsdb.net.ha.config.ServerConfig;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

@SuppressFBWarnings({"EXS_EXCEPTION_SOFTENING_NO_CONSTRAINTS"})
public abstract class AbstractOnDemandSender {

    private static final Log LOG = LogFactory.getLog(AbstractOnDemandSender.class);
    final int instance;
    private final ServerConfig serverConfig;
    private final int inMessageCode;
    private final int outMessageCode;
    private final String threadName;
    private Selector selector;
    private volatile boolean running = false;
    private volatile boolean selecting = false;
    private CountDownLatch latch;

    AbstractOnDemandSender(ServerConfig serverConfig, int inMessageCode, int outMessageCode, int instance) {
        this.serverConfig = serverConfig;
        this.inMessageCode = inMessageCode;
        this.outMessageCode = outMessageCode;
        this.threadName = "nfsdb-mcast-sender-" + instance;
        this.instance = instance;
    }

    @SuppressFBWarnings({"MDM_THREAD_YIELD"})
    public void halt() {
        if (running) {
            while (!selecting) {
                Thread.yield();
            }
            selector.wakeup();
            running = false;
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new JournalRuntimeException(e);
            }
        }
    }

    public void start() {
        if (!running) {
            running = true;
            latch = new CountDownLatch(1);
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    start0();
                }
            });
            thread.setName(threadName);
            thread.start();
        }
    }

    protected abstract void prepareBuffer(ByteBuffer buf) throws JournalNetworkException;

    private void start0() {
        try {
            try (DatagramChannelWrapper dcw = serverConfig.openDatagramChannel(instance)) {
                DatagramChannel dc = dcw.getChannel();
                LOG.info().$("Sending to ").$(dcw.getGroup()).$(" on [").$(dc.getOption(StandardSocketOptions.IP_MULTICAST_IF).getName()).$(']').$();

                selector = Selector.open();
                selecting = true;
                dc.configureBlocking(false);
                dc.register(selector, SelectionKey.OP_READ);
                ByteBuffer buf = ByteBuffer.allocateDirect(4096);
                try {
                    while (running) {
                        int updated = selector.select();
                        if (!running) {
                            break;
                        }
                        if (updated > 0) {
                            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                            while (iter.hasNext()) {
                                SelectionKey sk = iter.next();
                                iter.remove();
                                DatagramChannel ch = (DatagramChannel) sk.channel();
                                buf.clear();
                                SocketAddress sa = ch.receive(buf);
                                if (sa != null) {
                                    buf.flip();
                                    if (buf.remaining() >= 4 && inMessageCode == buf.getInt(0)) {
                                        LOG.debug().$("Sending server information [").$(inMessageCode).$("] to [").$(sa).$(']').$();
                                        buf.clear();
                                        buf.putInt(outMessageCode);
                                        prepareBuffer(buf);
                                        dc.send(buf, dcw.getGroup());
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    ByteBuffers.release(buf);
                }
            }
        } catch (Throwable e) {
            LOG.error().$("Multicast sender crashed").$(e).$();
        } finally {
            latch.countDown();
        }
    }
}
