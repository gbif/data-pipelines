package org.gbif.pipelines.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelinesConfig implements Serializable {

  private static final long serialVersionUID = 8102560635064341713L;

  private String zkConnectionString;

  private WsConfig gbifApi;

  private String imageCachePath = "bitmap/bitmap.png";

  private KvConfig nameUsageMatch;

  private KvConfig geocode;

  private KvConfig locationFeature;

  private ContentConfig content;

  private WsConfig amplification;

  private KeygenConfig keygen;

  private LockConfig indexLock;

  private LockConfig hdfsLock;
  /**
   * Provide recommended formats to parse ambiguous dates, e.g. 2/3/2008. If the field is empty or
   * invalid, only accepts standard ISO date format. Parsing 2/3/2008 will fail . <code>DMY</code>
   * will parse 2/3/2008 as 2 Mar 2008 <code>MDY</code> will parse 2/3/2008 as 3 Feb 2008
   */
  private String defaultDateFormat;
}
