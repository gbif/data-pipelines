package org.gbif.pipelines.crawler.indexing;

import com.google.common.util.concurrent.AbstractIdleService;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.gbif.common.messaging.DefaultMessagePublisher;
import org.gbif.common.messaging.MessageListener;
import org.gbif.common.messaging.api.MessagePublisher;
import org.gbif.common.messaging.api.messages.PipelinesInterpretedMessage;
import org.gbif.pipelines.common.configs.StepConfiguration;
import org.gbif.registry.ws.client.pipelines.PipelinesHistoryClient;
import org.gbif.validator.ws.client.ValidationWsClient;
import org.gbif.ws.client.ClientBuilder;

/**
 * A service which listens to the {@link
 * org.gbif.common.messaging.api.messages.PipelinesInterpretedMessage }
 */
@Slf4j
public class IndexingService extends AbstractIdleService {

  private final IndexingConfiguration config;
  private MessageListener listener;
  private MessagePublisher publisher;
  private CuratorFramework curator;
  private CloseableHttpClient httpClient;
  private ExecutorService executor;

  public IndexingService(IndexingConfiguration config) {
    this.config = config;
  }

  @Override
  protected void startUp() throws Exception {
    log.info("Started pipelines-index-dataset service with parameters : {}", config);
    // Prefetch is one, since this is a long-running process.
    StepConfiguration c = config.stepConfig;
    listener = new MessageListener(c.messaging.getConnectionParameters(), 1);
    publisher = new DefaultMessagePublisher(c.messaging.getConnectionParameters());
    curator = c.zooKeeper.getCuratorFramework();
    executor =
        config.standaloneNumberThreads == null
            ? null
            : Executors.newFixedThreadPool(config.standaloneNumberThreads);
    httpClient =
        HttpClients.custom()
            .setDefaultRequestConfig(
                RequestConfig.custom().setConnectTimeout(60_000).setSocketTimeout(60_000).build())
            .build();

    PipelinesHistoryClient historyClient =
        new ClientBuilder()
            .withUrl(config.stepConfig.registry.wsUrl)
            .withCredentials(config.stepConfig.registry.user, config.stepConfig.registry.password)
            .withFormEncoder()
            .build(PipelinesHistoryClient.class);

    ValidationWsClient validationClient =
        new ClientBuilder()
            .withUrl(config.stepConfig.registry.wsUrl)
            .withCredentials(config.stepConfig.registry.user, config.stepConfig.registry.password)
            .withFormEncoder()
            .build(ValidationWsClient.class);

    IndexingCallback callback =
        new IndexingCallback(
            config, publisher, curator, httpClient, historyClient, validationClient, executor);

    String routingKey;
    PipelinesInterpretedMessage pm =
        new PipelinesInterpretedMessage().setValidator(config.validatorOnly);
    if (config.validatorOnly && config.validatorListenAllMq) {
      routingKey = pm.getRoutingKey() + ".*";
    } else {
      routingKey = pm.setRunner(config.processRunner).getRoutingKey();
    }
    listener.listen(c.queueName, routingKey, c.poolSize, callback);
  }

  @Override
  protected void shutDown() {
    listener.close();
    publisher.close();
    curator.close();
    executor.shutdown();
    try {
      httpClient.close();
    } catch (IOException e) {
      log.error("Can't close ES http client connection");
    }
    log.info("Stopping pipelines-index-dataset service");
  }
}
