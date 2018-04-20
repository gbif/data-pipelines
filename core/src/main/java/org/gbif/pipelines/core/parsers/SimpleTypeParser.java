package org.gbif.pipelines.core.parsers;


import com.google.common.base.Function;
import org.gbif.common.parsers.BooleanParser;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.core.parsers.memoize.ParserMemoizer;
import org.gbif.pipelines.io.avro.ExtendedRecord;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Utility class that parses basic data types.
 */
public class SimpleTypeParser {


  //Caching instance of BooleanParser since it is a file based parser
  private static final ParserMemoizer<String,ParseResult<Boolean>> BOOLEAN_PARSER = ParserMemoizer.memoize(BooleanParser.getInstance());

  private static final ParserMemoizer<String,Integer> INTEGER_PARSER = ParserMemoizer.integerMemoizer();

  private static final ParserMemoizer<String,Double> DOUBLE_PARSER = ParserMemoizer.doubleMemoizer();

  /**
   * Utility class.
   */
  private SimpleTypeParser() {
    // hidden constructor
  }

  /**
   * Parses an integer value and consumes its response (if any).
   */
  public static void parseInt(ExtendedRecord extendedRecord, DwcTerm term, Consumer<Optional<Integer>> consumer) {
    Optional
      .ofNullable(extendedRecord.getCoreTerms().get(term.qualifiedName()))
      .ifPresent(termValue -> consumer.accept(Optional.ofNullable(INTEGER_PARSER.parse(termValue))));
  }

  /**
   * Parses an integer value and applies a mapping function to its response (if any).
   */
  public static <U> U parseInt(ExtendedRecord extendedRecord, DwcTerm term, Function<Optional<Integer>, U> mapper) {
    return Optional
            .ofNullable(extendedRecord.getCoreTerms().get(term.qualifiedName()))
            .map(termValue -> mapper.apply(Optional.ofNullable(INTEGER_PARSER.parse(termValue))))
            .get();
  }

  /**
   * Parses a double value and consumes its response (if any).
   */
  public static void parseDouble(ExtendedRecord extendedRecord, DwcTerm term, Consumer<Optional<Double>> consumer) {
    Optional
      .ofNullable(extendedRecord.getCoreTerms().get(term.qualifiedName()))
      .ifPresent(termValue -> consumer.accept(Optional.ofNullable(DOUBLE_PARSER.parse(termValue))));
  }

  /**
   * Parses a double value and applies a mapping function to its response (if any).
   */
  public static <U> U parseDouble(ExtendedRecord extendedRecord, DwcTerm term, Function<Optional<Double>, U> mapper) {
    return Optional
            .ofNullable(extendedRecord.getCoreTerms().get(term.qualifiedName()))
            .map(termValue -> mapper.apply(Optional.ofNullable(DOUBLE_PARSER.parse(termValue))))
            .get();
  }


  /**
   * Parses a boolean value and consumes its response (if any).
   */
  public static void parseBoolean(ExtendedRecord extendedRecord, DwcTerm term, Consumer<ParseResult<Boolean>> consumer) {
    Optional
      .ofNullable(extendedRecord.getCoreTerms().get(term.qualifiedName()))
      .ifPresent(termValue -> consumer.accept(BOOLEAN_PARSER.parse(termValue)));
  }

  /**
   * Parses a boolean value and applies mapping functions to its response (if any).
   */
  public static <U> U parseBoolean(ExtendedRecord extendedRecord, DwcTerm term, Function<ParseResult<Boolean>,U> mapper) {
    return Optional
            .ofNullable(extendedRecord.getCoreTerms().get(term.qualifiedName()))
            .map(termValue -> mapper.apply(BOOLEAN_PARSER.parse(termValue)))
            .get();
  }

}
