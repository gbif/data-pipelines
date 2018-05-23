package org.gbif.pipelines.assembling.interpretation.steps;

import org.gbif.pipelines.config.InterpretationType;
import org.gbif.pipelines.io.avro.InterpretedExtendedRecord;
import org.gbif.pipelines.io.avro.Location;
import org.gbif.pipelines.io.avro.MultimediaRecord;
import org.gbif.pipelines.io.avro.TaxonRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.transform.record.InterpretedExtendedRecordTransform;
import org.gbif.pipelines.transform.record.LocationTransform;
import org.gbif.pipelines.transform.record.MultimediaRecordTransform;
import org.gbif.pipelines.transform.record.TaxonRecordTransform;
import org.gbif.pipelines.transform.record.TemporalRecordTransform;

import java.util.function.Supplier;

import org.apache.avro.file.CodecFactory;

/**
 * {@link Supplier} that supplies a {@link InterpretationStep}.
 */
public interface InterpretationStepSupplier extends Supplier<InterpretationStep> {

  /**
   * Gbif location interpretation.
   */
  static InterpretationStepSupplier locationGbif(PipelineTargetPaths paths, CodecFactory avroCodec) {
    return () -> InterpretationStep.<Location>newBuilder().interpretationType(InterpretationType.LOCATION)
      .avroClass(Location.class)
      .transform(LocationTransform.create())
      .dataTargetPath(paths.getDataTargetPath())
      .issuesTargetPath(paths.getIssuesTargetPath())
      .tempDirectory(paths.getTempDir())
      .avroCodec(avroCodec)
      .build();
  }

  /**
   * Gbif temporal interpretation.
   */
  static InterpretationStepSupplier temporalGbif(PipelineTargetPaths paths, CodecFactory avroCodec) {
    return () -> InterpretationStep.<TemporalRecord>newBuilder().interpretationType(InterpretationType.TEMPORAL)
      .avroClass(TemporalRecord.class)
      .transform(TemporalRecordTransform.create())
      .dataTargetPath(paths.getDataTargetPath())
      .issuesTargetPath(paths.getIssuesTargetPath())
      .tempDirectory(paths.getTempDir())
      .avroCodec(avroCodec)
      .build();
  }

  /**
   * Gbif taxonomy interpretation.
   */
  static InterpretationStepSupplier taxonomyGbif(PipelineTargetPaths paths, CodecFactory avroCodec) {
    return () -> InterpretationStep.<TaxonRecord>newBuilder().interpretationType(InterpretationType.TAXONOMY)
      .avroClass(TaxonRecord.class)
      .transform(TaxonRecordTransform.create())
      .dataTargetPath(paths.getDataTargetPath())
      .issuesTargetPath(paths.getIssuesTargetPath())
      .tempDirectory(paths.getTempDir())
      .avroCodec(avroCodec)
      .build();
  }

  /**
   * Gbif common interpretations.
   */
  static InterpretationStepSupplier commonGbif(PipelineTargetPaths paths, CodecFactory avroCodec) {
    return () -> InterpretationStep.<InterpretedExtendedRecord>newBuilder().interpretationType(InterpretationType.COMMON)
      .avroClass(InterpretedExtendedRecord.class)
      .transform(InterpretedExtendedRecordTransform.create())
      .dataTargetPath(paths.getDataTargetPath())
      .issuesTargetPath(paths.getIssuesTargetPath())
      .tempDirectory(paths.getTempDir())
      .avroCodec(avroCodec)
      .build();
  }

  /**
   * Gbif multimedia interpretations.
   */
  static InterpretationStepSupplier multimediaGbif(PipelineTargetPaths paths, CodecFactory avroCodec) {
    return () -> InterpretationStep.<MultimediaRecord>newBuilder().interpretationType(InterpretationType.MULTIMEDIA)
      .avroClass(MultimediaRecord.class)
      .transform(MultimediaRecordTransform.create())
      .dataTargetPath(paths.getDataTargetPath())
      .issuesTargetPath(paths.getIssuesTargetPath())
      .tempDirectory(paths.getTempDir())
      .avroCodec(avroCodec)
      .build();
  }
}
