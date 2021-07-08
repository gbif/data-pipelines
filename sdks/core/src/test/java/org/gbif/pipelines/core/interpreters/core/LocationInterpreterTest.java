package org.gbif.pipelines.core.interpreters.core;

import static org.gbif.api.vocabulary.OccurrenceIssue.*;
import static org.gbif.pipelines.core.interpreters.core.LocationInterpreter.hasGeospatialIssues;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gbif.api.vocabulary.Country;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.kvs.KeyValueStore;
import org.gbif.kvs.geocode.LatLng;
import org.gbif.pipelines.core.interpreters.Interpretation;
import org.gbif.pipelines.core.interpreters.KeyValueTestStore;
import org.gbif.pipelines.core.parsers.location.GeocodeKvStore;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.IssueRecord;
import org.gbif.pipelines.io.avro.LocationRecord;
import org.gbif.pipelines.io.avro.MetadataRecord;
import org.gbif.rest.client.geocode.GeocodeResponse;
import org.gbif.rest.client.geocode.Location;
import org.junit.Assert;
import org.junit.Test;

public class LocationInterpreterTest {

  private static final KeyValueStore<LatLng, GeocodeResponse> KEY_VALUE_STORE;

  private static final String ID = "777";

  static {
    KeyValueTestStore store = new KeyValueTestStore();
    store.put(new LatLng(15.958333d, -85.908333d), toGeocodeResponse(Country.HONDURAS));
    store.put(new LatLng(35.891353d, -99.721925d), toGeocodeResponse(Country.UNITED_STATES));
    store.put(new LatLng(34.69545d, -94.65836d), toGeocodeResponse(Country.UNITED_STATES));
    store.put(new LatLng(-2.752778d, -58.653057d), toGeocodeResponse(Country.BRAZIL));
    store.put(new LatLng(-6.623889d, -45.869164d), toGeocodeResponse(Country.BRAZIL));
    store.put(new LatLng(-17.05d, -66d), toGeocodeResponse(Country.BOLIVIA));
    store.put(new LatLng(-8.023319, 110.279078), toGeocodeResponse(Country.INDONESIA));
    KEY_VALUE_STORE = GeocodeKvStore.create(store);
  }

  private static GeocodeResponse toGeocodeResponse(Country country) {
    Location location = new Location();
    location.setType("Political");
    location.setDistance(0.0d);
    location.setIsoCountryCode2Digit(country.getIso2LetterCode());
    return new GeocodeResponse(Collections.singletonList(location));
  }

  private static ExtendedRecord createEr(
      String country,
      String countryCode,
      String verbatimLatitude,
      String verbatimLongitude,
      String decimalLatitude,
      String decimalLongitude) {

    Map<String, String> coreMap = new HashMap<>();
    coreMap.put(DwcTerm.country.qualifiedName(), country);
    coreMap.put(DwcTerm.countryCode.qualifiedName(), countryCode);
    coreMap.put(DwcTerm.verbatimLatitude.qualifiedName(), verbatimLatitude);
    coreMap.put(DwcTerm.verbatimLongitude.qualifiedName(), verbatimLongitude);
    coreMap.put(DwcTerm.decimalLatitude.qualifiedName(), decimalLatitude);
    coreMap.put(DwcTerm.decimalLongitude.qualifiedName(), decimalLongitude);
    return ExtendedRecord.newBuilder().setId(ID).setCoreTerms(coreMap).build();
  }

  private static LocationRecord createLr(
      Country country, Double decimalLatitude, Double decimalLongitude, OccurrenceIssue... issues) {

    List<String> issueList =
        issues.length > 0
            ? Arrays.stream(issues).map(OccurrenceIssue::name).collect(Collectors.toList())
            : Collections.emptyList();

    boolean hasCoordinate = decimalLatitude != null && decimalLongitude != null;

    LocationRecord record =
        LocationRecord.newBuilder()
            .setId(ID)
            .setCountry(Optional.ofNullable(country).map(Country::getTitle).orElse(null))
            .setCountryCode(
                Optional.ofNullable(country).map(Country::getIso2LetterCode).orElse(null))
            .setDecimalLatitude(decimalLatitude)
            .setDecimalLongitude(decimalLongitude)
            .setHasCoordinate(hasCoordinate)
            .setIssues(IssueRecord.newBuilder().setIssueList(issueList).build())
            .build();

    record.setHasGeospatialIssue(hasGeospatialIssues(record));

    return record;
  }

  private static LocationRecord interpret(ExtendedRecord source) {
    MetadataRecord mdr = MetadataRecord.newBuilder().setId(ID).build();
    return Interpretation.from(source)
        .to(er -> LocationRecord.newBuilder().setId(er.getId()).build())
        .via(LocationInterpreter.interpretCountryAndCoordinates(KEY_VALUE_STORE, mdr))
        .getOfNullable()
        .orElse(null);
  }

  @Test
  public void issueDatumAndRoundedBrazilTest() {

    // State
    ExtendedRecord source = createEr("Brazil", null, null, null, "-2.7527778", "-58.653057");
    LocationRecord expected =
        createLr(
            Country.BRAZIL,
            -2.752778d,
            -58.653057d,
            COORDINATE_ROUNDED,
            GEODETIC_DATUM_ASSUMED_WGS84);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueDatumBrazilTest() {

    // State
    ExtendedRecord source = createEr("Brazil", null, null, null, "-6.623889", "-45.869164");
    LocationRecord expected =
        createLr(Country.BRAZIL, -6.623889d, -45.869164d, GEODETIC_DATUM_ASSUMED_WGS84);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueDatumUsTest() {

    // State
    ExtendedRecord source =
        createEr("United States", null, "34.695450000000001", "-94.658360000000002", null, null);
    LocationRecord expected =
        createLr(Country.UNITED_STATES, 34.69545d, -94.65836d, GEODETIC_DATUM_ASSUMED_WGS84);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueDatumAndNegatedUsTest() {

    // State
    ExtendedRecord source =
        createEr("United States", null, "35.8913528", "99.721924999999999", null, null);
    LocationRecord expected =
        createLr(
            Country.UNITED_STATES,
            35.891353d,
            -99.721925d,
            COORDINATE_ROUNDED,
            GEODETIC_DATUM_ASSUMED_WGS84,
            PRESUMED_NEGATED_LONGITUDE);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueCountryFromCoordinateTest() {

    // State
    ExtendedRecord source =
        createEr(null, null, "15° 57' 30\" N", "85° 54' 30\" W", "15.9583333333", "-85.9083333333");
    LocationRecord expected =
        createLr(
            Country.HONDURAS,
            15.958333d,
            -85.908333d,
            COORDINATE_ROUNDED,
            COUNTRY_DERIVED_FROM_COORDINATES,
            GEODETIC_DATUM_ASSUMED_WGS84);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueInvalidCoordinateTest() {

    // State
    ExtendedRecord source = createEr(null, null, null, null, "Nova Teutonia", "Seara");
    LocationRecord expected = createLr(null, null, null, COORDINATE_INVALID);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueInvalidCoordinateAndCountryTest() {

    // State
    ExtendedRecord source = createEr("11", null, null, null, "Nova Teutonia", "Seara");
    LocationRecord expected = createLr(null, null, null, COORDINATE_INVALID, COUNTRY_INVALID);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueInvalidCoordinateFromVerbatimTest() {

    // State
    ExtendedRecord source =
        createEr(
            null, null, "15 7 3.677 N ; 15 6 37.801 N", "92 6 8.069 W ; 92 6 33.832 W", null, null);
    LocationRecord expected = createLr(null, null, null, COORDINATE_INVALID);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueInvalidCoordinateColombiaTest() {

    // State
    ExtendedRecord source = createEr("Colombia", "CO", null, null, "4.594732.", "-74070495.");
    LocationRecord expected = createLr(Country.COLOMBIA, null, null, COORDINATE_INVALID);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void issueDatumBoliviaTest() {

    // State
    ExtendedRecord source = createEr("Bolivia", null, "17 03  S", "066   W", null, null);
    LocationRecord expected =
        createLr(Country.BOLIVIA, -17.05d, -66d, GEODETIC_DATUM_ASSUMED_WGS84);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void presumedSwappedCoordinatesTest() {

    // State
    ExtendedRecord source = createEr("Indonesia", "ID", null, null, "110.279078", "-8.023319");
    LocationRecord expected =
        createLr(
            Country.INDONESIA,
            -8.023319,
            110.279078,
            GEODETIC_DATUM_ASSUMED_WGS84,
            PRESUMED_SWAPPED_COORDINATE);

    // When
    LocationRecord result = interpret(source);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void nullAwareValues() {
    // State
    ExtendedRecord er = new ExtendedRecord();
    er.setId(ID);
    Map<String, String> coreTerms = new HashMap<>();
    coreTerms.put(DwcTerm.maximumDepthInMeters.qualifiedName(), "NuLL");
    coreTerms.put(DwcTerm.minimumDepthInMeters.qualifiedName(), "null");
    coreTerms.put(DwcTerm.minimumElevationInMeters.qualifiedName(), "10");
    er.setCoreTerms(coreTerms);

    LocationRecord lr = LocationRecord.newBuilder().setId(ID).build();

    // When
    LocationInterpreter.interpretDepth(er, lr);
    LocationInterpreter.interpretElevation(er, lr);

    // Should
    assertNull(lr.getDepth());
    assertNotNull(lr.getElevation());
    assertTrue(lr.getIssues().getIssueList().isEmpty());
  }

  @Test
  public void issueInvalidFootprintSRSTest() {

    // State
    ExtendedRecord source = ExtendedRecord.newBuilder().setId("1").build();
    Map<String, String> coreMap = new HashMap<>();
    coreMap.put(DwcTerm.footprintSRS.qualifiedName(), "invalid!");
    source.setCoreTerms(coreMap);
    LocationRecord result = LocationRecord.newBuilder().setId("1").build();

    // When
    LocationInterpreter.interpretFootprintWKT(source, result);

    // Should
    Assert.assertTrue(
        result.getIssues().getIssueList().stream()
            .anyMatch(issue -> issue.equals(OccurrenceIssue.FOOTPRINT_SRS_INVALID.name())));
  }

  @Test
  public void issueInvalidFootprintWKTTest() {

    // State
    ExtendedRecord source = ExtendedRecord.newBuilder().setId("1").build();
    Map<String, String> coreMap = new HashMap<>();
    coreMap.put(DwcTerm.footprintSRS.qualifiedName(), "EPSG:28992");
    coreMap.put(DwcTerm.footprintWKT.qualifiedName(), "POLYGON((0 0, 0 10, 10 10, 10 0))");
    source.setCoreTerms(coreMap);
    LocationRecord result = LocationRecord.newBuilder().setId("1").build();

    // When
    LocationInterpreter.interpretFootprintWKT(source, result);

    // Should
    Assert.assertTrue(
        result.getIssues().getIssueList().stream()
            .anyMatch(issue -> issue.equals(OccurrenceIssue.FOOTPRINT_WKT_INVALID.name())));
  }

  @Test
  public void footprintWKTTest() {

    // State
    ExtendedRecord source = ExtendedRecord.newBuilder().setId("1").build();
    Map<String, String> coreMap = new HashMap<>();
    coreMap.put(DwcTerm.footprintSRS.qualifiedName(), "EPSG:28992");
    coreMap.put(
        DwcTerm.footprintWKT.qualifiedName(),
        "POLYGON((100000 515000,100000 520000,105000 520000,105000 515000,100000 515000))");
    source.setCoreTerms(coreMap);
    LocationRecord result = LocationRecord.newBuilder().setId("1").build();

    // Value projected in WGS84
    LocationRecord expected =
        LocationRecord.newBuilder()
            .setId("1")
            .setFootprintWKT(
                "POLYGON ((52.619749292808244 4.575033022857827, 52.66468072273538 4.574203170903047, 52.665162889286556 4.648106265726084, 52.6202308261076 4.648860682668263, 52.619749292808244 4.575033022857827))")
            .build();

    // When
    LocationInterpreter.interpretFootprintWKT(source, result);

    // Should
    assertEquals(expected, result);
  }

  @Test
  public void footprintTest() {

    // State
    ExtendedRecord source = ExtendedRecord.newBuilder().setId("1").build();
    Map<String, String> coreMap = new HashMap<>();
    coreMap.put(DwcTerm.footprintWKT.qualifiedName(), "POINT(-85.908333 15.958333)");
    coreMap.put(DwcTerm.footprintSRS.qualifiedName(), "WGS84");
    coreMap.put(DwcTerm.countryCode.qualifiedName(), "HN");
    source.setCoreTerms(coreMap);

    // When
    LocationRecord result = interpret(source);
    LocationInterpreter.interpretFootprintWKT(source, result);

    // Should
    assertEquals(15.958333d, result.getDecimalLatitude(), 0);
    assertEquals(-85.908333d, result.getDecimalLongitude(), 0);
    assertNull(result.getFootprintWKT());
    assertEquals(0, result.getIssues().getIssueList().size());
  }

  @Test
  public void footprintConflictTest() {

    // State
    ExtendedRecord source = ExtendedRecord.newBuilder().setId("1").build();
    Map<String, String> coreMap = new HashMap<>();
    coreMap.put(DwcTerm.decimalLatitude.qualifiedName(), "15.958333");
    coreMap.put(DwcTerm.decimalLongitude.qualifiedName(), "-85.908333");
    coreMap.put(DwcTerm.geodeticDatum.qualifiedName(), "WGS84");
    coreMap.put(DwcTerm.footprintWKT.qualifiedName(), "POINT(-66.6 66.6)");
    coreMap.put(DwcTerm.countryCode.qualifiedName(), "HN");
    source.setCoreTerms(coreMap);

    // When
    LocationRecord result = interpret(source);
    LocationInterpreter.interpretFootprintWKT(source, result);

    // Should
    assertEquals(15.958333d, result.getDecimalLatitude(), 0);
    System.out.println(result.getIssues());
    System.out.println(result.getCountry());
    assertEquals(1, result.getIssues().getIssueList().size());
    Assert.assertTrue(
        result.getIssues().getIssueList().stream()
            .anyMatch(issue -> issue.equals(FOOTPRINT_WKT_MISMATCH.name())));
  }
}
