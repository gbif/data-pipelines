package org.gbif.validator.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.sql.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = Validation.ValidationBuilder.class)
public class Validation {

  private static final EnumSet<Status> EXECUTING_STATUSES =
      EnumSet.of(Status.SUBMITTED, Status.DOWNLOADING, Status.RUNNING);

  private static final EnumSet<Status> FINISHED_STATUSES =
      EnumSet.of(Status.FAILED, Status.ABORTED);

  public enum Status {
    DOWNLOADING,
    SUBMITTED,
    RUNNING,
    FINISHED,
    ABORTED,
    FAILED
  }

  private final UUID key;
  private Date created;
  private Date modified;
  private String username;
  private String file;
  private Long fileSize;
  private FileFormat fileFormat;
  private Status status;
  private Metrics metrics;

  public static Set<Status> executingStatuses() {
    return EXECUTING_STATUSES;
  }

  public static Set<Status> finishedStatuses() {
    return FINISHED_STATUSES;
  }

  @JsonIgnore
  public boolean isExecuting() {
    return EXECUTING_STATUSES.contains(status);
  }

  @JsonIgnore
  public boolean hasFinished() {
    return FINISHED_STATUSES.contains(status);
  }
}
