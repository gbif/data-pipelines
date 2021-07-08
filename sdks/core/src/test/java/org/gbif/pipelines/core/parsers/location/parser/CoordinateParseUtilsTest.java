package org.gbif.pipelines.core.parsers.location.parser;

import static org.gbif.api.vocabulary.OccurrenceIssue.*;
import static org.junit.Assert.*;

import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.kvs.geocode.LatLng;
import org.gbif.pipelines.core.parsers.common.ParsedField;
import org.junit.Test;

public class CoordinateParseUtilsTest {

  @Test
  public void testParseLatLng() {
    assertExpected(
        CoordinateParseUtils.parseLatLng("-46,33", "51,8717"), new LatLng(-46.33, 51.8717));
    assertExpected(CoordinateParseUtils.parseLatLng("10.3", "99.99"), new LatLng(10.3, 99.99));
    assertExpected(CoordinateParseUtils.parseLatLng("10", "10"), new LatLng(10d, 10d));
    assertExpected(CoordinateParseUtils.parseLatLng("90", "180"), new LatLng(90d, 180d));
    assertExpected(CoordinateParseUtils.parseLatLng("-90", "180"), new LatLng(-90d, 180d));
    assertExpected(CoordinateParseUtils.parseLatLng("90", "-180"), new LatLng(90d, -180d));
    assertExpected(CoordinateParseUtils.parseLatLng("-90", "-180"), new LatLng(-90d, -180d));
    assertExpected(CoordinateParseUtils.parseLatLng("0", "0"), new LatLng(0d, 0d), ZERO_COORDINATE);

    // rounding
    assertExpected(
        CoordinateParseUtils.parseLatLng("2.123450678", "-8.123450678"),
        new LatLng(2.123451, -8.123451),
        COORDINATE_ROUNDED);
    assertExpected(
        CoordinateParseUtils.parseLatLng("2.123451", "-8.123450678"),
        new LatLng(2.123451, -8.123451),
        COORDINATE_ROUNDED);
    assertExpected(
        CoordinateParseUtils.parseLatLng("2.123451", "-8.123451"), new LatLng(2.123451, -8.123451));
    assertExpected(
        CoordinateParseUtils.parseLatLng("2.12345100", "-8.1234510"),
        new LatLng(2.123451, -8.123451));
    assertExpected(
        CoordinateParseUtils.parseLatLng("2.123", "-8.1234506"),
        new LatLng(2.123, -8.123451),
        COORDINATE_ROUNDED);

    // degree minutes seconds
    assertExpected(
        CoordinateParseUtils.parseLatLng("02° 49' 52\" N", "131° 47' 03\" E"),
        new LatLng(2.831111d, 131.784167d));

    // check swapped coordinates
    assertExpected(
        CoordinateParseUtils.parseLatLng("100", "40"),
        new LatLng(40d, 100d),
        PRESUMED_SWAPPED_COORDINATE);
    assertExpected(
        CoordinateParseUtils.parseLatLng("-100", "90"),
        new LatLng(90d, -100d),
        PRESUMED_SWAPPED_COORDINATE);

    // check errors
    assertFailed(CoordinateParseUtils.parseLatLng("", "30"));
    assertFailedWithIssues(CoordinateParseUtils.parseLatLng("tim", "tom"), COORDINATE_INVALID);
    assertFailedWithIssues(
        CoordinateParseUtils.parseLatLng("20,432,12", "13,4"), COORDINATE_INVALID);
    assertFailedWithIssues(CoordinateParseUtils.parseLatLng("200", "200"), COORDINATE_OUT_OF_RANGE);
    assertFailedWithIssues(CoordinateParseUtils.parseLatLng("-200", "30"), COORDINATE_OUT_OF_RANGE);
    assertFailedWithIssues(CoordinateParseUtils.parseLatLng("200", "30"), COORDINATE_OUT_OF_RANGE);
    assertFailedWithIssues(
        CoordinateParseUtils.parseLatLng("20.432,12", "13,4"), COORDINATE_OUT_OF_RANGE);
    assertFailedWithIssues(
        CoordinateParseUtils.parseLatLng("20,432,12", "13,4"), COORDINATE_INVALID);
  }

  @Test
  public void testParseDMS() {
    assertDMS("2°49'N", "131°47'E", 2.816667d, 131.783333d);

    assertDMS("02° 49' 52\" N", "131° 47' 03\" E", 2.831111d, 131.784167d);
    assertDMS("2°49'52\"S", "131°47'03\" W", -2.831111d, -131.784167d);
    assertDMS("2°49'52\"  n", "131°47'03\"  O", 2.831111d, 131.784167d);
    assertDMS("002°49'52\"N", "131°47'03\"E", 2.831111d, 131.784167d);
    assertDMS("2°49'N", "131°47'E", 2.816667d, 131.783333d);
    assertDMS("002°49'52''N", "131°47'03''E", 2.831111d, 131.784167d);
    assertDMS("6º39'36\"S", "35º59'59\"W", -6.66d, -35.999722);
    assertDMS("17  02.877 N", "121  05.966 E", 17.04795, 121.099433);
    assertDMS("08º37'S", "37º10'W", -8.616667, -37.166667);
    assertDMS("39g30mS", "56g27mW", -39.5, -56.45);
    assertDMS("42g24m50.00sS", "64g17m20.00sW", -42.413889, -64.288889);
    assertDMS("42º30´S", "54º14´W", -42.5, -54.233333);
    assertDMS("61o50'N", "30o45'E", 61.833333, 30.75);
    assertDMS("07°35N", "38°44E", 7.583333, 38.733333);
    assertDMS("29º32.3’N", "113º34.9’W", 29.538333, -113.581667);
    assertDMS("5°45′30″N", "100º30′30″W", 5.758333, -100.508333);
    assertDMS("13.1939 N", "59.5432 W", 13.1939, -59.5432);
    assertDMS("24 06.363 N", "110 11.969 E", 24.10605d, 110.199483d);
    assertDMS("N36.93276", "W102.96361", 36.93276, -102.96361);
    assertDMS("90°N", "180°", 90, 180);

    // truly failing
    assertIllegalArg("12344");
    assertIllegalArg("432");
    assertIllegalArg(" ");
    assertIllegalArg(" ");
    assertIllegalArg("131°47'132\"");
    assertIllegalArg("131 47 132");
    assertIllegalArg("131°47'132\"");
    assertIllegalArg("043300S");
    assertIllegalArg("0433S");
    assertIllegalArg("043300 S");
    assertIllegalArg("0433 S");
  }

  @Test
  public void testParseFootprint() {
    assertFootprint("POINT(131.78 2.8167)", 2.8167, 131.78);
    assertFootprint("\tPoint ( -54.23333 ,\t-42.5 ) ", -42.5, -54.23333);
    assertFootprint("POINT(180 90)", 90, 180);

    // Not valid, single points.
    assertBadFootprint("POINT(1234 5678)");
    assertBadFootprint("POINT((40 40))");
    assertBadFootprint("POINT( )");
    assertBadFootprint("POINT (130.14606 33.07453) POINT(130.12915 33.11009)");
    assertBadFootprint("MULTIPOINT (130.14606 33.07453, 130.12915 33.11009, 130.12188 33.12365)");
  }

  private void assertDMS(String lat, String lon, double eLat, double eLon) {
    ParsedField<LatLng> result = CoordinateParseUtils.parseLatLng(lat, lon);
    assertEquals(eLat, result.getResult().getLatitude(), 0.000001);
    assertEquals(eLon, result.getResult().getLongitude(), 0.000001);
  }

  private void assertIllegalArg(String coord) {
    assertFailed(CoordinateParseUtils.parseLatLng(coord, coord));
  }

  private void assertFootprint(String footprint, double eLat, double eLon) {
    ParsedField<LatLng> result = CoordinateParseUtils.parsePointFootprintWKT(footprint);
    System.out.println(result.getResult());
    assertEquals(eLat, result.getResult().getLatitude(), 0.000001);
    assertEquals(eLon, result.getResult().getLongitude(), 0.000001);
  }

  private void assertBadFootprint(String footprint) {
    assertFailed(CoordinateParseUtils.parsePointFootprintWKT(footprint));
  }

  @Test
  public void testParseVerbatimCoordinates() {
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("02° 49' 52\" N 131° 47' 03\" E"),
        new LatLng(2.831111d, 131.784167d));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("02° 49' 52\" N, 131° 47' 03\" E"),
        new LatLng(2.831111d, 131.784167d));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("02°49'52\"N; 131°47'03\"O"),
        new LatLng(2.831111d, 131.784167d));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("17d 33m 5s N/99d 30m 3s W"),
        new LatLng(17.551389d, -99.500833d));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("14.93333/-91.9"),
        new LatLng(14.93333d, -91.9d));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("63d 41m 39s N 170d 28m 44s W"),
        new LatLng(63.694167d, -170.478889d));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("37° 28' N, 122° 6' W"),
        new LatLng(37.466667d, -122.1d));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("2°49'52\"N, 131°47'03\""),
        new LatLng(2.831111d, 131.784167d));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("10°07'06\"N 20°48'23\"W"),
        new LatLng(10.118333, -20.806389));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("10º07'06\"N 20º48'23\"W"),
        new LatLng(10.118333, -20.806389));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("10°07'N 20°48'W"),
        new LatLng(10.116667, -20.8));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("10°07.55'N 20°48.55'W"),
        new LatLng(10.125833, -20.809167));
    assertExpected(
        CoordinateParseUtils.parseVerbatimCoordinates("100º23'05\"N 20º35'25\"W"),
        new LatLng(-20.590278, 100.384722),
        PRESUMED_SWAPPED_COORDINATE);

    // failed
    assertFailed(CoordinateParseUtils.parseVerbatimCoordinates(""));
    assertFailedWithIssues(
        CoordinateParseUtils.parseVerbatimCoordinates("12344"), COORDINATE_INVALID);
    assertFailedWithIssues(CoordinateParseUtils.parseVerbatimCoordinates(" "), COORDINATE_INVALID);
    assertFailedWithIssues(
        CoordinateParseUtils.parseVerbatimCoordinates(",11.12"), COORDINATE_INVALID);
    assertFailedWithIssues(
        CoordinateParseUtils.parseVerbatimCoordinates("122°49'52\"N, 131°47'03\"E"),
        COORDINATE_OUT_OF_RANGE);
  }

  private void assertExpected(ParsedField<LatLng> pr, Object expected, OccurrenceIssue... issue) {
    assertNotNull(pr);
    assertTrue(pr.isSuccessful());
    assertNotNull(pr.getResult());
    assertEquals(expected, pr.getResult());
    if (issue == null) {
      assertTrue(pr.getIssues().isEmpty());
    } else {
      assertEquals(issue.length, pr.getIssues().size());
      for (OccurrenceIssue iss : issue) {
        assertTrue(pr.getIssues().contains(iss.name()));
      }
    }
  }

  private void assertFailed(ParsedField<LatLng> pr) {
    assertNotNull(pr);
    assertFalse(pr.isSuccessful());
  }

  private void assertFailedWithIssues(ParsedField<LatLng> pr, OccurrenceIssue... issue) {
    assertFailed(pr);
    assertEquals(issue.length, pr.getIssues().size());
    System.out.println(pr.getIssues());
    for (OccurrenceIssue iss : issue) {
      assertTrue(pr.getIssues().contains(iss.name()));
    }
  }

  @Test
  public void parsePointFootprintWKTTest() {
    ParsedField<LatLng> result = CoordinateParseUtils.parseLatLng("-43.9293", "+12.09");
    System.out.println(result.getResult());

    result = CoordinateParseUtils.parsePointFootprintWKT("POINT((12.09 -43.9293))");
    System.out.println(result.getResult());
  }
}
