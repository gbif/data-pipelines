package org.gbif.pipelines.core.parsers;

import org.gbif.pipelines.core.parsers.memoize.ParserMemoizer;
import org.gbif.pipelines.core.parsers.temporal.ParsedTemporalDates;
import org.gbif.pipelines.core.parsers.temporal.accumulator.ChronoAccumulator;
import org.gbif.pipelines.core.parsers.temporal.accumulator.ChronoAccumulatorConverter;
import org.gbif.pipelines.core.parsers.temporal.parser.ParserRawDateTime;
import org.gbif.pipelines.core.parsers.temporal.utils.DelimiterUtils;
import org.gbif.pipelines.io.avro.IssueType;

import java.time.Month;
import java.time.Year;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Main interpreter class. Interpreter for raw temporal period. The main method interpret two dates, from and to
 */
public class TemporalParser {

  private static final BiFunction<ChronoAccumulator, List<IssueType>, Temporal> TEMPORAL_FUNC =
    (ca, deq) -> ChronoAccumulatorConverter.toTemporal(ca, deq).orElse(null);

  private static final ParserMemoizer<RawDateFields,ParsedTemporalDates> MEMOIZER = ParserMemoizer.memoize(TemporalParser::parseRawDate);

  /**
   * This class is used to cache parsed value into the MEMOIZER. It is required to have a consistent hashing.
   */
  private static class RawDateFields {

     private final String rawYear;
     private final String rawMonth;
     private final String rawDay;
     private final String rawDate;

     private RawDateFields(String rawYear, String rawMonth, String rawDay, String rawDate) {
        this.rawYear = rawYear;
        this.rawMonth = rawMonth;
        this.rawDay = rawDay;
        this.rawDate = rawDate;
     }

      @Override
     public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RawDateFields rawDateFields1 = (RawDateFields) o;
        return Objects.equals(rawYear, rawDateFields1.rawYear) &&
                 Objects.equals(rawMonth, rawDateFields1.rawMonth) &&
                 Objects.equals(rawDay, rawDateFields1.rawDay) &&
                 Objects.equals(rawDate, rawDateFields1.rawDate);
     }

     @Override
     public int hashCode() {
        return Objects.hash(rawYear, rawMonth, rawDay, rawDate);
     }

     public static RawDateFields of(String rawYear, String rawMonth, String rawDay, String rawDate) {
        return new RawDateFields(rawYear, rawMonth, rawDay, rawDate);
     }
  }

  /**
   * Private constructor.
   */
  private TemporalParser() {
    // Can't have an instance
  }

  /**
   * Performs a parser by creating a RawDateFields instance that is stores in the cache.
   */
  private static ParsedTemporalDates parseRawDate(RawDateFields rawDateFields) {
     return doParse(rawDateFields.rawYear, rawDateFields.rawMonth, rawDateFields.rawDay, rawDateFields.rawDate);
  }

  /**
   * Performs the date parsing from raw values.
   */
  private static ParsedTemporalDates doParse(String rawYear, String rawMonth, String rawDay, String rawDate) {
    // If year and rawDate are absent, return ParsedTemporalDates with NULL values inside
    if (isEmpty(rawYear) && isEmpty(rawDate)) {
      return new ParsedTemporalDates();
    }

    List<IssueType> issueList = new ArrayList<>();

    // Parse year, month and day
    ChronoAccumulator baseAccumulator = ChronoAccumulator.from(rawYear, rawMonth, rawDay);

    // Convert year, month and day parsed values
    Year year = ChronoAccumulatorConverter.getYear(baseAccumulator, issueList).orElse(null);
    Month month = ChronoAccumulatorConverter.getMonth(baseAccumulator, issueList).orElse(null);
    Integer day = ChronoAccumulatorConverter.getDay(baseAccumulator, issueList).orElse(null);
    Temporal base = TEMPORAL_FUNC.apply(baseAccumulator, issueList);

    // Base temporal instance
    ParsedTemporalDates temporalDates = new ParsedTemporalDates(year, month, day, base);

    if (isEmpty(rawDate)) {
      return temporalDates;
    }

    // Parse period
    String[] rawPeriod = DelimiterUtils.splitPeriod(rawDate);
    String rawFrom = rawPeriod[0];
    String rawTo = rawPeriod[1];
    ChronoAccumulator fromAccumulator = ParserRawDateTime.parse(rawFrom, null);
    ChronoAccumulator toAccumulator = ParserRawDateTime.parse(rawTo, fromAccumulator.getLastParsed().orElse(null));

    if (fromAccumulator.areAllNumeric() || (!isEmpty(rawTo) && toAccumulator.areAllNumeric())) {
      issueList.add(IssueType.RECORDED_DATE_INVALID);
    }

    // If toAccumulator doesn't contain last parsed value, raw date will consist of one date only
    if (toAccumulator.getLastParsed().isPresent()) {
      // Use toAccumulator value toAccumulator improve fromAccumulator parsed date
      toAccumulator.mergeAbsent(fromAccumulator);
    } else {
      // Use baseAccumulator value toAccumulator improve parsed date
      fromAccumulator.mergeReplace(baseAccumulator);
    }

    Temporal fromTemporal = TEMPORAL_FUNC.apply(fromAccumulator, issueList);
    Temporal toTemporal = TEMPORAL_FUNC.apply(toAccumulator, issueList);

    temporalDates.setFromDate(fromTemporal);
    temporalDates.setToDate(toTemporal);
    temporalDates.setIssueList(issueList);
    return temporalDates;
  }

  /**
   * Performs the parser of raw elements.
   */
  public static ParsedTemporalDates parse(String rawYear, String rawMonth, String rawDay, String rawDate) {
    return MEMOIZER.parse(RawDateFields.of(rawYear, rawMonth, rawDay, rawDate));
  }

  /**
   * Performs the parser of raw elements.
   */
  public static ParsedTemporalDates parse(String rawDate) {
    return MEMOIZER.parse(RawDateFields.of("", "", "", rawDate));
  }
}
