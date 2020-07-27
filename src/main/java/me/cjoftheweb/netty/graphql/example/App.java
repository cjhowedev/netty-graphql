package me.cjoftheweb.netty.graphql.example;

import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLCodeRegistry.newCodeRegistry;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

import graphql.GraphQL;
import graphql.Scalars;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.reactivex.rxjava3.core.Flowable;
import java.util.concurrent.TimeUnit;
import me.cjoftheweb.netty.graphql.GraphQLBasicServerInitializer;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class App {
  private static final Logger LOG = LoggerFactory.getLogger(App.class);

  public static void main(final String[] args) {
    final GraphQLSchema graphQLSchema =
        GraphQLSchema.newSchema()
            .query(
                newObject()
                    .name("Query")
                    .field(newFieldDefinition().name("hello").type(Scalars.GraphQLString).build())
                    .build())
            .subscription(
                newObject()
                    .name("Subscription")
                    .field(newFieldDefinition().name("test").type(Scalars.GraphQLInt).build())
                    .build())
            .codeRegistry(
                newCodeRegistry()
                    .dataFetcher(coordinates("Query", "hello"), new StaticDataFetcher("world"))
                    .dataFetcher(
                        coordinates("Subscription", "test"),
                        (DataFetcher<Publisher<Long>>)
                            environment -> Flowable.interval(1, TimeUnit.SECONDS))
                    .build())
            .build();

    final EventLoopGroup bossGroup = new NioEventLoopGroup();
    final EventLoopGroup workerGroup = new NioEventLoopGroup();
    try {
      final ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .option(ChannelOption.SO_BACKLOG, 128)
          .childHandler(
              new GraphQLBasicServerInitializer(
                  req ->
                      GraphQL.newGraphQL(graphQLSchema)
                          .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy())
                          .build()))
          .childOption(ChannelOption.SO_KEEPALIVE, true);

      final ChannelFuture f = b.bind(4000).sync();

      f.channel().closeFuture().sync();
    } catch (final Throwable t) {
      LOG.error("Failed to bootstrap server", t);
    } finally {
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
    }
  }
}
