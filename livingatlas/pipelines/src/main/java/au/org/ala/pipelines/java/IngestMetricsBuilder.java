package au.org.ala.pipelines.java;

import static org.gbif.pipelines.common.PipelinesVariables.Metrics.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.gbif.pipelines.common.beam.metrics.IngestMetrics;
import org.gbif.pipelines.transforms.common.FilterExtendedRecordTransform;
import org.gbif.pipelines.transforms.common.UniqueGbifIdTransform;
import org.gbif.pipelines.transforms.common.UniqueIdTransform;
import org.gbif.pipelines.transforms.converters.GbifJsonTransform;
import org.gbif.pipelines.transforms.converters.OccurrenceExtensionTransform;
import org.gbif.pipelines.transforms.core.*;
import org.gbif.pipelines.transforms.extension.MultimediaTransform;
import org.gbif.pipelines.transforms.metadata.MetadataTransform;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IngestMetricsBuilder {

  public static IngestMetrics createVerbatimToInterpretedMetrics() {
    return IngestMetrics.create()
        .addMetric(BasicTransform.class, BASIC_RECORDS_COUNT)
        .addMetric(LocationTransform.class, LOCATION_RECORDS_COUNT)
        .addMetric(MetadataTransform.class, METADATA_RECORDS_COUNT)
        .addMetric(TaxonomyTransform.class, TAXON_RECORDS_COUNT)
        .addMetric(GrscicollTransform.class, GRSCICOLL_RECORDS_COUNT)
        .addMetric(TemporalTransform.class, TEMPORAL_RECORDS_COUNT)
        .addMetric(VerbatimTransform.class, VERBATIM_RECORDS_COUNT)
        .addMetric(MultimediaTransform.class, MULTIMEDIA_RECORDS_COUNT)
        .addMetric(FilterExtendedRecordTransform.class, FILTER_ER_BASED_ON_GBIF_ID)
        .addMetric(UniqueGbifIdTransform.class, UNIQUE_GBIF_IDS_COUNT)
        .addMetric(UniqueGbifIdTransform.class, DUPLICATE_GBIF_IDS_COUNT)
        .addMetric(UniqueGbifIdTransform.class, IDENTICAL_GBIF_OBJECTS_COUNT)
        .addMetric(UniqueGbifIdTransform.class, INVALID_GBIF_ID_COUNT)
        .addMetric(UniqueIdTransform.class, UNIQUE_IDS_COUNT)
        .addMetric(UniqueIdTransform.class, DUPLICATE_IDS_COUNT)
        .addMetric(UniqueIdTransform.class, IDENTICAL_OBJECTS_COUNT)
        .addMetric(OccurrenceExtensionTransform.class, OCCURRENCE_EXT_COUNT);
  }

  public static IngestMetrics createInterpretedToEsIndexMetrics() {
    return IngestMetrics.create().addMetric(GbifJsonTransform.class, AVRO_TO_JSON_COUNT);
  }
}
