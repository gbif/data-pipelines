package org.gbif.pipelines.transforms.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

import org.gbif.pipelines.core.Interpretation;
import org.gbif.pipelines.core.interpreters.core.DefaultTemporalInterpreter;
import org.gbif.pipelines.core.interpreters.core.TemporalInterpreter;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.transforms.SerializableConsumer;
import org.gbif.pipelines.transforms.Transform;

import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.TypeDescriptor;

import org.gbif.pipelines.parsers.config.model.PipelinesConfig;

import static org.gbif.common.parsers.date.DateComponentOrdering.DMY_FORMATS;
import static org.gbif.common.parsers.date.DateComponentOrdering.MDY_FORMATS;
import static org.gbif.pipelines.common.PipelinesVariables.Metrics.TEMPORAL_RECORDS_COUNT;
import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.RecordType.TEMPORAL;

/**
 * Beam level transformations for the DWC Event, reads an avro, writes an avro, maps from value to keyValue and
 * transforms form {@link ExtendedRecord} to {@link TemporalRecord}.
 * <p>
 * ParDo runs sequence of interpretations for {@link TemporalRecord} using {@link ExtendedRecord}
 * as a source and {@link TemporalInterpreter} as interpretation steps
 *
 * @see <a href="https://dwc.tdwg.org/terms/#event">https://dwc.tdwg.org/terms/#event</a>
 */
public class TemporalTransform extends Transform<ExtendedRecord, TemporalRecord> {

  private PipelinesConfig config;
  private  DefaultTemporalInterpreter temporalInterpreter;

  private TemporalTransform() {
    super(TemporalRecord.class, TEMPORAL, TemporalTransform.class.getName(), TEMPORAL_RECORDS_COUNT);

  }

  //use default ISO date parser
  public static TemporalTransform create() {
    TemporalTransform tr = new TemporalTransform();
    tr.temporalInterpreter =  DefaultTemporalInterpreter.getInstance();
    return tr;
  }

  public static TemporalTransform create(PipelinesConfig config) {
    TemporalTransform tr = new TemporalTransform();
    tr.config = config;

    if(config.getDefaultDateFormat().equalsIgnoreCase("DMY")){
      tr.temporalInterpreter =  DefaultTemporalInterpreter.getInstance(DMY_FORMATS);
    }else if( config.getDefaultDateFormat().equalsIgnoreCase("MDY") ){
      tr.temporalInterpreter =  DefaultTemporalInterpreter.getInstance(MDY_FORMATS);
    }else
      tr.temporalInterpreter =  DefaultTemporalInterpreter.getInstance();

    return tr;
  }


  /** Maps {@link TemporalRecord} to key value, where key is {@link TemporalRecord#getId} */
  public MapElements<TemporalRecord, KV<String, TemporalRecord>> toKv() {
    return MapElements.into(new TypeDescriptor<KV<String, TemporalRecord>>() {})
        .via((TemporalRecord tr) -> KV.of(tr.getId(), tr));
  }

  public TemporalTransform counterFn(SerializableConsumer<String> counterFn) {
    setCounterFn(counterFn);
    return this;
  }

  @Override
  public Optional<TemporalRecord> convert(ExtendedRecord source) {
    TemporalRecord tr = TemporalRecord.newBuilder()
        .setId(source.getId())
        .setCreated(Instant.now().toEpochMilli())
        .build();


    return Interpretation.from(source)
        .to(tr)
        .when(er -> !er.getCoreTerms().isEmpty())
        .via(temporalInterpreter::interpretTemporal)
        .get();
  }


}
