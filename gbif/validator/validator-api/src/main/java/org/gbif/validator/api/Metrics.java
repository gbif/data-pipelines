package org.gbif.validator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.gbif.api.model.crawler.GenericValidationReport;
import org.gbif.api.model.crawler.OccurrenceValidationReport;

@JsonDeserialize(builder = Metrics.MetricsBuilder.class)
@Builder
@Data
public class Metrics {

  @Builder.Default private Core core = Core.builder().build();
  @Builder.Default private List<Extension> extensions = Collections.emptyList();

  @Builder.Default
  private ArchiveValidationReport archiveValidationReport =
      ArchiveValidationReport.builder().build();

  @JsonDeserialize(builder = Core.CoreBuilder.class)
  @Builder
  @Data
  public static class Core {
    @Builder.Default private Long fileCount = 0L;
    @Builder.Default private Long indexedCount = 0L;

    @Builder.Default private Map<String, TermInfo> indexedCoreTerms = Collections.emptyMap();

    @Builder.Default private Map<String, Long> occurrenceIssues = Collections.emptyMap();

    @JsonDeserialize(builder = Core.TermInfo.TermInfoBuilder.class)
    @Builder
    @Data
    public static class TermInfo {
      @Builder.Default private Long rawIndexed = 0L;
      @Builder.Default private Long interpretedIndexed = null;
    }
  }

  @JsonDeserialize(builder = Extension.ExtensionBuilder.class)
  @Builder
  @Data
  public static class Extension {
    @Builder.Default private String rowType = "";
    @Builder.Default private Long fileCount = 0L;
    @Builder.Default private Map<String, Long> extensionsTermsCounts = Collections.emptyMap();
  }

  @JsonDeserialize(builder = ArchiveValidationReport.ArchiveValidationReportBuilder.class)
  @Builder
  @Data
  public static class ArchiveValidationReport {
    private final OccurrenceValidationReport occurrenceReport;
    private final GenericValidationReport genericReport;
    private final String invalidationReason;
  }

  @Override
  public String toString() {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.writeValueAsString(this);
    } catch (IOException e) {
      // NOP
    }
    return "";
  }
}
