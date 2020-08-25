package cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.management.javadsl.AkkaManagement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;

class Main {
  static Behavior<Void> create() {
    return Behaviors.setup(context -> {
      bootstrap(context);

      return Behaviors.receive(Void.class).onSignal(Terminated.class, signal -> Behaviors.stopped()).build();
    });
  }

  private static void bootstrap(final ActorContext<Void> context) {
    context.spawn(ClusterListenerActor.create(), "clusterListener");

    final ActorRef<HttpServer.PingStatistics> httpServerActorRef = context.spawn(HttpServerActor.create(), HttpServerActor.class.getSimpleName());

    context.spawn(ClusterAwareActor.create(httpServerActorRef), ClusterAwareActor.class.getSimpleName());
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException("Akka node port is required.");
    }
    final String port = Arrays.asList(args).get(0);
    final ActorSystem<?> actorSystem = ActorSystem.create(Main.create(), "cluster", setupClusterNodeConfig(port));
    AkkaManagement.get(actorSystem.classicSystem()).start();
    HttpServer.start(actorSystem);
  }

  private static Config setupClusterNodeConfig(String port) {
    final String hostname = "127.0.0.1";
    return ConfigFactory
        .parseString(String.format("akka.remote.artery.canonical.hostname = \"%s\"%n", hostname)
            + String.format("akka.remote.artery.canonical.port=%s%n", port)
            + String.format("akka.management.http.hostname = \"%s\"%n", "127.0.0.1")
            + String.format("akka.management.http.port=%s%n", port.replace("255", "855"))
            + String.format("akka.management.http.route-providers-read-only = %s%n", "false")
            + String.format("akka.remote.artery.advanced.tcp.outbound-client-hostname = %s%n", hostname))
        .withFallback(ConfigFactory.load());
  }
}
