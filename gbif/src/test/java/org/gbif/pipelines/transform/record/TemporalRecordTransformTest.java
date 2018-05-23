package org.gbif.pipelines.transform.record;

import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.core.parsers.temporal.ParsedTemporalDates;
import org.gbif.pipelines.io.avro.EventDate;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.transform.Kv2Value;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.beam.sdk.testing.NeedsRunner;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TemporalRecordTransformTest {

  @Rule
  public final transient TestPipeline p = TestPipeline.create();

  @Test
  @Category(NeedsRunner.class)
  public void testTransformation() {
    // State
    final List<ExtendedRecord> input =
      createExtendedRecordList("1999-04-17T12:26Z/12:52:17Z", "1999-04/2010-05", "2010/2011");

    // Expected
    // First
    final LocalDateTime fromOne = LocalDateTime.of(1999, 4, 17, 12, 26);
    final LocalDateTime toOne = LocalDateTime.of(1999, 4, 17, 12, 52, 17);
    final ParsedTemporalDates periodOne = new ParsedTemporalDates(fromOne, toOne);
    periodOne.setYear(Year.of(1999));
    periodOne.setMonth(Month.of(10));
    periodOne.setDay(1);
    // Second
    final YearMonth fromTwo = YearMonth.of(1999, 4);
    final YearMonth toTwo = YearMonth.of(2010, 5);
    final ParsedTemporalDates periodTwo = new ParsedTemporalDates(fromTwo, toTwo);
    periodTwo.setYear(Year.of(1999));
    periodTwo.setMonth(Month.of(10));
    periodTwo.setDay(1);
    // Third
    final Year fromThree = Year.of(2010);
    final Year toThree = Year.of(2011);
    final ParsedTemporalDates periodThree = new ParsedTemporalDates(fromThree, toThree);
    periodThree.setYear(Year.of(1999));
    periodThree.setMonth(Month.of(10));
    periodThree.setDay(1);

    final List<TemporalRecord> dataExpected = createTemporalRecordList(periodOne, periodTwo, periodThree);

    // When
    //Coders.registerAvroCoders(p, ExtendedRecord.class, TemporalRecord.class, OccurrenceIssue.class);

    TemporalRecordTransform temporalRecord = TemporalRecordTransform.create().withAvroCoders(p);
    PCollection<ExtendedRecord> inputStream = p.apply(Create.of(input));

    PCollectionTuple tuple = inputStream.apply(temporalRecord);

    PCollection<TemporalRecord> dataStream = tuple.get(temporalRecord.getDataTag()).apply(Kv2Value.create());

    // Should
    PAssert.that(dataStream).containsInAnyOrder(dataExpected);
    p.run();
  }

  private List<ExtendedRecord> createExtendedRecordList(String... events) {
    return Arrays.stream(events).map(x -> {
      ExtendedRecord record = ExtendedRecord.newBuilder().setId("0").build();
      record.getCoreTerms().put(DwcTerm.year.qualifiedName(), "1999");
      record.getCoreTerms().put(DwcTerm.month.qualifiedName(), "10");
      record.getCoreTerms().put(DwcTerm.day.qualifiedName(), "1");
      record.getCoreTerms().put(DwcTerm.eventDate.qualifiedName(), x);
      record.getCoreTerms().put(DwcTerm.dateIdentified.qualifiedName(), x);
      record.getCoreTerms().put(DcTerm.modified.qualifiedName(), x);
      return record;
    }).collect(Collectors.toList());
  }

  private List<TemporalRecord> createTemporalRecordList(ParsedTemporalDates... dates) {
    return Arrays.stream(dates).map(x -> {
      String from = x.getFrom().map(Temporal::toString).orElse(null);
      String to = x.getTo().map(Temporal::toString).orElse(null);
      return TemporalRecord.newBuilder()
        .setId("0")
        .setYear(x.getYear().map(Year::getValue).orElse(null))
        .setMonth(x.getMonth().map(Month::getValue).orElse(null))
        .setDay(x.getDay().orElse(null))
        .setEventDate(EventDate.newBuilder().setGte(from).setLte(to).build())
        .setDateIdentified(x.getFrom().map(Temporal::toString).orElse(null))
        .setModified(x.getFrom().map(Temporal::toString).orElse(null))
        .build();
    }).collect(Collectors.toList());
  }

}
