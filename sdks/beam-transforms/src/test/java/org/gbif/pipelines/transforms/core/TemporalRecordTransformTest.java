package org.gbif.pipelines.transforms.core;

import static org.gbif.common.parsers.date.DateComponentOrdering.DMY_FORMATS;

import java.time.*;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.beam.sdk.testing.NeedsRunner;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.core.config.model.PipelinesConfig;
import org.gbif.pipelines.core.parsers.temporal.ParsedTemporal;
import org.gbif.pipelines.io.avro.EventDate;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@Category(NeedsRunner.class)
public class TemporalRecordTransformTest {

  @Rule public final transient TestPipeline p = TestPipeline.create();

  private static class CleanDateCreate extends DoFn<TemporalRecord, TemporalRecord> {

    @ProcessElement
    public void processElement(ProcessContext context) {
      TemporalRecord tr = TemporalRecord.newBuilder(context.element()).build();
      tr.setCreated(0L);
      context.output(tr);
    }
  }

  @Test
  public void transformationTest() {
    // State
    ExtendedRecord record = ExtendedRecord.newBuilder().setId("0").build();
    record.getCoreTerms().put(DwcTerm.year.qualifiedName(), "1999");
    record.getCoreTerms().put(DwcTerm.month.qualifiedName(), "2");
    record.getCoreTerms().put(DwcTerm.day.qualifiedName(), "2");
    record.getCoreTerms().put(DwcTerm.eventDate.qualifiedName(), "1999-02-02T12:26");
    record.getCoreTerms().put(DwcTerm.dateIdentified.qualifiedName(), "1999-02-02T12:26");
    record.getCoreTerms().put(DcTerm.modified.qualifiedName(), "1999-02-02T12:26");
    final List<ExtendedRecord> input = Collections.singletonList(record);

    // Expected
    // First
    final ParsedTemporal parsedTemporal = ParsedTemporal.create();
    parsedTemporal.setFromDate(LocalDateTime.of(1999, 2, 2, 12, 26));
    parsedTemporal.setYear(Year.of(1999));
    parsedTemporal.setMonth(Month.of(2));
    parsedTemporal.setDay(2);

    final ParsedTemporal other = ParsedTemporal.create();
    other.setFromDate(LocalDateTime.of(1999, 2, 2, 12, 26));

    final List<TemporalRecord> dataExpected = createTemporalRecordList(parsedTemporal, other);

    // When
    PCollection<TemporalRecord> dataStream =
        p.apply(Create.of(input))
            .apply(TemporalTransform.create().interpret())
            .apply("Cleaning timestamps", ParDo.of(new CleanDateCreate()));

    // Should
    PAssert.that(dataStream).containsInAnyOrder(dataExpected);
    p.run();
  }

  @Test
  public void emptyErTest() {

    // State
    ExtendedRecord er = ExtendedRecord.newBuilder().setId("777").build();

    // When
    PCollection<TemporalRecord> dataStream =
        p.apply(Create.of(er))
            .apply(TemporalTransform.create().interpret())
            .apply("Cleaning timestamps", ParDo.of(new CleanDateCreate()));

    // Should
    PAssert.that(dataStream).empty();
    p.run();
  }

  @Test
  public void DMY_transformationTest() {
    // State
    final List<ExtendedRecord> input = new ArrayList<>();

    ExtendedRecord record = ExtendedRecord.newBuilder().setId("0").build();
    record.getCoreTerms().put(DwcTerm.eventDate.qualifiedName(), "01/02/1999T12:26Z");
    record.getCoreTerms().put(DwcTerm.dateIdentified.qualifiedName(), "01/04/1999");
    record.getCoreTerms().put(DcTerm.modified.qualifiedName(), "01/03/1999T12:26");
    input.add(record);
    // Expected
    TemporalRecord expected1 =
        TemporalRecord.newBuilder()
            .setId("0")
            .setEventDate(EventDate.newBuilder().setGte("1999-02-01T12:26Z").build())
            .setYear(1999)
            .setMonth(2)
            .setDay(1)
            .setDateIdentified("1999-04-01")
            .setModified("1999-03-01T12:26")
            .setCreated(0L)
            .build();

    final List<TemporalRecord> dataExpected = new ArrayList<>();

    dataExpected.add(expected1);

    // When
    PipelinesConfig config = new PipelinesConfig();
    config.setDefaultDateFormat("DMY");

    PCollection<TemporalRecord> dataStream =
        p.apply(Create.of(input))
            .apply(TemporalTransform.create(DMY_FORMATS).interpret())
            .apply("Cleaning timestamps", ParDo.of(new CleanDateCreate()));

    // Should
    PAssert.that(dataStream).containsInAnyOrder(dataExpected);
    p.run();
  }

  private List<TemporalRecord> createTemporalRecordList(
      ParsedTemporal eventDate, ParsedTemporal other) {

    String from = eventDate.getFromOpt().map(Temporal::toString).orElse(null);
    String to = eventDate.getToOpt().map(Temporal::toString).orElse(null);
    return Collections.singletonList(
        TemporalRecord.newBuilder()
            .setId("0")
            .setYear(eventDate.getYearOpt().map(Year::getValue).orElse(null))
            .setMonth(eventDate.getMonthOpt().map(Month::getValue).orElse(null))
            .setDay(eventDate.getDayOpt().orElse(null))
            .setEventDate(EventDate.newBuilder().setGte(from).setLte(to).build())
            .setDateIdentified(other.getFromOpt().map(Temporal::toString).orElse(null))
            .setModified(other.getFromOpt().map(Temporal::toString).orElse(null))
            .setCreated(0L)
            .build());
  }
}
