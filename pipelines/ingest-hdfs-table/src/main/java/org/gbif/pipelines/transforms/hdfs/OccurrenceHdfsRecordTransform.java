package org.gbif.pipelines.transforms.hdfs;

import org.gbif.pipelines.io.avro.OccurrenceHdfsRecord;
import org.gbif.pipelines.transforms.Transform;

import org.apache.beam.sdk.io.AvroIO;

import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.RecordType.OCCURRENCE_HDFS_RECORD;

public class OccurrenceHdfsRecordTransform extends Transform<OccurrenceHdfsRecord, OccurrenceHdfsRecord> {

  private OccurrenceHdfsRecordTransform() {
    super(OccurrenceHdfsRecord.class, OCCURRENCE_HDFS_RECORD);
  }

  public static OccurrenceHdfsRecordTransform create() {
    return new OccurrenceHdfsRecordTransform();
  }

  /**
   * Writes {@link OccurrenceHdfsRecord} *.avro files to path, data will be split into several files, uses
   * Snappy compression codec by default
   *
   * @param toPath path with name to output files, like - directory/name
   */
  public AvroIO.Write<OccurrenceHdfsRecord> write(String toPath, Integer numShards) {
    return numShards == null ? write(toPath) : write(toPath).withNumShards(numShards);
  }

}
