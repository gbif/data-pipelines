package org.gbif.pipelines.labs.io;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import org.apache.beam.sdk.annotations.Experimental;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.BoundedSource;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.apache.beam.sdk.transforms.display.DisplayData;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Transforms for reading and writing data from/to Elasticsearch.
 *
 * <h3>Reading from Elasticsearch</h3>
 *
 * <p>{@link ElasticsearchIO#read ElasticsearchIO.read()} returns a bounded {@link PCollection
 * PCollection&lt;String&gt;} representing JSON documents.
 *
 * <p>To configure the {@link ElasticsearchIO#read}, you have to provide a connection configuration
 * containing the HTTP address of the instances, an index name and a type. The following example
 * illustrates options for configuring the source:
 *
 * <pre>{@code
 * pipeline.apply(ElasticsearchIO.read().withConnectionConfiguration(
 *    ElasticsearchIO.ConnectionConfiguration.create("http://host:9200", "my-index", "my-type")
 * )
 *
 * }</pre>
 *
 * <p>The connection configuration also accepts optional configuration: {@code withUsername()} and
 * {@code withPassword()}.
 *
 * <p>You can also specify a query on the {@code read()} using {@code withQuery()}.
 *
 * <h3>Writing to Elasticsearch</h3>
 *
 * <p>To write documents to Elasticsearch, use {@link ElasticsearchIO#write
 * ElasticsearchIO.write()}, which writes JSON documents from a {@link PCollection
 * PCollection&lt;String&gt;} (which can be bounded or unbounded).
 *
 * <p>To configure {@link ElasticsearchIO#write ElasticsearchIO.write()}, similar to the read, you
 * have to provide a connection configuration. For instance:
 *
 * <pre>{@code
 * pipeline
 *   .apply(...)
 *   .apply(ElasticsearchIO.write().withConnectionConfiguration(
 *      ElasticsearchIO.ConnectionConfiguration.create("http://host:9200", "my-index", "my-type")
 *   )
 *
 * }</pre>
 *
 * <p>Optionally, you can provide {@code withBatchSize()} and {@code withBatchSizeBytes()} to
 * specify the size of the write batch in number of documents or in bytes.
 *
 * <p>Optionally, you can provide an {@link ElasticsearchIO.Write.FieldValueExtractFn} using {@code
 * withIdFn()} that will be run to extract the id value out of the provided document rather than
 * using the document id auto-generated by Elasticsearch.
 *
 * <p>Optionally, you can provide {@link ElasticsearchIO.Write.FieldValueExtractFn} using {@code
 * withIndexFn()} or {@code withTypeFn()} to enable per-document routing to the target Elasticsearch
 * index and type.
 *
 * <p>Optionally, you can provide {withUsePartialUpdate()} to issue partial updates to Elasticsearch
 * instead of inserts. When provided {@code withIdFn()} must also be used.
 */
@Experimental(Experimental.Kind.SOURCE_SINK)
public class PatchedElasticsearchIO {

  private static final ObjectMapper mapper = new ObjectMapper();

  public static Read read() {
    // default scrollKeepalive = 5m as a majorant for un-predictable time between 2 start/read calls
    // default batchSize to 100 as recommended by ES dev team as a safe value when dealing
    // with big documents and still a good compromise for performances
    return new AutoValue_PatchedElasticsearchIO_Read.Builder().setScrollKeepalive("5m").setBatchSize(100L).build();
  }

  public static Write write() {
    return new AutoValue_PatchedElasticsearchIO_Write.Builder()
      // advised default starting batch size in ES docs
      .setMaxBatchSize(1000L)
      // advised default starting batch size in ES docs
      .setMaxBatchSizeBytes(5L * 1024L * 1024L).setUsePartialUpdate(false) // default is document upsert
      .build();
  }

  @VisibleForTesting
  static JsonNode parseResponse(Response response) throws IOException {
    return mapper.readValue(response.getEntity().getContent(), JsonNode.class);
  }

  static void checkForErrors(Response response, int backendVersion) throws IOException {
    JsonNode searchResult = parseResponse(response);
    boolean errors = searchResult.path("errors").asBoolean();
    if (errors) {
      StringBuilder errorMessages =
        new StringBuilder("Error writing to Elasticsearch, some elements could not be inserted:");
      JsonNode items = searchResult.path("items");
      //some items present in bulk might have errors, concatenate error messages
      for (JsonNode item : items) {
        String errorRootName = "";
        if (backendVersion == 2) {
          errorRootName = "create";
        } else if (backendVersion == 5) {
          errorRootName = "index";
        }
        JsonNode errorRoot = item.path(errorRootName);
        JsonNode error = errorRoot.get("error");
        if (error != null) {
          String type = error.path("type").asText();
          String reason = error.path("reason").asText();
          String docId = errorRoot.path("_id").asText();
          errorMessages.append(String.format("%nDocument id %s: %s (%s)", docId, reason, type));
          JsonNode causedBy = error.get("caused_by");
          if (causedBy != null) {
            String cbReason = causedBy.path("reason").asText();
            String cbType = causedBy.path("type").asText();
            errorMessages.append(String.format("%nCaused by: %s (%s)", cbReason, cbType));
          }
        }
      }
      throw new IOException(errorMessages.toString());
    }
  }

  static int getBackendVersion(ConnectionConfiguration connectionConfiguration) {
    try (RestClient restClient = connectionConfiguration.createClient()) {
      Response response = restClient.performRequest("GET", "");
      JsonNode jsonNode = parseResponse(response);
      int backendVersion = Integer.parseInt(jsonNode.path("version").path("number").asText().substring(0, 1));
      checkArgument((backendVersion == 2 || backendVersion == 5),
                    "The Elasticsearch version to connect to is %s.x. "
                    + "This version of the ElasticsearchIO is only compatible with "
                    + "Elasticsearch v5.x and v2.x",
                    backendVersion);
      return backendVersion;

    } catch (IOException e) {
      throw (new IllegalArgumentException("Cannot get Elasticsearch version"));
    }
  }

  private PatchedElasticsearchIO() {}

  /**
   * A POJO describing a connection configuration to Elasticsearch.
   */
  @AutoValue
  public abstract static class ConnectionConfiguration implements Serializable {

    /**
     * Creates a new Elasticsearch connection configuration.
     *
     * @param addresses list of addresses of Elasticsearch nodes
     * @param index     the index toward which the requests will be issued
     * @param type      the document type toward which the requests will be issued
     *
     * @return the connection configuration object
     */
    public static ConnectionConfiguration create(String[] addresses, String index, String type) {
      checkArgument(addresses != null, "addresses can not be null");
      checkArgument(addresses.length > 0, "addresses can not be empty");
      checkArgument(index != null, "index can not be null");
      checkArgument(type != null, "type can not be null");
      ConnectionConfiguration connectionConfiguration =
        new AutoValue_PatchedElasticsearchIO_ConnectionConfiguration.Builder().setAddresses(Arrays.asList(addresses))
          .setIndex(index)
          .setType(type)
          .build();
      return connectionConfiguration;
    }

    public abstract List<String> getAddresses();

    @Nullable
    public abstract String getUsername();

    @Nullable
    public abstract String getPassword();

    @Nullable
    public abstract String getKeystorePath();

    @Nullable
    public abstract String getKeystorePassword();

    public abstract String getIndex();

    public abstract String getType();

    /**
     * If Elasticsearch authentication is enabled, provide the username.
     *
     * @param username the username used to authenticate to Elasticsearch
     */
    public ConnectionConfiguration withUsername(String username) {
      checkArgument(username != null, "username can not be null");
      checkArgument(!username.isEmpty(), "username can not be empty");
      return builder().setUsername(username).build();
    }

    /**
     * If Elasticsearch authentication is enabled, provide the password.
     *
     * @param password the password used to authenticate to Elasticsearch
     */
    public ConnectionConfiguration withPassword(String password) {
      checkArgument(password != null, "password can not be null");
      checkArgument(!password.isEmpty(), "password can not be empty");
      return builder().setPassword(password).build();
    }

    /**
     * If Elasticsearch uses SSL/TLS with mutual authentication (via shield),
     * provide the keystore containing the client key.
     *
     * @param keystorePath the location of the keystore containing the client key.
     */
    public ConnectionConfiguration withKeystorePath(String keystorePath) {
      checkArgument(keystorePath != null, "keystorePath can not be null");
      checkArgument(!keystorePath.isEmpty(), "keystorePath can not be empty");
      return builder().setKeystorePath(keystorePath).build();
    }

    /**
     * If Elasticsearch uses SSL/TLS with mutual authentication (via shield),
     * provide the password to open the client keystore.
     *
     * @param keystorePassword the password of the client keystore.
     */
    public ConnectionConfiguration withKeystorePassword(String keystorePassword) {
      checkArgument(keystorePassword != null, "keystorePassword can not be null");
      return builder().setKeystorePassword(keystorePassword).build();
    }

    abstract Builder builder();

    @VisibleForTesting
    RestClient createClient() throws IOException {
      HttpHost[] hosts = new HttpHost[getAddresses().size()];
      int i = 0;
      for (String address : getAddresses()) {
        URL url = new URL(address);
        hosts[i] = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
        i++;
      }
      RestClientBuilder restClientBuilder = RestClient.builder(hosts);
      if (getUsername() != null) {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                                           new UsernamePasswordCredentials(getUsername(), getPassword()));
        restClientBuilder.setHttpClientConfigCallback(httpAsyncClientBuilder -> httpAsyncClientBuilder.setDefaultCredentialsProvider(
          credentialsProvider));
      }
      if (getKeystorePath() != null && !getKeystorePath().isEmpty()) {
        try {
          KeyStore keyStore = KeyStore.getInstance("jks");
          try (InputStream is = new FileInputStream(new File(getKeystorePath()))) {
            String keystorePassword = getKeystorePassword();
            keyStore.load(is, (keystorePassword == null) ? null : keystorePassword.toCharArray());
          }
          final SSLContext sslContext =
            SSLContexts.custom().loadTrustMaterial(keyStore, new TrustSelfSignedStrategy()).build();
          final SSLIOSessionStrategy sessionStrategy = new SSLIOSessionStrategy(sslContext);
          restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setSSLContext(sslContext)
            .setSSLStrategy(sessionStrategy));
        } catch (Exception e) {
          throw new IOException("Can't load the client certificate from the keystore", e);
        }
      }
      return restClientBuilder.build();
    }

    private void populateDisplayData(DisplayData.Builder builder) {
      builder.add(DisplayData.item("address", getAddresses().toString()));
      builder.add(DisplayData.item("index", getIndex()));
      builder.add(DisplayData.item("type", getType()));
      builder.addIfNotNull(DisplayData.item("username", getUsername()));
      builder.addIfNotNull(DisplayData.item("keystore.path", getKeystorePath()));
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setAddresses(List<String> addresses);

      abstract Builder setUsername(String username);

      abstract Builder setPassword(String password);

      abstract Builder setKeystorePath(String keystorePath);

      abstract Builder setKeystorePassword(String password);

      abstract Builder setIndex(String index);

      abstract Builder setType(String type);

      abstract ConnectionConfiguration build();
    }
  }

  /**
   * A {@link PTransform} reading data from Elasticsearch.
   */
  @AutoValue
  public abstract static class Read extends PTransform<PBegin, PCollection<String>> {

    private static final long MAX_BATCH_SIZE = 10000L;

    /**
     * Provide the Elasticsearch connection configuration object.
     */
    public Read withConnectionConfiguration(ConnectionConfiguration connectionConfiguration) {
      checkArgument(connectionConfiguration != null, "connectionConfiguration can not be null");
      return builder().setConnectionConfiguration(connectionConfiguration).build();
    }

    /**
     * Provide a query used while reading from Elasticsearch.
     *
     * @param query the query. See <a
     *              href="https://www.elastic.co/guide/en/elasticsearch/reference/2.4/query-dsl.html">Query
     *              DSL</a>
     */
    public Read withQuery(String query) {
      checkArgument(query != null, "query can not be null");
      checkArgument(!query.isEmpty(), "query can not be empty");
      return builder().setQuery(query).build();
    }

    /**
     * Provide a scroll keepalive. See <a
     * href="https://www.elastic.co/guide/en/elasticsearch/reference/2.4/search-request-scroll.html">scroll
     * API</a> Default is "5m". Change this only if you get "No search context found" errors.
     */
    public Read withScrollKeepalive(String scrollKeepalive) {
      checkArgument(scrollKeepalive != null, "scrollKeepalive can not be null");
      checkArgument(!"0m".equals(scrollKeepalive), "scrollKeepalive can not be 0m");
      return builder().setScrollKeepalive(scrollKeepalive).build();
    }

    /**
     * Provide a size for the scroll read. See <a
     * href="https://www.elastic.co/guide/en/elasticsearch/reference/2.4/search-request-scroll.html">
     * scroll API</a> Default is 100. Maximum is 10 000. If documents are small, increasing batch
     * size might improve read performance. If documents are big, you might need to decrease
     * batchSize
     *
     * @param batchSize number of documents read in each scroll read
     */
    public Read withBatchSize(long batchSize) {
      checkArgument(batchSize > 0 && batchSize <= MAX_BATCH_SIZE,
                    "batchSize must be > 0 and <= %s, but was: %s",
                    MAX_BATCH_SIZE,
                    batchSize);
      return builder().setBatchSize(batchSize).build();
    }

    @Override
    public PCollection<String> expand(PBegin input) {
      ConnectionConfiguration connectionConfiguration = getConnectionConfiguration();
      checkState(connectionConfiguration != null, "withConnectionConfiguration() is required");
      return input.apply(org.apache.beam.sdk.io.Read.from(new BoundedElasticsearchSource(this, null, null, null)));
    }

    @Nullable
    abstract ConnectionConfiguration getConnectionConfiguration();

    @Nullable
    abstract String getQuery();

    abstract String getScrollKeepalive();

    abstract long getBatchSize();

    abstract Builder builder();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setConnectionConfiguration(ConnectionConfiguration connectionConfiguration);

      abstract Builder setQuery(String query);

      abstract Builder setScrollKeepalive(String scrollKeepalive);

      abstract Builder setBatchSize(long batchSize);

      abstract Read build();
    }

    @Override
    public void populateDisplayData(DisplayData.Builder builder) {
      super.populateDisplayData(builder);
      builder.addIfNotNull(DisplayData.item("query", getQuery()));
      builder.addIfNotNull(DisplayData.item("batchSize", getBatchSize()));
      builder.addIfNotNull(DisplayData.item("scrollKeepalive", getScrollKeepalive()));
      getConnectionConfiguration().populateDisplayData(builder);
    }
  }

  /**
   * A {@link BoundedSource} reading from Elasticsearch.
   */
  @VisibleForTesting
  public static class BoundedElasticsearchSource extends BoundedSource<String> {

    private final Read spec;
    // shardPreference is the shard id where the source will read the documents
    @Nullable
    private final String shardPreference;
    @Nullable
    private final Integer numSlices;
    @Nullable
    private final Integer sliceId;
    private int backendVersion;

    @VisibleForTesting
    static long estimateIndexSize(ConnectionConfiguration connectionConfiguration) throws IOException {
      // we use indices stats API to estimate size and list the shards
      // (https://www.elastic.co/guide/en/elasticsearch/reference/2.4/indices-stats.html)
      // as Elasticsearch 2.x doesn't not support any way to do parallel read inside a shard
      // the estimated size bytes is not really used in the split into bundles.
      // However, we implement this method anyway as the runners can use it.
      // NB: Elasticsearch 5.x now provides the slice API.
      // (https://www.elastic.co/guide/en/elasticsearch/reference/5.0/search-request-scroll.html
      // #sliced-scroll)
      JsonNode statsJson = getStats(connectionConfiguration, false);
      JsonNode indexStats = statsJson.path("indices").path(connectionConfiguration.getIndex()).path("primaries");
      JsonNode store = indexStats.path("store");
      return store.path("size_in_bytes").asLong();
    }

    private static JsonNode getStats(
      ConnectionConfiguration connectionConfiguration, boolean shardLevel
    ) throws IOException {
      HashMap<String, String> params = new HashMap<>();
      if (shardLevel) {
        params.put("level", "shards");
      }
      String endpoint = String.format("/%s/_stats", connectionConfiguration.getIndex());
      try (RestClient restClient = connectionConfiguration.createClient()) {
        return parseResponse(restClient.performRequest("GET", endpoint, params));
      }
    }

    //constructor used in split() when we know the backend version
    private BoundedElasticsearchSource(
      Read spec,
      @Nullable String shardPreference,
      @Nullable Integer numSlices,
      @Nullable Integer sliceId,
      int backendVersion
    ) {
      this.backendVersion = backendVersion;
      this.spec = spec;
      this.shardPreference = shardPreference;
      this.numSlices = numSlices;
      this.sliceId = sliceId;
    }

    @VisibleForTesting
    BoundedElasticsearchSource(
      Read spec, @Nullable String shardPreference, @Nullable Integer numSlices, @Nullable Integer sliceId
    ) {
      this.spec = spec;
      this.shardPreference = shardPreference;
      this.numSlices = numSlices;
      this.sliceId = sliceId;
    }

    @Override
    public List<? extends BoundedSource<String>> split(
      long desiredBundleSizeBytes, PipelineOptions options
    ) throws Exception {
      ConnectionConfiguration connectionConfiguration = spec.getConnectionConfiguration();
      this.backendVersion = getBackendVersion(connectionConfiguration);
      List<BoundedElasticsearchSource> sources = new ArrayList<>();
      if (backendVersion == 2) {
        // 1. We split per shard :
        // unfortunately, Elasticsearch 2. x doesn 't provide a way to do parallel reads on a single
        // shard.So we do not use desiredBundleSize because we cannot split shards.
        // With the slice API in ES 5.0 we will be able to use desiredBundleSize.
        // Basically we will just ask the slice API to return data
        // in nbBundles = estimatedSize / desiredBundleSize chuncks.
        // So each beam source will read around desiredBundleSize volume of data.

        JsonNode statsJson = BoundedElasticsearchSource.getStats(connectionConfiguration, true);
        JsonNode shardsJson = statsJson.path("indices").path(connectionConfiguration.getIndex()).path("shards");

        Iterator<Map.Entry<String, JsonNode>> shards = shardsJson.fields();
        while (shards.hasNext()) {
          Map.Entry<String, JsonNode> shardJson = shards.next();
          String shardId = shardJson.getKey();
          sources.add(new BoundedElasticsearchSource(spec, shardId, null, null, backendVersion));
        }
        checkArgument(!sources.isEmpty(), "No shard found");
      } else if (backendVersion == 5) {
        long indexSize = BoundedElasticsearchSource.estimateIndexSize(connectionConfiguration);
        float nbBundlesFloat = (float) indexSize / desiredBundleSizeBytes;
        int nbBundles = (int) Math.ceil(nbBundlesFloat);
        //ES slice api imposes that the number of slices is <= 1024 even if it can be overloaded
        if (nbBundles > 1024) {
          nbBundles = 1024;
        }
        // split the index into nbBundles chunks of desiredBundleSizeBytes by creating
        // nbBundles sources each reading a slice of the index
        // (see https://goo.gl/MhtSWz)
        // the slice API allows to split the ES shards
        // to have bundles closer to desiredBundleSizeBytes
        for (int i = 0; i < nbBundles; i++) {
          sources.add(new BoundedElasticsearchSource(spec, null, nbBundles, i, backendVersion));
        }
      }
      return sources;
    }

    @Override
    public long getEstimatedSizeBytes(PipelineOptions options) throws IOException {
      return estimateIndexSize(spec.getConnectionConfiguration());
    }

    @Override
    public BoundedReader<String> createReader(PipelineOptions options) {
      return new BoundedElasticsearchReader(this);
    }

    @Override
    public void validate() {
      spec.validate(null);
    }

    @Override
    public Coder<String> getOutputCoder() {
      return StringUtf8Coder.of();
    }

    @Override
    public void populateDisplayData(DisplayData.Builder builder) {
      spec.populateDisplayData(builder);
      builder.addIfNotNull(DisplayData.item("shard", shardPreference));
      builder.addIfNotNull(DisplayData.item("numSlices", numSlices));
      builder.addIfNotNull(DisplayData.item("sliceId", sliceId));
    }
  }

  private static class BoundedElasticsearchReader extends BoundedSource.BoundedReader<String> {

    private final BoundedElasticsearchSource source;

    private RestClient restClient;
    private String current;
    private String scrollId;
    private ListIterator<String> batchIterator;

    private BoundedElasticsearchReader(BoundedElasticsearchSource source) {
      this.source = source;
    }

    @Override
    public boolean start() throws IOException {
      restClient = source.spec.getConnectionConfiguration().createClient();

      String query = source.spec.getQuery();
      if (query == null) {
        query = "{\"query\": { \"match_all\": {} }}";
      }
      if (source.backendVersion == 5 && source.numSlices != null && source.numSlices > 1) {
        //if there is more than one slice, add the slice to the user query
        String sliceQuery = String.format("\"slice\": {\"id\": %s,\"max\": %s}", source.sliceId, source.numSlices);
        query = query.replaceFirst("\\{", "{" + sliceQuery + ",");
      }
      Response response;
      String endPoint = String.format("/%s/%s/_search",
                                      source.spec.getConnectionConfiguration().getIndex(),
                                      source.spec.getConnectionConfiguration().getType());
      Map<String, String> params = new HashMap<>();
      params.put("scroll", source.spec.getScrollKeepalive());
      if (source.backendVersion == 2) {
        params.put("size", String.valueOf(source.spec.getBatchSize()));
        if (source.shardPreference != null) {
          params.put("preference", "_shards:" + source.shardPreference);
        }
      }
      HttpEntity queryEntity = new NStringEntity(query, ContentType.APPLICATION_JSON);
      response = restClient.performRequest("GET", endPoint, params, queryEntity);
      JsonNode searchResult = parseResponse(response);
      updateScrollId(searchResult);
      return readNextBatchAndReturnFirstDocument(searchResult);
    }

    @Override
    public boolean advance() throws IOException {
      if (batchIterator.hasNext()) {
        current = batchIterator.next();
        return true;
      } else {
        String requestBody =
          String.format("{\"scroll\" : \"%s\",\"scroll_id\" : \"%s\"}", source.spec.getScrollKeepalive(), scrollId);
        HttpEntity scrollEntity = new NStringEntity(requestBody, ContentType.APPLICATION_JSON);
        Response response = restClient.performRequest("GET", "/_search/scroll", Collections.emptyMap(), scrollEntity);
        JsonNode searchResult = parseResponse(response);
        updateScrollId(searchResult);
        return readNextBatchAndReturnFirstDocument(searchResult);
      }
    }

    @Override
    public String getCurrent() throws NoSuchElementException {
      if (current == null) {
        throw new NoSuchElementException();
      }
      return current;
    }

    @Override
    public void close() throws IOException {
      // remove the scroll
      String requestBody = String.format("{\"scroll_id\" : [\"%s\"]}", scrollId);
      HttpEntity entity = new NStringEntity(requestBody, ContentType.APPLICATION_JSON);
      try {
        restClient.performRequest("DELETE", "/_search/scroll", Collections.emptyMap(), entity);
      } finally {
        if (restClient != null) {
          restClient.close();
        }
      }
    }

    @Override
    public BoundedSource<String> getCurrentSource() {
      return source;
    }

    private void updateScrollId(JsonNode searchResult) {
      scrollId = searchResult.path("_scroll_id").asText();
    }

    private boolean readNextBatchAndReturnFirstDocument(JsonNode searchResult) {
      //stop if no more data
      JsonNode hits = searchResult.path("hits").path("hits");
      if (hits.size() == 0) {
        current = null;
        batchIterator = null;
        return false;
      }
      // list behind iterator is empty
      List<String> batch = new ArrayList<>();
      for (JsonNode hit : hits) {
        String document = hit.path("_source").toString();
        batch.add(document);
      }
      batchIterator = batch.listIterator();
      current = batchIterator.next();
      return true;
    }
  }

  /**
   * A {@link PTransform} writing data to Elasticsearch.
   */
  @AutoValue
  public abstract static class Write extends PTransform<PCollection<String>, PDone> {

    /**
     * Provide the Elasticsearch connection configuration object.
     *
     * @param connectionConfiguration the Elasticsearch {@link ConnectionConfiguration} object
     *
     * @return the {@link Write} with connection configuration set
     */
    public Write withConnectionConfiguration(ConnectionConfiguration connectionConfiguration) {
      checkArgument(connectionConfiguration != null, "connectionConfiguration can not be null");
      return builder().setConnectionConfiguration(connectionConfiguration).build();
    }

    /**
     * Provide a maximum size in number of documents for the batch see bulk API
     * (https://www.elastic.co/guide/en/elasticsearch/reference/2.4/docs-bulk.html). Default is 1000
     * docs (like Elasticsearch bulk size advice). See
     * https://www.elastic.co/guide/en/elasticsearch/guide/current/bulk.html Depending on the
     * execution engine, size of bundles may vary, this sets the maximum size. Change this if you
     * need to have smaller ElasticSearch bulks.
     *
     * @param batchSize maximum batch size in number of documents
     *
     * @return the {@link Write} with connection batch size set
     */
    public Write withMaxBatchSize(long batchSize) {
      checkArgument(batchSize > 0, "batchSize must be > 0, but was %s", batchSize);
      return builder().setMaxBatchSize(batchSize).build();
    }

    /**
     * Provide a maximum size in bytes for the batch see bulk API
     * (https://www.elastic.co/guide/en/elasticsearch/reference/2.4/docs-bulk.html). Default is 5MB
     * (like Elasticsearch bulk size advice). See
     * https://www.elastic.co/guide/en/elasticsearch/guide/current/bulk.html Depending on the
     * execution engine, size of bundles may vary, this sets the maximum size. Change this if you
     * need to have smaller ElasticSearch bulks.
     *
     * @param batchSizeBytes maximum batch size in bytes
     *
     * @return the {@link Write} with connection batch size in bytes set
     */
    public Write withMaxBatchSizeBytes(long batchSizeBytes) {
      checkArgument(batchSizeBytes > 0, "batchSizeBytes must be > 0, but was %s", batchSizeBytes);
      return builder().setMaxBatchSizeBytes(batchSizeBytes).build();
    }

    /**
     * Provide a function to extract the id from the document. This id will be used as the document
     * id in Elasticsearch. Should the function throw an Exception then the batch will fail and the
     * exception propagated.
     *
     * @param idFn to extract the document ID
     *
     * @return the {@link Write} with the function set
     */
    public Write withIdFn(FieldValueExtractFn idFn) {
      checkArgument(idFn != null, "idFn must not be null");
      return builder().setIdFn(idFn).build();
    }

    /**
     * Provide a function to extract the target index from the document allowing for dynamic
     * document routing. Should the function throw an Exception then the batch will fail and the
     * exception propagated.
     *
     * @param indexFn to extract the destination index from
     *
     * @return the {@link Write} with the function set
     */
    public Write withIndexFn(FieldValueExtractFn indexFn) {
      checkArgument(indexFn != null, "indexFn must not be null");
      return builder().setIndexFn(indexFn).build();
    }

    /**
     * Provide a function to extract the target type from the document allowing for dynamic document
     * routing. Should the function throw an Exception then the batch will fail and the exception
     * propagated. Users are encouraged to consider carefully if multipe types are a sensible model
     * <a
     * href="https://www.elastic.co/blog/index-type-parent-child-join-now-future-in-elasticsearch">as
     * discussed in this blog</a>.
     *
     * @param typeFn to extract the destination index from
     *
     * @return the {@link Write} with the function set
     */
    public Write withTypeFn(FieldValueExtractFn typeFn) {
      checkArgument(typeFn != null, "typeFn must not be null");
      return builder().setTypeFn(typeFn).build();
    }

    /**
     * Provide an instruction to control whether partial updates or inserts (default) are issued to
     * Elasticsearch.
     *
     * @param usePartialUpdate set to true to issue partial updates
     *
     * @return the {@link Write} with the partial update control set
     */
    public Write withUsePartialUpdate(boolean usePartialUpdate) {
      return builder().setUsePartialUpdate(usePartialUpdate).build();
    }

    @Nullable
    abstract ConnectionConfiguration getConnectionConfiguration();

    abstract long getMaxBatchSize();

    abstract long getMaxBatchSizeBytes();

    @Nullable
    abstract FieldValueExtractFn getIdFn();

    @Nullable
    abstract FieldValueExtractFn getIndexFn();

    @Nullable
    abstract FieldValueExtractFn getTypeFn();

    abstract boolean getUsePartialUpdate();

    abstract Builder builder();

    /**
     * Interface allowing a specific field value to be returned from a parsed JSON document. This is
     * used for using explicit document ids, and for dynamic routing (index/Type) on a document
     * basis. A null response will result in default behaviour and an exception will be propagated
     * as a failure.
     */
    public interface FieldValueExtractFn extends SerializableFunction<JsonNode, String> {}

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setConnectionConfiguration(ConnectionConfiguration connectionConfiguration);

      abstract Builder setMaxBatchSize(long maxBatchSize);

      abstract Builder setMaxBatchSizeBytes(long maxBatchSizeBytes);

      abstract Builder setIdFn(FieldValueExtractFn idFunction);

      abstract Builder setIndexFn(FieldValueExtractFn indexFn);

      abstract Builder setTypeFn(FieldValueExtractFn typeFn);

      abstract Builder setUsePartialUpdate(boolean usePartialUpdate);

      abstract Write build();
    }

    /**
     * {@link DoFn} to for the {@link Write} transform.
     */
    @VisibleForTesting
    static class WriteFn extends DoFn<String, Void> {

      private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
      private static final int DEFAULT_RETRY_ON_CONFLICT = 5; // race conditions on updates
      private final Write spec;
      private int backendVersion;
      private transient RestClient restClient;
      private ArrayList<String> batch;
      private long currentBatchSizeBytes;

      private static String lowerCaseOrNull(String input) {
        return input == null ? null : input.toLowerCase();
      }

      @VisibleForTesting
      WriteFn(Write spec) {
        this.spec = spec;
      }

      @Setup
      public void setup() throws Exception {
        ConnectionConfiguration connectionConfiguration = spec.getConnectionConfiguration();
        backendVersion = getBackendVersion(connectionConfiguration);
        restClient = connectionConfiguration.createClient();
      }

      @StartBundle
      public void startBundle(StartBundleContext context) {
        batch = new ArrayList<>();
        currentBatchSizeBytes = 0;
      }

      @ProcessElement
      public void processElement(ProcessContext context) throws Exception {
        String document = context.element();
        String documentMetadata = getDocumentMetadata(document);

        // index is an insert/upsert and update is a partial update
        if (spec.getUsePartialUpdate()) {
          batch.add(String.format("{ \"update\" : %s }%n{ \"doc\" : %s}%n", documentMetadata, document));
        } else {
          batch.add(String.format("{ \"index\" : %s }%n%s%n", documentMetadata, document));
        }

        currentBatchSizeBytes += document.getBytes(StandardCharsets.UTF_8).length;
        if (batch.size() >= spec.getMaxBatchSize() || currentBatchSizeBytes >= spec.getMaxBatchSizeBytes()) {
          flushBatch();
        }
      }

      @FinishBundle
      public void finishBundle(FinishBundleContext context) throws Exception {
        flushBatch();
      }

      @Teardown
      public void closeClient() throws Exception {
        if (restClient != null) {
          restClient.close();
        }
      }

      /**
       * Extracts the components that comprise the document address from the document using the
       * {@link FieldValueExtractFn} configured. This allows any or all of the index, type and
       * document id to be controlled on a per document basis. If none are provided then an empty
       * default of {@code {}} is returned. Sanitization of the index is performed, automatically
       * lower-casing the value as required by Elasticsearch.
       *
       * @param document the json from which the index, type and id may be extracted
       *
       * @return the document address as JSON or the default
       *
       * @throws IOException if the document cannot be parsed as JSON
       */
      private String getDocumentMetadata(String document) throws IOException {
        if (spec.getIndexFn() != null || spec.getTypeFn() != null || spec.getIdFn() != null) {
          // parse once and reused for efficiency
          JsonNode parsedDocument = OBJECT_MAPPER.readTree(document);

          DocumentMetadata metadata = new DocumentMetadata(spec.getIndexFn() != null
                                                             ? lowerCaseOrNull(spec.getIndexFn().apply(parsedDocument))
                                                             : null,
                                                           spec.getTypeFn() != null ? spec.getTypeFn()
                                                             .apply(parsedDocument) : null,
                                                           spec.getIdFn() != null
                                                             ? spec.getIdFn().apply(parsedDocument)
                                                             : null,
                                                           spec.getUsePartialUpdate()
                                                             ? DEFAULT_RETRY_ON_CONFLICT
                                                             : null);
          return OBJECT_MAPPER.writeValueAsString(metadata);

        } else {
          return "{}"; // use configuration and auto-generated document IDs
        }
      }

      private void flushBatch() throws IOException {
        if (batch.isEmpty()) {
          return;
        }
        StringBuilder bulkRequest = new StringBuilder();
        for (String json : batch) {
          bulkRequest.append(json);
        }
        batch.clear();
        currentBatchSizeBytes = 0;
        Response response;
        // Elasticsearch will default to the index/type provided here if none are set in the
        // document meta (i.e. using ElasticsearchIO$Write#withIndexFn and
        // ElasticsearchIO$Write#withTypeFn options)
        String endPoint = String.format("/%s/%s/_bulk",
                                        spec.getConnectionConfiguration().getIndex(),
                                        spec.getConnectionConfiguration().getType());
        HttpEntity requestBody = new NStringEntity(bulkRequest.toString(), ContentType.APPLICATION_JSON);
        response = restClient.performRequest("POST", endPoint, Collections.emptyMap(), requestBody);
        checkForErrors(response, backendVersion);
      }

      // Encapsulates the elements which form the metadata for an Elasticsearch bulk operation
      @JsonPropertyOrder({"_index", "_type", "_id"})
      @JsonInclude(JsonInclude.Include.NON_NULL)
      private static class DocumentMetadata implements Serializable {

        @JsonProperty("_index")
        final String index;

        @JsonProperty("_type")
        final String type;

        @JsonProperty("_id")
        final String id;

        @JsonProperty("_retry_on_conflict")
        final Integer retryOnConflict;

        DocumentMetadata(String index, String type, String id, Integer retryOnConflict) {
          this.index = index;
          this.type = type;
          this.id = id;
          this.retryOnConflict = retryOnConflict;
        }
      }
    }    @Override
    public PDone expand(PCollection<String> input) {
      ConnectionConfiguration connectionConfiguration = getConnectionConfiguration();
      checkState(connectionConfiguration != null, "withConnectionConfiguration() is required");
      input.apply(ParDo.of(new WriteFn(this)));
      return PDone.in(input.getPipeline());
    }


  }
}
