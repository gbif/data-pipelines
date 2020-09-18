package org.gbif.pipelines.core.interpreters.core;

import static org.gbif.common.parsers.core.ParseResult.CONFIDENCE.DEFINITE;
import static org.gbif.common.parsers.core.ParseResult.CONFIDENCE.PROBABLE;
import static org.gbif.common.parsers.date.DateComponentOrdering.DMY_FORMATS;
import static org.gbif.common.parsers.date.DateComponentOrdering.MDY_FORMATS;
import static org.gbif.pipelines.core.utils.ModelUtils.addIssue;
import static org.gbif.pipelines.core.utils.ModelUtils.addIssueSet;
import static org.gbif.pipelines.core.utils.ModelUtils.extractValue;
import static org.gbif.pipelines.core.utils.ModelUtils.hasValue;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Range;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.common.parsers.core.OccurrenceParseResult;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.common.parsers.date.AtomizedLocalDate;
import org.gbif.common.parsers.date.CustomizedTextDateParser;
import org.gbif.common.parsers.date.DateParsers;
import org.gbif.common.parsers.date.TemporalAccessorUtils;
import org.gbif.common.parsers.date.TemporalParser;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.core.config.model.PipelinesConfig;
import org.gbif.pipelines.core.parsers.temporal.utils.DelimiterUtils;
import org.gbif.pipelines.io.avro.EventDate;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;

/** Interprets date representations into a Date to support API v1 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TemporalInterpreter {

  private static final LocalDate MIN_LOCAL_DATE = LocalDate.of(1600, 1, 1);
  private static final LocalDate MIN_EPOCH_LOCAL_DATE = LocalDate.ofEpochDay(0);

  private static TemporalParser temporalParser = DateParsers.defaultTemporalParser();

  /**
   * Interpreter works on an independent VM, changing a static TemporalParser should be safe.
   *
   * <p>Set a temporal parser based on config. Support D/M/Y EU/AU or M/D/Y us formats for ambiguous
   * dates, like 01/02/2001
   *
   * @param config
   */
  public static void setTemporalParser(PipelinesConfig config) {
    if (Strings.isNullOrEmpty(config.getDefaultDateFormat())) {
      temporalParser = DateParsers.defaultTemporalParser();
    } else {
      if (config.getDefaultDateFormat().equalsIgnoreCase("DMY")) {
        temporalParser = CustomizedTextDateParser.getInstance(DMY_FORMATS);
      } else if (config.getDefaultDateFormat().equalsIgnoreCase("MDY")) {
        temporalParser = CustomizedTextDateParser.getInstance(MDY_FORMATS);
      } else {
        temporalParser = DateParsers.defaultTemporalParser();
      }
    }
  }

  /**
   * Preprocess for converting some none ISO standards to ISO standards
   *
   * @param dateString
   * @return
   */
  private static String normalizeDateString(String dateString) {
    // Convert 2004-2-1 to 3-2 , 2004-2-1 & 3-2  to 2004-2-1/3-2
    if (StringUtils.isNotEmpty(dateString)) {
      dateString = dateString.replace(" to ", "/");
      dateString = dateString.replace(" & ", "/");
    }

    return dateString;
  }

  public static void interpretTemporal(ExtendedRecord er, TemporalRecord tr) {
    interpretEventDate(er, tr);
    //    OccurrenceParseResult<TemporalAccessor> eventResult = interpretRecordedDate(er);
    //    if (eventResult.isSuccessful()) {
    //      Optional<TemporalAccessor> temporalAccessor =
    // Optional.ofNullable(eventResult.getPayload());
    //
    //      Optional<TemporalAccessor> localDate =
    //          temporalAccessor
    //              .filter(ta -> ta.isSupported(ChronoField.HOUR_OF_DAY))
    //              .map(ta -> ta.query(TemporalQueries.localDate()));
    //
    //      if (localDate.isPresent()) {
    //        temporalAccessor = localDate;
    //      }
    //
    //      temporalAccessor
    //          .map(TemporalAccessor::toString)
    //          .ifPresent(x -> tr.setEventDate(new EventDate(x, null)));
    //
    //      temporalAccessor
    //          .map(AtomizedLocalDate::fromTemporalAccessor)
    //          .ifPresent(
    //              ald -> {
    //                tr.setYear(ald.getYear());
    //                tr.setMonth(ald.getMonth());
    //                tr.setDay(ald.getDay());
    //              });
    //    }
    //    addIssueSet(tr, eventResult.getIssues());
  }

  public static void interpretModified(ExtendedRecord er, TemporalRecord tr) {
    if (hasValue(er, DcTerm.modified)) {
      LocalDate upperBound = LocalDate.now().plusDays(1);
      Range<LocalDate> validModifiedDateRange = Range.closed(MIN_EPOCH_LOCAL_DATE, upperBound);
      OccurrenceParseResult<TemporalAccessor> parsed =
          interpretLocalDate(
              extractValue(er, DcTerm.modified),
              validModifiedDateRange,
              OccurrenceIssue.MODIFIED_DATE_UNLIKELY);
      if (parsed.isSuccessful()) {
        Optional.ofNullable(parsed.getPayload())
            .map(TemporalAccessor::toString)
            .ifPresent(tr::setModified);
      }

      addIssueSet(tr, parsed.getIssues());
    }
  }

  public static void interpretDateIdentified(ExtendedRecord er, TemporalRecord tr) {
    if (hasValue(er, DwcTerm.dateIdentified)) {
      LocalDate upperBound = LocalDate.now().plusDays(1);
      Range<LocalDate> validRecordedDateRange = Range.closed(MIN_LOCAL_DATE, upperBound);
      OccurrenceParseResult<TemporalAccessor> parsed =
          interpretLocalDate(
              extractValue(er, DwcTerm.dateIdentified),
              validRecordedDateRange,
              OccurrenceIssue.IDENTIFIED_DATE_UNLIKELY);
      if (parsed.isSuccessful()) {
        Optional.ofNullable(parsed.getPayload())
            .map(TemporalAccessor::toString)
            .ifPresent(tr::setDateIdentified);
      }

      addIssueSet(tr, parsed.getIssues());
    }
  }

  private static void interpretEventDate(ExtendedRecord er, TemporalRecord tr) {
    Set<OccurrenceIssue> issues = EnumSet.noneOf(OccurrenceIssue.class);
    // Reset
    tr.setEventDate(null);

    final String year = extractValue(er, DwcTerm.year);
    final String month = extractValue(er, DwcTerm.month);
    final String day = extractValue(er, DwcTerm.day);
    String eventDateString = extractValue(er, DwcTerm.eventDate);

    boolean atomizedDateProvided =
        StringUtils.isNotBlank(year)
            || StringUtils.isNotBlank(month)
            || StringUtils.isNotBlank(day);
    boolean dateStringProvided = StringUtils.isNotBlank(eventDateString);

    if (atomizedDateProvided || dateStringProvided) {
      TemporalAccessor parsedStartTemporalAccessor;
      ParseResult.CONFIDENCE confidence;
      // parsed from YMD
      ParseResult<TemporalAccessor> parsedYMDResult =
          atomizedDateProvided ? temporalParser.parse(year, month, day) : ParseResult.fail();
      TemporalAccessor parsedYmdTa = parsedYMDResult.getPayload();
      // parse from string / possible date range
      ParseResult<TemporalAccessor> startParseResult = null;
      ParseResult<TemporalAccessor> endParseResult = null;
      TemporalAccessor from = null;
      TemporalAccessor to = null;

      eventDateString = normalizeDateString(eventDateString);
      String[] rawPeriod = DelimiterUtils.splitPeriod(eventDateString);
      // Even a single date will be split to two
      if (rawPeriod.length == 2) {
        String rawFrom = rawPeriod[0];
        String rawTo = rawPeriod[1];

        if (!Strings.isNullOrEmpty(rawFrom)) {
          startParseResult = temporalParser.parse(rawPeriod[0]);
          if (startParseResult.isSuccessful()) {
            from = startParseResult.getPayload();
          } else {
            log.debug("Event start date is invalid: {} ", rawFrom);
            addIssue(tr, OccurrenceIssue.RECORDED_DATE_INVALID);
            return;
          }
        }

        if (!Strings.isNullOrEmpty(rawTo)) {
          endParseResult = temporalParser.parse(rawPeriod[1]);
          if (endParseResult.isSuccessful()) {
            to = endParseResult.getPayload();
          } else {
            log.debug("Event end date is invalid: {} ", rawTo);
            addIssue(tr, OccurrenceIssue.RECORDED_DATE_INVALID);
            return;
          }
        }

        // Solve conflicts of dates from YMD and dateString
        if (atomizedDateProvided
            && dateStringProvided
            && !TemporalAccessorUtils.sameOrContained(parsedYMDResult.getPayload(), from)) {
          // eventDate could be ambiguous (5/4/2014), but disambiguated by year-month-day.
          boolean ambiguityResolved = false;
          if (startParseResult.getAlternativePayloads() != null) {
            for (TemporalAccessor possibleTa : startParseResult.getAlternativePayloads()) {
              if (TemporalAccessorUtils.sameOrContained(parsedYmdTa, possibleTa)) {
                from = possibleTa;
                ambiguityResolved = true;
                log.debug(
                    "Ambiguous date {} matches year-month-day date {}-{}-{} for {}",
                    rawFrom,
                    year,
                    month,
                    day,
                    from);
              }
            }
          }

          // still a conflict
          if (!ambiguityResolved) {
            log.debug("Date mismatch: [{} vs {}].", parsedYmdTa, from);
            addIssue(tr, OccurrenceIssue.RECORDED_DATE_MISMATCH);
            return;
          }

          // choose the one with better resolution
          Optional<TemporalAccessor> bestResolution =
              TemporalAccessorUtils.bestResolution(parsedYmdTa, from);
          if (bestResolution.isPresent()) {
            parsedStartTemporalAccessor = bestResolution.get();
          } else {
            // faild
            addIssue(tr, OccurrenceIssue.RECORDED_DATE_UNLIKELY);
            return;
          }
        } else {
          // they match, or we only have one anyway, choose the one with better resolution.
          parsedStartTemporalAccessor =
              TemporalAccessorUtils.bestResolution(parsedYmdTa, from).orElse(null);
        }
        // If invalid, return directly
        if (!isValidDate(parsedStartTemporalAccessor, true)) {
          if (parsedStartTemporalAccessor == null) {
            issues.add(OccurrenceIssue.RECORDED_DATE_INVALID);
          } else {
            issues.add(OccurrenceIssue.RECORDED_DATE_UNLIKELY);
          }
          log.debug("Invalid date: [{}]].", parsedStartTemporalAccessor);
          addIssueSet(tr, issues);
          return;
        }

        // extra works on it
        // Get eventDate as java.util.Date and ignore the offset (timezone) if provided
        // Note for debug: be careful if you inspect the content of 'eventDate' it will contain your
        // machine timezone.
        LocalDateTime eventStartDate =
            TemporalAccessorUtils.toEarliestLocalDateTime(parsedStartTemporalAccessor, true);
        AtomizedLocalDate atomizedLocalDate =
            AtomizedLocalDate.fromTemporalAccessor(parsedStartTemporalAccessor);

        LocalDateTime eventEndDate = TemporalAccessorUtils.toLatestLocalDateTime(to, true);

        tr.setYear(atomizedLocalDate.getYear());
        tr.setMonth(atomizedLocalDate.getMonth());
        tr.setDay(atomizedLocalDate.getDay());

        EventDate eventDate = new EventDate();
        Optional.ofNullable(eventStartDate)
            .map(LocalDateTime::toString)
            .ifPresent(x -> eventDate.setGte(x));
        Optional.ofNullable(eventEndDate)
            .map(LocalDateTime::toString)
            .ifPresent(x -> eventDate.setLte(x));

        tr.setEventDate(eventDate);

        addIssueSet(tr, issues);
      }
    }
  }

  /**
   * A convenience method that calls interpretRecordedDate with the verbatim recordedDate values
   * from the VerbatimOccurrence.
   *
   * @param er the VerbatimOccurrence containing a recordedDate
   * @return the interpretation result which is never null
   */
  @VisibleForTesting
  protected static OccurrenceParseResult<TemporalAccessor> interpretRecordedDate(
      ExtendedRecord er) {
    final String year = extractValue(er, DwcTerm.year);
    final String month = extractValue(er, DwcTerm.month);
    final String day = extractValue(er, DwcTerm.day);
    final String eventDate = extractValue(er, DwcTerm.eventDate);

    return interpretRecordedDate(year, month, day, eventDate);
  }

  /**
   * Given possibly both of year, month, day and a dateString, produces a single date. When year,
   * month and day are all populated and parseable they are given priority, but if any field is
   * missing or illegal and dateString is parseable dateString is preferred. Partially valid dates
   * are not supported and null will be returned instead. The only exception is the year alone which
   * will be used as the last resort if nothing else works. Years are verified to be before or next
   * year and after 1600. x
   *
   * @return interpretation result, never null
   */
  @VisibleForTesting
  protected static OccurrenceParseResult<TemporalAccessor> interpretRecordedDate(
      String year, String month, String day, String dateString) {

    boolean atomizedDateProvided =
        StringUtils.isNotBlank(year)
            || StringUtils.isNotBlank(month)
            || StringUtils.isNotBlank(day);
    boolean dateStringProvided = StringUtils.isNotBlank(dateString);

    if (!atomizedDateProvided && !dateStringProvided) {
      return OccurrenceParseResult.fail();
    }

    Set<OccurrenceIssue> issues = EnumSet.noneOf(OccurrenceIssue.class);

    // First, attempt year, month, day parsing
    // If the parse result is SUCCESS it means that a whole date could be extracted (with year,
    // month and day). If it is a failure but the normalizer returned a meaningful result (e.g. it
    // could extract just
    // a year) we're going to return a result with all the fields set that we could parse.
    TemporalAccessor parsedTemporalAccessor;
    ParseResult.CONFIDENCE confidence;

    ParseResult<TemporalAccessor> parsedYMDResult =
        atomizedDateProvided ? temporalParser.parse(year, month, day) : ParseResult.fail();
    ParseResult<TemporalAccessor> parsedDateResult =
        dateStringProvided ? temporalParser.parse(dateString) : ParseResult.fail();
    TemporalAccessor parsedYmdTa = parsedYMDResult.getPayload();
    TemporalAccessor parsedDateTa = parsedDateResult.getPayload();

    // If both inputs exist handle the case when they don't match
    if (atomizedDateProvided
        && dateStringProvided
        && !TemporalAccessorUtils.sameOrContained(parsedYmdTa, parsedDateTa)) {

      // eventDate could be ambiguous (5/4/2014), but disambiguated by year-month-day.
      boolean ambiguityResolved = false;
      if (parsedDateResult.getAlternativePayloads() != null) {
        for (TemporalAccessor possibleTa : parsedDateResult.getAlternativePayloads()) {
          if (TemporalAccessorUtils.sameOrContained(parsedYmdTa, possibleTa)) {
            parsedDateTa = possibleTa;
            ambiguityResolved = true;
            log.debug(
                "Ambiguous date {} matches year-month-day date {}-{}-{} for {}",
                dateString,
                year,
                month,
                day,
                parsedDateTa);
          }
        }
      }

      // still a conflict
      if (!ambiguityResolved) {
        issues.add(OccurrenceIssue.RECORDED_DATE_MISMATCH);
        log.debug("Date mismatch: [{} vs {}].", parsedYmdTa, parsedDateTa);
      }

      // choose the one with better resolution
      Optional<TemporalAccessor> bestResolution =
          TemporalAccessorUtils.bestResolution(parsedYmdTa, parsedDateTa);
      if (bestResolution.isPresent()) {
        parsedTemporalAccessor = bestResolution.get();
        // if one of the two results is null we can not set the confidence to DEFINITE
        confidence = (parsedYmdTa == null || parsedDateTa == null) ? PROBABLE : DEFINITE;
      } else {
        return OccurrenceParseResult.fail(issues);
      }
    } else {
      // they match, or we only have one anyway, choose the one with better resolution.
      parsedTemporalAccessor =
          TemporalAccessorUtils.bestResolution(parsedYmdTa, parsedDateTa).orElse(null);
      confidence =
          parsedDateTa != null ? parsedDateResult.getConfidence() : parsedYMDResult.getConfidence();
    }

    if (!isValidDate(parsedTemporalAccessor, true)) {
      if (parsedTemporalAccessor == null) {
        issues.add(OccurrenceIssue.RECORDED_DATE_INVALID);
      } else {
        issues.add(OccurrenceIssue.RECORDED_DATE_UNLIKELY);
      }

      log.debug("Invalid date: [{}]].", parsedTemporalAccessor);
      return OccurrenceParseResult.fail(issues);
    }

    return OccurrenceParseResult.success(confidence, parsedTemporalAccessor, issues);
  }

  /**
   * Check if a date express as TemporalAccessor falls between the predefined range. Lower bound
   * defined by {@link #MIN_LOCAL_DATE} and upper bound by current date + 1 day
   *
   * @return valid or not according to the predefined range.
   */
  @VisibleForTesting
  protected static boolean isValidDate(
      TemporalAccessor temporalAccessor, boolean acceptPartialDate) {
    LocalDate upperBound = LocalDate.now().plusDays(1);
    return isValidDate(
        temporalAccessor, acceptPartialDate, Range.closed(MIN_LOCAL_DATE, upperBound));
  }

  /** Check if a date express as TemporalAccessor falls between the provided range. */
  private static boolean isValidDate(
      TemporalAccessor temporalAccessor, boolean acceptPartialDate, Range<LocalDate> likelyRange) {

    if (temporalAccessor == null) {
      return false;
    }

    if (!acceptPartialDate) {
      LocalDate localDate = temporalAccessor.query(TemporalQueries.localDate());
      if (localDate == null) {
        return false;
      }
      return likelyRange.contains(localDate);
    }

    // if partial dates should be considered valid
    int year;
    int month = 1;
    int day = 1;
    if (temporalAccessor.isSupported(ChronoField.YEAR)) {
      year = temporalAccessor.get(ChronoField.YEAR);
    } else {
      return false;
    }

    if (temporalAccessor.isSupported(ChronoField.MONTH_OF_YEAR)) {
      month = temporalAccessor.get(ChronoField.MONTH_OF_YEAR);
    }

    if (temporalAccessor.isSupported(ChronoField.DAY_OF_MONTH)) {
      day = temporalAccessor.get(ChronoField.DAY_OF_MONTH);
    }

    return likelyRange.contains(LocalDate.of(year, month, day));
  }

  /** @return TemporalAccessor that represents a LocalDate or LocalDateTime */
  private static OccurrenceParseResult<TemporalAccessor> interpretLocalDate(
      String dateString, Range<LocalDate> likelyRange, OccurrenceIssue unlikelyIssue) {
    if (!Strings.isNullOrEmpty(dateString)) {
      OccurrenceParseResult<TemporalAccessor> result =
          new OccurrenceParseResult<>(temporalParser.parse(dateString));
      // check year makes sense
      if (result.isSuccessful() && !isValidDate(result.getPayload(), true, likelyRange)) {
        log.debug("Unlikely date parsed, ignore [{}].", dateString);
        result.addIssue(unlikelyIssue);
      }
      return result;
    }
    return OccurrenceParseResult.fail();
  }
}
