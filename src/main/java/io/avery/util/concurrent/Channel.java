package io.avery.util.concurrent;

public class Channel<In, Out> {
    private final PingPong<In, Out>.Pong pong;
    
    public Channel(PingPong<In, Out>.Pong pong) {
        this.pong = pong;
    }
    
    public In yield(Out item) throws InterruptedException {
        return pong.yield(item);
    }
}
