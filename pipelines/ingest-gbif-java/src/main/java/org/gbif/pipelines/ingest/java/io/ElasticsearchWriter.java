package org.gbif.pipelines.ingest.java.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;

import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class ElasticsearchWriter<T> {

  private String[] esHosts;
  private boolean useSyncMode;
  private Function<T, IndexRequest> indexRequestFn;
  private ExecutorService executor;
  private Collection<T> records;
  private long esMaxBatchSize;
  private long esMaxBatchSizeBytes;
  @Builder.Default
  private int backPressure = 10;

  @SneakyThrows
  public void write() {

    // Create ES client and extra function
    HttpHost[] hosts = Arrays.stream(esHosts).map(HttpHost::create).toArray(HttpHost[]::new);
    try (RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(hosts))) {

      List<CompletableFuture<Void>> futures = new ArrayList<>();
      AtomicInteger backPressureCounter = new AtomicInteger(0);

      Queue<BulkRequest> requests = new LinkedBlockingQueue<>();
      requests.add(new BulkRequest().timeout(TimeValue.timeValueMinutes(5L)));

      Consumer<T> addIndexRequestFn = br -> Optional.ofNullable(requests.peek())
          .ifPresent(req -> req.add(indexRequestFn.apply(br)));

      Consumer<BulkRequest> clientBulkFn = br -> {
        try {
          log.info("Push ES request, number of actions - {}", br.numberOfActions());
          backPressureCounter.incrementAndGet();
          BulkResponse bulk = client.bulk(br, RequestOptions.DEFAULT);
          backPressureCounter.decrementAndGet();
          if (bulk.hasFailures()) {
            log.error(bulk.buildFailureMessage());
            throw new ElasticsearchException(bulk.buildFailureMessage());
          }
        } catch (IOException ex) {
          log.error(ex.getMessage(), ex);
          throw new ElasticsearchException(ex.getMessage(), ex);
        }
      };

      Runnable pushIntoEsFn = () -> Optional.ofNullable(requests.poll())
          .filter(req -> req.numberOfActions() > 0)
          .ifPresent(req -> {
            if (useSyncMode) {
              clientBulkFn.accept(req);
            } else {
              futures.add(CompletableFuture.runAsync(() -> clientBulkFn.accept(req), executor));
            }
          });

      // Push requests into ES
      for (T t : records) {

        while (backPressureCounter.get() > backPressure) {
          log.info("Back pressure barrier: too many requests wainting...");
          TimeUnit.MILLISECONDS.sleep(200L);
        }

        BulkRequest peek = requests.peek();
        addIndexRequestFn.accept(t);
        if (peek == null
            || peek.numberOfActions() < esMaxBatchSize - 1
            || peek.estimatedSizeInBytes() < esMaxBatchSizeBytes) {
          pushIntoEsFn.run();
          requests.add(new BulkRequest().timeout(TimeValue.timeValueMinutes(5L)));
        }
      }

      // Final push
      pushIntoEsFn.run();

      // Wait for all futures
      if (!useSyncMode) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
      }
    }

  }

}
