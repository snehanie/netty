package io.netty.channel.socket.oio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelType;

import java.io.IOException;
import java.util.Queue;

abstract class AbstractOioMessageChannel extends AbstractOioChannel {

    protected AbstractOioMessageChannel(
            Channel parent, Integer id, ChannelBufferHolder<?> outboundBuffer) {
        super(parent, id, outboundBuffer);
    }

    @Override
    public ChannelType type() {
        return ChannelType.MESSAGE;
    }

    @Override
    protected Unsafe newUnsafe() {
        return new OioMessageUnsafe();
    }

    private class OioMessageUnsafe extends AbstractOioUnsafe {
        @Override
        public void read() {
            assert eventLoop().inEventLoop();

            final ChannelPipeline pipeline = pipeline();
            final Queue<Object> msgBuf = pipeline.inboundMessageBuffer();
            boolean closed = false;
            boolean read = false;
            try {
                int localReadAmount = doReadMessages(msgBuf);
                if (localReadAmount > 0) {
                    read = true;
                } else if (localReadAmount < 0) {
                    closed = true;
                }
            } catch (Throwable t) {
                if (read) {
                    read = false;
                    pipeline.fireInboundBufferUpdated();
                }
                pipeline().fireExceptionCaught(t);
                if (t instanceof IOException) {
                    close(voidFuture());
                }
            } finally {
                if (read) {
                    pipeline.fireInboundBufferUpdated();
                }
                if (closed && isOpen()) {
                    close(voidFuture());
                }
            }
        }
    }

    @Override
    protected void doFlush(ChannelBufferHolder<Object> buf) throws Exception {
        flushMessageBuf(buf.messageBuffer());
    }

    private void flushMessageBuf(Queue<Object> buf) throws Exception {
        while (!buf.isEmpty()) {
            doWriteMessages(buf);
        }
    }

    protected abstract int doReadMessages(Queue<Object> buf) throws Exception;
    protected abstract int doWriteMessages(Queue<Object> buf) throws Exception;
}
