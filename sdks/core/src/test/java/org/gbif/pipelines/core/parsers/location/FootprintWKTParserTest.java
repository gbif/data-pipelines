package org.gbif.pipelines.core.parsers.location;

import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.pipelines.core.parsers.common.ParsedField;
import org.gbif.pipelines.core.parsers.location.parser.FootprintWKTParser;
import org.gbif.pipelines.core.parsers.location.parser.SpatialReferenceSystemParser;
import org.junit.Assert;
import org.junit.Test;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/** Tests of footprintWKT parser. */
public class FootprintWKTParserTest {

  @Test
  public void validFootprintWKTTest() {
    CoordinateReferenceSystem fromCRS = SpatialReferenceSystemParser.parseCRS("EPSG:28992");
    ParsedField<String> footPrintWKT =
        FootprintWKTParser.parseFootprintWKT(
            fromCRS,
            "POLYGON((100000 515000,100000 520000,105000 520000,105000 515000,100000 515000))");
    Assert.assertTrue(footPrintWKT.isSuccessful());
  }

  @Test
  public void validFootprintWKTNoSRSTest() {
    ParsedField<String> footPrintWKT =
        FootprintWKTParser.parseFootprintWKT(
            null,
            "MULTIPOLYGON (((-124.160354746303 49.3510959043449,-124.160203653009 49.3489123496606,-124.160776738903 49.3465892980439,-124.163038949177 49.3462764995535,-124.185669555976 49.3128754173328,-124.18606886087 49.3116279817231,-124.182602955298 49.3064654997771,-124.185183156346 49.2869615464782,-124.273139874644 49.2431468791988,-124.778607982738 49.4229759780448,-124.722560520936 49.4609580466264,-124.724994684329 49.4672021632683,-124.685095085765 49.4722579040976,-124.647824626891 49.4838140698403,-124.576907943641 49.5172678603206,-124.434778030849 49.4867780278967,-124.160354746303 49.3510959043449)))");
    Assert.assertTrue(footPrintWKT.isSuccessful());
  }

  @Test
  public void invalidFootPrintWKTTest() {
    CoordinateReferenceSystem fromCRS = SpatialReferenceSystemParser.parseCRS("EPSG:28992");
    ParsedField<String> footPrintWKT =
        FootprintWKTParser.parseFootprintWKT(fromCRS, "POLYGON((0 0, 0 10, 10 10, 10 0))");
    Assert.assertFalse(footPrintWKT.isSuccessful());
    Assert.assertTrue(
        footPrintWKT.getIssues().contains(OccurrenceIssue.FOOTPRINT_WKT_INVALID.name()));
  }
}
