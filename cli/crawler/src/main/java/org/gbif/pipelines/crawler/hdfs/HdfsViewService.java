package org.gbif.pipelines.crawler.hdfs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.gbif.api.service.pipelines.PipelinesHistoryService;
import org.gbif.common.messaging.DefaultMessagePublisher;
import org.gbif.common.messaging.MessageListener;
import org.gbif.common.messaging.api.MessagePublisher;
import org.gbif.pipelines.common.configs.StepConfiguration;
import org.gbif.registry.ws.client.pipelines.PipelinesHistoryClient;

import org.apache.curator.framework.CuratorFramework;

import com.google.common.util.concurrent.AbstractIdleService;
import lombok.extern.slf4j.Slf4j;

/**
 * A service which listens to the {@link org.gbif.common.messaging.api.messages.PipelinesInterpretedMessage }
 */
@Slf4j
public class HdfsViewService extends AbstractIdleService {

  private final HdfsViewConfiguration config;
  private MessageListener listener;
  private MessagePublisher publisher;
  private CuratorFramework curator;
  private ExecutorService executor;

  public HdfsViewService(HdfsViewConfiguration config) {
    this.config = config;
  }

  @Override
  protected void startUp() throws Exception {
    log.info("Started pipelines-hdfs-view service with parameters : {}", config);
    // Prefetch is one, since this is a long-running process.
    StepConfiguration c = config.stepConfig;
    listener = new MessageListener(c.messaging.getConnectionParameters(), 1);
    publisher = new DefaultMessagePublisher(c.messaging.getConnectionParameters());
    curator = c.zooKeeper.getCuratorFramework();
    executor = config.standaloneNumberThreads == null ? null : Executors.newFixedThreadPool(config.standaloneNumberThreads);
    PipelinesHistoryService historyWsClient = c.registry.newRegistryClientFactory().newInstance(PipelinesHistoryClient.class);

    HdfsViewCallback callback = new HdfsViewCallback(config, publisher, curator, historyWsClient, executor);
    listener.listen(c.queueName, c.poolSize, callback);
  }

  @Override
  protected void shutDown() {
    listener.close();
    publisher.close();
    curator.close();
    executor.shutdown();
    log.info("Stopping pipelines-hdfs-view service");
  }
}
