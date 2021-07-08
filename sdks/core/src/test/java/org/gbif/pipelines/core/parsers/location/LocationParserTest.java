package org.gbif.pipelines.core.parsers.location;

import static org.gbif.api.vocabulary.OccurrenceIssue.COORDINATE_ROUNDED;
import static org.gbif.api.vocabulary.OccurrenceIssue.COUNTRY_DERIVED_FROM_COORDINATES;
import static org.gbif.api.vocabulary.OccurrenceIssue.COUNTRY_INVALID;
import static org.gbif.api.vocabulary.OccurrenceIssue.GEODETIC_DATUM_ASSUMED_WGS84;

import java.util.Arrays;
import java.util.Collections;
import org.gbif.api.vocabulary.Country;
import org.gbif.kvs.KeyValueStore;
import org.gbif.kvs.geocode.LatLng;
import org.gbif.pipelines.core.parsers.common.ParsedField;
import org.gbif.pipelines.core.parsers.location.parser.LocationParser;
import org.gbif.pipelines.core.parsers.location.parser.ParsedLocation;
import org.gbif.pipelines.core.utils.ExtendedRecordBuilder;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.rest.client.geocode.GeocodeResponse;
import org.gbif.rest.client.geocode.Location;
import org.junit.Assert;
import org.junit.Test;

public class LocationParserTest {

  private static final String TEST_ID = "1";

  private static final Double LATITUDE_CANADA = 60.4;
  private static final Double LONGITUDE_CANADA = -131.3;

  private static final KeyValueStore<LatLng, GeocodeResponse> GEOCODE_KV_STORE;

  static {
    KeyValueTestStore testStore = new KeyValueTestStore();
    testStore.put(new LatLng(60.4d, -131.3d), toGeocodeResponse(Country.CANADA));
    testStore.put(new LatLng(30.2d, 100.2344349d), toGeocodeResponse(Country.CHINA));
    testStore.put(new LatLng(30.2d, 100.234435d), toGeocodeResponse(Country.CHINA));
    testStore.put(new LatLng(71.7d, -42.6d), toGeocodeResponse(Country.GREENLAND));
    testStore.put(new LatLng(-17.65, -149.46), toGeocodeResponse(Country.FRENCH_POLYNESIA));
    testStore.put(new LatLng(27.15, -13.20), toGeocodeResponse(Country.MOROCCO));
    GEOCODE_KV_STORE = GeocodeKvStore.create(testStore);
  }

  private static GeocodeResponse toGeocodeResponse(Country country) {
    Location location = new Location();
    location.setType("Political");
    location.setDistance(0.0d);
    location.setIsoCountryCode2Digit(country.getIso2LetterCode());
    return new GeocodeResponse(Collections.singletonList(location));
  }

  private KeyValueStore<LatLng, GeocodeResponse> getGeocodeKvStore() {
    return GEOCODE_KV_STORE;
  }

  @Test
  public void parseByCountryTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create().id(TEST_ID).country("Spain").build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertEquals(Country.SPAIN, result.getResult().getCountry());
  }

  @Test
  public void parseByCountryCodeTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create().id(TEST_ID).countryCode("ES").build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertEquals(Country.SPAIN, result.getResult().getCountry());
  }

  @Test
  public void parseByCountryAndCountryCodeTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create().id(TEST_ID).country("Spain").countryCode("ES").build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertEquals(Country.SPAIN, result.getResult().getCountry());
  }

  @Test
  public void invalidCountryIssueTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create().id(TEST_ID).country("foo").build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertFalse(result.isSuccessful());
    Assert.assertNull(result.getResult().getCountry());
    Assert.assertTrue(result.getIssues().contains(COUNTRY_INVALID.name()));
  }

  @Test
  public void invalidCountryCodeIssueTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create().id(TEST_ID).countryCode("foo").build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertFalse(result.isSuccessful());
    Assert.assertNull(result.getResult().getCountry());
    Assert.assertTrue(result.getIssues().contains(COUNTRY_INVALID.name()));
  }

  @Test
  public void coordsWithDerivedCountryTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create()
            .id(TEST_ID)
            .decimalLatitude("30.2")
            .decimalLongitude("100.2344349")
            .build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertFalse(result.isSuccessful());
    Assert.assertEquals(Country.CHINA, result.getResult().getCountry());
    Assert.assertEquals(30.2d, result.getResult().getLatLng().getLatitude(), 0);
    Assert.assertEquals(100.234435d, result.getResult().getLatLng().getLongitude(), 0);
    Assert.assertTrue(
        result
            .getIssues()
            .containsAll(
                Arrays.asList(
                    COORDINATE_ROUNDED.name(),
                    COUNTRY_DERIVED_FROM_COORDINATES.name(),
                    GEODETIC_DATUM_ASSUMED_WGS84.name())));
  }

  @Test
  public void verbatimLtnLngWithDerivedCountryTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create()
            .id(TEST_ID)
            .verbatimLatitude("30.2")
            .verbatimLongitude("100.2344349")
            .build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertFalse(result.isSuccessful());
    Assert.assertEquals(Country.CHINA, result.getResult().getCountry());
    Assert.assertEquals(30.2d, result.getResult().getLatLng().getLatitude(), 0);
    Assert.assertEquals(100.234435d, result.getResult().getLatLng().getLongitude(), 0);
    Assert.assertTrue(
        result
            .getIssues()
            .containsAll(
                Arrays.asList(
                    COORDINATE_ROUNDED.name(),
                    COUNTRY_DERIVED_FROM_COORDINATES.name(),
                    GEODETIC_DATUM_ASSUMED_WGS84.name())));
  }

  @Test
  public void verbatimCoordsWithDerivedCountryTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create().id(TEST_ID).verbatimCoords("30.2, 100.2344349").build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertFalse(result.isSuccessful());
    Assert.assertEquals(Country.CHINA, result.getResult().getCountry());
    Assert.assertEquals(30.2d, result.getResult().getLatLng().getLatitude(), 0);
    Assert.assertEquals(100.234435d, result.getResult().getLatLng().getLongitude(), 0);
    Assert.assertTrue(
        result
            .getIssues()
            .containsAll(
                Arrays.asList(
                    COORDINATE_ROUNDED.name(),
                    COUNTRY_DERIVED_FROM_COORDINATES.name(),
                    GEODETIC_DATUM_ASSUMED_WGS84.name())));
  }

  @Test
  public void coordsAndCountryWhenParsedThenReturnCoordsAndCountryTest() {

    // State
    ExtendedRecord extendedRecord =
        ExtendedRecordBuilder.create()
            .id(TEST_ID)
            .country(Country.CANADA.getTitle())
            .countryCode(Country.CANADA.getIso2LetterCode())
            .decimalLatitude(String.valueOf(LATITUDE_CANADA))
            .decimalLongitude(String.valueOf(LONGITUDE_CANADA))
            .build();

    // When
    ParsedField<ParsedLocation> result = LocationParser.parse(extendedRecord, getGeocodeKvStore());

    // Should
    Assert.assertTrue(result.isSuccessful());
    Assert.assertEquals(Country.CANADA, result.getResult().getCountry());
    Assert.assertEquals(LATITUDE_CANADA, result.getResult().getLatLng().getLatitude(), 0);
    Assert.assertEquals(LONGITUDE_CANADA, result.getResult().getLatLng().getLongitude(), 0);
    Assert.assertEquals(1, result.getIssues().size());
    Assert.assertTrue(result.getIssues().contains(GEODETIC_DATUM_ASSUMED_WGS84.name()));
  }

  @Test(expected = NullPointerException.class)
  public void nullArgsTest() {
    // When
    LocationParser.parse(null, null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidArgsTest() {
    // When
    LocationParser.parse(new ExtendedRecord(), null);
  }
}
