package org.gbif.pipelines.core.ws;

import org.gbif.pipelines.core.ws.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for http client.
 */
public final class HttpClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(HttpClientFactory.class);

  private HttpClientFactory() {}

  /**
   * Creates a {@link OkHttpClient} from a specific {@link Config}.
   */
  public static OkHttpClient createClient(Config config) {
    Objects.requireNonNull(config);
    return Objects.isNull(config.getCacheConfig()) ? createClientWithoutCache(config) : createClientWithCache(config);
  }

  /**
   * Creates a {@link OkHttpClient} with {@link Cache} from a specific {@link Config}.
   */
  private static OkHttpClient createClientWithCache(Config config) {
    // create cache file
    File httpCacheDirectory;
    try {
      // use a new file cache for the current session
      httpCacheDirectory = Files.createTempDirectory(config.getCacheConfig().getName()).toFile();
      LOG.info("Cache file created - {}", httpCacheDirectory.getAbsolutePath());
    } catch (IOException e) {
      throw new IllegalStateException("Cannot run without the ability to create temporary cache directory", e);
    }

    // create cache
    Cache cache = new Cache(httpCacheDirectory, config.getCacheConfig().getSize());

    // create the client and return it
    return new OkHttpClient.Builder()
      .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
      .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
      .cache(cache)
      .build();
  }

  /**
   * Creates a {@link OkHttpClient} without {@link Cache} from a specific {@link Config}.
   */
  private static OkHttpClient createClientWithoutCache(Config config) {
    // create the client and return it
    return new OkHttpClient.Builder()
      .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
      .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
      .build();
  }

}
