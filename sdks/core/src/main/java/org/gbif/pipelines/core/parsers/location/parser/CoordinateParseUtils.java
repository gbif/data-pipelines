package org.gbif.pipelines.core.parsers.location.parser;

import static org.gbif.api.vocabulary.OccurrenceIssue.COORDINATE_INVALID;
import static org.gbif.api.vocabulary.OccurrenceIssue.COORDINATE_OUT_OF_RANGE;
import static org.gbif.api.vocabulary.OccurrenceIssue.COORDINATE_ROUNDED;
import static org.gbif.api.vocabulary.OccurrenceIssue.PRESUMED_SWAPPED_COORDINATE;
import static org.gbif.api.vocabulary.OccurrenceIssue.ZERO_COORDINATE;

import com.google.common.base.Strings;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.gbif.common.parsers.NumberParser;
import org.gbif.kvs.geocode.LatLng;
import org.gbif.pipelines.core.parsers.common.ParsedField;

/** Utilities for assisting in the parsing of latitude and longitude strings into Decimals. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CoordinateParseUtils {

  private static final String DMS =
      "\\s*(\\d{1,3})\\s*[°dº go]" // The degrees
          + "\\s*([0-6]?\\d)\\s*['m ´’′]" // The minutes
          + "\\s*(?:" // Non-capturing group
          + "([0-6]?\\d(?:[,.]\\d+)?)" // Seconds and optional decimal
          + "\\s*(?:\"|''|s|´´|″)?"
          + ")?\\s*";
  private static final String DM =
      "\\s*(\\d{1,3})\\s*[°dº go]" // The degrees
          + "\\s*(?:" // Non-capturing group
          + "([0-6]?\\d(?:[,.]\\d+)?)" // Minutes and optional decimal
          + "\\s*['m ´’′]?"
          + ")?\\s*";
  private static final String D =
      "\\s*(\\d{1,3}(?:[,.]\\d+)?)\\s*(?:°|d|º| |g|o|)\\s*"; // The degrees and optional decimal
  private static final Pattern DMS_SINGLE =
      Pattern.compile("^" + DMS + "$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DM_SINGLE =
      Pattern.compile("^" + DM + "$", Pattern.CASE_INSENSITIVE);
  private static final Pattern D_SINGLE = Pattern.compile("^" + D + "$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DMS_COORD =
      Pattern.compile(
          "^" + DMS + "([NSEOW])" + "[ ,;/]?" + DMS + "([NSEOW])$", Pattern.CASE_INSENSITIVE);
  private static final Pattern POINT_FOOTPRINT =
      Pattern.compile(
          "^\\s*POINT\\s*\\("
              + "\\s*(-?\\d{1,3}(?:[.]\\d+)?)"
              + "\\s*[ ,;/]\\s*"
              + "\\s*(-?\\d{1,3}(?:[.]\\d+)?)"
              + "\\s*\\)\\s*$",
          Pattern.CASE_INSENSITIVE);
  private static final String POSITIVE = "NEO";

  /**
   * This parses string representations of latitude and longitude values. It tries its best to
   * interpret the values and indicates any problems in its result as {@link String}. When the
   * {@link ParsedField} is not successful the payload will be null and one or more issues should be
   * set in {@link ParsedField#getIssues()}.
   *
   * <p>Coordinate precision will be 6 decimals at most, any more precise values will be rounded.
   *
   * <p>Supported standard formats are the following, with dots or optionally a comma as the decimal
   * marker:
   *
   * <ul>
   *   <li>43.63871944444445
   *   <li>N43°38'19.39"
   *   <li>43°38'19.39"N
   *   <li>43°38.3232'N
   *   <li>43d 38m 19.39s N
   *   <li>43 38 19.39
   *   <li>433819N
   * </ul>
   *
   * @param latitude The decimal latitude
   * @param longitude The decimal longitude
   * @return The parse result
   */
  public static ParsedField<LatLng> parseLatLng(final String latitude, final String longitude) {
    if (Strings.isNullOrEmpty(latitude) || Strings.isNullOrEmpty(longitude)) {
      return ParsedField.fail();
    }
    Double lat = NumberParser.parseDouble(latitude);
    Double lng = NumberParser.parseDouble(longitude);
    if (lat == null || lng == null) {
      // try degree minute seconds
      try {
        lat = parseDMS(latitude, true);
        lng = parseDMS(longitude, false);
      } catch (IllegalArgumentException e) {
        return ParsedField.fail(COORDINATE_INVALID.name());
      }
    }

    return validateAndRound(lat, lng);
  }

  private static boolean inRange(double lat, double lon) {
    return Double.compare(lat, 90) <= 0
        && Double.compare(lat, -90) >= 0
        && Double.compare(lon, 180) <= 0
        && Double.compare(lon, -180) >= 0;
  }

  private static boolean isLat(String direction) {
    return "NS".contains(direction.toUpperCase());
  }

  private static int coordSign(String direction) {
    return POSITIVE.contains(direction.toUpperCase()) ? 1 : -1;
  }

  // 02° 49' 52" N	131° 47' 03" E
  public static ParsedField<LatLng> parseVerbatimCoordinates(final String coordinates) {
    if (Strings.isNullOrEmpty(coordinates)) {
      return ParsedField.fail();
    }
    Matcher m = DMS_COORD.matcher(coordinates);
    if (m.find()) {
      final String dir1 = m.group(4);
      final String dir2 = m.group(8);
      // first parse coords regardless whether they are lat or lon
      double c1 = coordFromMatcher(m, 1, 2, 3, dir1);
      double c2 = coordFromMatcher(m, 5, 6, 7, dir2);
      // now see what order the coords are in:
      if (isLat(dir1) && !isLat(dir2)) {
        return validateAndRound(c1, c2);

      } else if (!isLat(dir1) && isLat(dir2)) {
        return validateAndRound(c2, c1);

      } else {
        return ParsedField.fail(COORDINATE_INVALID.name());
      }

    } else if (coordinates.length() > 4) {
      // try to split and then use lat/lon parsing
      for (final char delim : ",;/ ".toCharArray()) {
        int cnt = StringUtils.countMatches(coordinates, String.valueOf(delim));
        if (cnt == 1) {
          String[] latlon = StringUtils.split(coordinates, delim);
          if (latlon.length == 2) {
            return parseLatLng(latlon[0], latlon[1]);
          }
        }
      }
    }

    return ParsedField.fail(COORDINATE_INVALID.name());
  }

  /**
   * Parse a POINT(long lat) value from the footprintWKT, but fail silently if one is not found —
   * this field can contain lines, polygons and coordinates in different projections.
   */
  // POINT(30.29 -23.2)
  public static ParsedField<LatLng> parsePointFootprintWKT(final String footprint) {
    if (Strings.isNullOrEmpty(footprint)) {
      return ParsedField.fail();
    }
    Matcher m = POINT_FOOTPRINT.matcher(footprint);
    if (m.find()) {
      // assume WKT order (longitude, latitude)
      Double lng = NumberParser.parseDouble(m.group(1));
      Double lat = NumberParser.parseDouble(m.group(2));
      ParsedField<LatLng> result = validateAndRound(lat, lng);
      if (result.isSuccessful()) {
        return result;
      }
    }

    return ParsedField.fail();
  }

  private static ParsedField<LatLng> validateAndRound(double lat, double lon) {
    // collecting issues for result
    Set<String> issues = new TreeSet<>();

    // round to 6 decimals
    final double latOrig = lat;
    final double lngOrig = lon;
    lat = roundTo6decimals(lat);
    lon = roundTo6decimals(lon);
    if (Double.compare(lat, latOrig) != 0) {
      issues.add(COORDINATE_ROUNDED.name());
    }
    if (Double.compare(lon, lngOrig) != 0) {
      issues.add(COORDINATE_ROUNDED.name());
    }

    // 0,0 is too suspicious
    if (Double.compare(lat, 0) == 0 && Double.compare(lon, 0) == 0) {
      issues.add(ZERO_COORDINATE.name());
      return ParsedField.success(new LatLng(0.0d, 0.0d), issues);
    }

    // if everything falls in range
    if (inRange(lat, lon)) {
      return ParsedField.success(new LatLng(lat, lon), issues);
    }

    // if lat is out of range, but in range of the lng,
    // assume swapped coordinates.
    // note that should we desire to trust the following records, we would need to clear the flag
    // for the records to
    // appear in
    // search results and maps etc. however, this is logic decision, that goes above the
    // capabilities of this method
    if ((Double.compare(lat, 90) > 0 || Double.compare(lat, -90) < 0) && inRange(lon, lat)) {
      issues.add(PRESUMED_SWAPPED_COORDINATE.name());
      return ParsedField.success(new LatLng(lon, lat), issues);
    }

    // then something is out of range
    issues.add(COORDINATE_OUT_OF_RANGE.name());
    return ParsedField.fail(issues);
  }

  /**
   * Parses a single DMS coordinate
   *
   * @return the converted decimal up to 6 decimals accuracy
   */
  private static double parseDMS(String coord, boolean lat) {
    final String DIRS = lat ? "NS" : "EOW";
    coord = coord.trim().toUpperCase();

    if (coord.length() > 3) {
      // preparse the direction and remove it from the string to avoid a very complex regex
      char dir = 'n';
      if (DIRS.contains(String.valueOf(coord.charAt(0)))) {
        dir = coord.charAt(0);
        coord = coord.substring(1);
      } else if (DIRS.contains(String.valueOf(coord.charAt(coord.length() - 1)))) {
        dir = coord.charAt(coord.length() - 1);
        coord = coord.substring(0, coord.length() - 1);
      }
      // without the direction chuck it at the regex
      Matcher m = DMS_SINGLE.matcher(coord);
      if (m.find()) {
        return coordFromMatcher(m, 1, 2, 3, String.valueOf(dir));
      } else {
        m = DM_SINGLE.matcher(coord);
        if (m.find()) {
          return coordFromMatcher(m, 1, 2, String.valueOf(dir));
        } else {
          m = D_SINGLE.matcher(coord);
          if (m.find()) {
            return coordFromMatcher(m, 1, String.valueOf(dir));
          }
        }
      }
    }
    throw new IllegalArgumentException("Coordinates could not be parsed: " + coord);
  }

  private static double coordFromMatcher(Matcher m, int idx1, int idx2, int idx3, String sign) {
    return roundTo6decimals(
        coordSign(sign)
            * dmsToDecimal(
                NumberParser.parseDouble(m.group(idx1)),
                NumberParser.parseDouble(m.group(idx2)),
                NumberParser.parseDouble(m.group(idx3))));
  }

  private static double coordFromMatcher(Matcher m, int idx1, int idx2, String sign) {
    return roundTo6decimals(
        coordSign(sign)
            * dmsToDecimal(
                NumberParser.parseDouble(m.group(idx1)),
                NumberParser.parseDouble(m.group(idx2)),
                0.0));
  }

  private static double coordFromMatcher(Matcher m, int idx1, String sign) {
    return roundTo6decimals(
        coordSign(sign) * dmsToDecimal(NumberParser.parseDouble(m.group(idx1)), 0.0, 0.0));
  }

  private static double dmsToDecimal(double degree, Double minutes, Double seconds) {
    minutes = minutes == null ? Double.valueOf(0d) : minutes;
    seconds = seconds == null ? Double.valueOf(0d) : seconds;
    return degree + (minutes / 60) + (seconds / 3600);
  }

  // Round to 6 decimals (~1m precision) since no way we're getting anything legitimately more
  // precise
  private static Double roundTo6decimals(Double x) {
    return x == null ? null : Math.round(x * Math.pow(10, 6)) / Math.pow(10, 6);
  }
}
