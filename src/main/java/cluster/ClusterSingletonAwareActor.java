package cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.SingletonActor;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.slf4j.Logger;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


class ClusterSingletonAwareActor extends AbstractBehavior<ClusterSingletonAwareActor.Message> {
  private final ActorRef<Message> clusterSingletonProxy;
  private final ActorRef<HttpServer.Statistics> httpServerActor;
  private Map<Integer, Integer> singletonStatistics = new HashMap<>();
  private static final Duration tickInterval = Duration.ofMillis(500);

  static Behavior<Message> create(ActorRef<HttpServer.Statistics> httpServerActor) {
    return Behaviors.setup(actorContext ->
        Behaviors.withTimers(timer -> new ClusterSingletonAwareActor(actorContext, timer, httpServerActor)));
  }

  ClusterSingletonAwareActor(ActorContext<Message> actorContext, TimerScheduler<Message> timers, ActorRef<HttpServer.Statistics> httpServerActor) {
    super(actorContext);
    this.httpServerActor = httpServerActor;

    clusterSingletonProxy = ClusterSingleton.get(actorContext.getSystem())
        .init(SingletonActor.of(ClusterSingletonActor.create(), ClusterSingletonActor.class.getSimpleName()));
    timers.startTimerAtFixedRate(Tick.Instance, tickInterval);
  }

  @Override
  public Receive<Message> createReceive() {
    return newReceiveBuilder()
        .onMessage(Tick.class, notUsed -> onTick())
        .onMessage(Pong.class, this::onPong)
        .build();
  }

  private Behavior<Message> onTick() {
    clusterSingletonProxy.tell(new Ping(getContext().getSelf(), System.nanoTime()));
    return Behaviors.same();
  }

  private Behavior<Message> onPong(Pong pong) {
    log().info("<--{}", pong);
    singletonStatistics = pong.singletonStatistics;
    httpServerActor.tell(new HttpServer.SingletonStatistics(singletonStatistics));
    return Behaviors.same();
  }

  interface Message extends Serializable {
  }

  public static class Ping implements Message, Serializable {
    public final ActorRef<Message> replyTo;
    public final long start;

    @JsonCreator
    public Ping(ActorRef<Message> replyTo, long start) {
      this.replyTo = replyTo;
      this.start = start;
    }

    @Override
    public String toString() {
      return String.format("%s[%s]", getClass().getSimpleName(), replyTo.path());
    }
  }

  public static class Pong implements Message, Serializable {
    public final ActorRef<Message> replyFrom;
    public final long pingStart;
    public final Map<Integer, Integer> singletonStatistics;

    @JsonCreator
    public Pong(ActorRef<Message> replyFrom, long pingStart, Map<Integer, Integer> singletonStatistics) {
      this.replyFrom = replyFrom;
      this.pingStart = pingStart;
      this.singletonStatistics = singletonStatistics;
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %,dns, %s]", getClass().getSimpleName(), replyFrom.path(), System.nanoTime() - pingStart, singletonStatistics);
    }
  }

  enum Tick implements Message {
    Instance
  }

  private Logger log() {
    return getContext().getLog();
  }
}
