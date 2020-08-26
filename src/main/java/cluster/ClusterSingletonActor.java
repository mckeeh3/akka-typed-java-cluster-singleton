package cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static cluster.ClusterSingletonAwareActor.Message;

class ClusterSingletonActor extends AbstractBehavior<Message> {
  private final SingletonStatistics singletonStatistics = new SingletonStatistics();

  static Behavior<Message> create() {
    return Behaviors.setup(ClusterSingletonActor::new);
  }

  ClusterSingletonActor(ActorContext<Message> actorContext) {
    super(actorContext);
  }

  @Override
  public Receive<Message> createReceive() {
    return newReceiveBuilder()
        .onMessage(ClusterSingletonAwareActor.Ping.class, this::onPing)
        .build();
  }

  private Behavior<Message> onPing(ClusterSingletonAwareActor.Ping ping) {
    log().info("<=={}", ping);
    singletonStatistics.ping(ping);
    ping.replyTo.tell(new ClusterSingletonAwareActor.Pong(getContext().getSelf(), ping.start, Collections.unmodifiableMap(singletonStatistics.nodePings)));
    return Behaviors.same();
  }

  static class SingletonStatistics {
    Map<Integer, Integer> nodePings = new HashMap<>();

    SingletonStatistics() {
      IntStream.rangeClosed(2551, 2559).forEach(p -> nodePings.put(p, 0));
    }

    void ping(ClusterSingletonAwareActor.Ping ping) {
      if (ping.port >= 2551 && ping.port <= 2559) {
        nodePings.put(ping.port, 1 + nodePings.getOrDefault(ping.port, 0));
      }
    }
  }

  private Logger log() {
    return getContext().getLog();
  }
}
