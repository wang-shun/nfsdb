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

package com.nfsdb.net;

import com.nfsdb.ex.JournalNetworkException;
import com.nfsdb.ex.JournalRuntimeException;
import com.nfsdb.log.Log;
import com.nfsdb.log.LogFactory;
import com.nfsdb.misc.ByteBuffers;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

public class SecureSocketChannel implements WrappedByteChannel {

    private static final Log LOG = LogFactory.getLog(SecureSocketChannel.class);

    private final SocketChannel socketChannel;
    private final SSLEngine engine;
    private final ByteBuffer inBuf;
    private final ByteBuffer outBuf;
    private final int sslDataLimit;
    private final boolean client;
    private final ByteBuffer swapBuf;
    private boolean inData = false;
    private SSLEngineResult.HandshakeStatus handshakeStatus = SSLEngineResult.HandshakeStatus.NEED_WRAP;
    private boolean fillInBuf = true;

    public SecureSocketChannel(SocketChannel socketChannel, SslConfig sslConfig) {
        this.socketChannel = socketChannel;
        SSLContext sslc = sslConfig.getSslContext();
        this.engine = sslc.createSSLEngine();
        this.engine.setEnableSessionCreation(true);
        this.engine.setUseClientMode(sslConfig.isClient());
        this.engine.setNeedClientAuth(sslConfig.isRequireClientAuth());
        this.client = sslConfig.isClient();
        SSLSession session = engine.getSession();
        this.sslDataLimit = session.getApplicationBufferSize();
        inBuf = ByteBuffer.allocateDirect(session.getPacketBufferSize()).order(ByteOrder.LITTLE_ENDIAN);
        outBuf = ByteBuffer.allocateDirect(session.getPacketBufferSize()).order(ByteOrder.LITTLE_ENDIAN);
        swapBuf = ByteBuffer.allocateDirect(sslDataLimit * 2).order(ByteOrder.LITTLE_ENDIAN);
    }

    public SocketChannel getChannel() {
        return socketChannel;
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        socketChannel.close();
        ByteBuffers.release(inBuf);
        ByteBuffers.release(outBuf);
        ByteBuffers.release(swapBuf);
        if (engine.isOutboundDone()) {
            engine.closeOutbound();
        }

        while (!engine.isInboundDone()) {
            try {
                engine.closeInbound();
            } catch (SSLException ignored) {
                // ignore
            }
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {

        if (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED) {
            handshake();
        }

        int p = dst.position();

        while (true) {

            int limit = dst.remaining();
            if (limit == 0) {
                break;
            }

            // check if anything is remaining in swapBuf
            if (swapBuf.hasRemaining()) {

                ByteBuffers.copy(swapBuf, dst);

            } else {

                if (fillInBuf) {
                    // read from channel
                    inBuf.clear();
                    int result = socketChannel.read(inBuf);

                    if (result == -1) {
                        throw new IOException("Did not expect -1 from socket channel");
                    }

                    if (result == 0) {
                        throw new IOException("Blocking connection must not return 0");
                    }

                    inBuf.flip();
                }

                // dst is larger than minimum?
                if (limit < sslDataLimit) {
                    // no, dst is small, use swap
                    swapBuf.clear();
                    fillInBuf = unwrap(swapBuf);
                    swapBuf.flip();
                    ByteBuffers.copy(swapBuf, dst);
                } else {
                    fillInBuf = unwrap(dst);
                }
            }
        }
        return dst.position() - p;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED) {
            handshake();
        }

        int count = src.remaining();
        while (src.hasRemaining()) {
            outBuf.clear();
            SSLEngineResult result = engine.wrap(src, outBuf);
            if (result.getStatus() != SSLEngineResult.Status.OK) {
                throw new IOException("Expected OK, got: " + result.getStatus());
            }
            outBuf.flip();
            try {
                ByteBuffers.copy(outBuf, socketChannel);
            } catch (JournalNetworkException e) {
                throw new IOException(e);
            }
        }
        return count;
    }

    private void closureOnException() throws IOException {
        swapBuf.position(0);
        swapBuf.limit(0);
        SSLEngineResult sslEngineResult;
        do {
            outBuf.clear();
            sslEngineResult = engine.wrap(swapBuf, outBuf);
            outBuf.flip();
            socketChannel.write(outBuf);
            if (sslEngineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
                break;
            }
        } while (sslEngineResult.getStatus() != SSLEngineResult.Status.CLOSED && !engine.isInboundDone());
        engine.closeOutbound();
    }

    private void handshake() throws IOException {

        if (handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
            return;
        }

        engine.beginHandshake();

        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED) {
            switch (handshakeStatus) {
                case NOT_HANDSHAKING:
                    throw new IOException("Not handshaking");
                case NEED_WRAP:
                    outBuf.clear();
                    swapBuf.clear();
                    try {
                        handshakeStatus = engine.wrap(swapBuf, outBuf).getHandshakeStatus();
                    } catch (SSLException e) {
                        LOG.error().$("Server SSL handshake failed: ").$(e.getMessage()).$();
                        closureOnException();
                        throw e;
                    }
                    outBuf.flip();
                    socketChannel.write(outBuf);
                    break;
                case NEED_UNWRAP:

                    if (!inData || !inBuf.hasRemaining()) {
                        inBuf.clear();
                        socketChannel.read(inBuf);
                        inBuf.flip();
                        inData = true;
                    }

                    try {
                        SSLEngineResult res = engine.unwrap(inBuf, swapBuf);
                        handshakeStatus = res.getHandshakeStatus();
                        switch (res.getStatus()) {
                            case BUFFER_UNDERFLOW:
                                inBuf.compact();
                                socketChannel.read(inBuf);
                                inBuf.flip();
                                break;
                            case BUFFER_OVERFLOW:
                                throw new IOException("Did not expect OVERFLOW here");
                            case OK:
                                break;
                            case CLOSED:
                                throw new IOException("Did not expect CLOSED");
                            default:
                                break;
                        }
                    } catch (SSLException e) {
                        LOG.error().$("Client SSL handshake failed: ").$(e.getMessage()).$();
                        throw e;
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                default:
                    throw new JournalRuntimeException("Unknown handshake status: %s", handshakeStatus);
            }
        }

        inBuf.clear();
        // make sure swapBuf starts by having remaining() == false
        swapBuf.position(swapBuf.limit());

        LOG.info().$("Handshake SSL complete: ").$(client ? "CLIENT" : "SERVER").$();
    }

    private boolean unwrap(ByteBuffer dst) throws IOException {
        while (inBuf.hasRemaining()) {
            SSLEngineResult.Status status = engine.unwrap(inBuf, dst).getStatus();
            switch (status) {
                case BUFFER_UNDERFLOW:
                    inBuf.compact();
                    socketChannel.read(inBuf);
                    inBuf.flip();
                    break;
                case BUFFER_OVERFLOW:
                    return false;
                case OK:
                    break;
                case CLOSED:
                    throw new IOException("Did not expect CLOSED");
                default:
                    break;
            }
        }
        return true;
    }
}
