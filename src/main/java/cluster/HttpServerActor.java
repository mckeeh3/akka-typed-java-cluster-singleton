package cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import org.slf4j.Logger;

class HttpServerActor {
  private final ActorContext<HttpServer.Statistics> context;
  private final HttpServer httpServer;

  HttpServerActor(ActorContext<HttpServer.Statistics> context) {
    this.context = context;

    httpServer = HttpServer.start(context.getSystem());
  }

  static Behavior<HttpServer.Statistics> create() {
    return Behaviors.setup(context -> new HttpServerActor(context).behavior());
  }

  private Behavior<HttpServer.Statistics> behavior() {
    return Behaviors.receive(HttpServer.Statistics.class)
        .onMessage(HttpServer.ClusterAwareStatistics.class, this::onPingStatistics)
        .onMessage(HttpServer.SingletonStatistics.class, this::omSingletonStatistics)
        .build();
  }

  private Behavior<HttpServer.Statistics> onPingStatistics(HttpServer.ClusterAwareStatistics clusterAwareStatistics) {
    log().info("Cluster aware statistics {} {}", clusterAwareStatistics.totalPings, clusterAwareStatistics.nodePings);
    httpServer.load(clusterAwareStatistics);
    return Behaviors.same();
  }

  private Behavior<HttpServer.Statistics> omSingletonStatistics(HttpServer.SingletonStatistics singletonStatistics) {
    log().info("Singleton statistics {}", singletonStatistics.nodePings);
    httpServer.load(singletonStatistics);
    return Behaviors.same();
  }

  private Logger log() {
    return context.getLog();
  }
}
