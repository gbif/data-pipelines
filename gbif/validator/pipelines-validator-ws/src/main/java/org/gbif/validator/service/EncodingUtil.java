package org.gbif.validator.service;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/** Url encoding class. */
@UtilityClass
@Slf4j
public class EncodingUtil {

  /** Encodes an URL, specially URLs with blank spaces can be problematics. */
  static String encode(String rawUrl) {
    try {
      String decodedURL = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.name());
      URL url = new URL(decodedURL);
      URI uri =
          new URI(
              url.getProtocol(),
              url.getUserInfo(),
              url.getHost(),
              url.getPort(),
              url.getPath(),
              url.getQuery(),
              url.getRef());
      return uri.toURL().toString();
    } catch (Exception ex) {
      log.error("Url encoding error", ex);
      throw new IllegalArgumentException(ex);
    }
  }
}
