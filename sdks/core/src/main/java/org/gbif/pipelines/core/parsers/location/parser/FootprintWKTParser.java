package org.gbif.pipelines.core.parsers.location.parser;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.pipelines.core.parsers.common.ParsedField;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.transform.IdentityTransform;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

/** Parser for the Dwc Terms related to the footprintWKT. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FootprintWKTParser {

  private static CoordinateReferenceSystem WGS84;

  // WGS84 is the reference CRS.
  static {
    try {
      WGS84 = CRS.decode("EPSG:4326");
    } catch (FactoryException ex) {
      log.error("Error initiating WGS84", ex);
    }
  }

  /** Parse the DwcTerm footprintWKT using a CRS/footprintSRS as reference. */
  public static ParsedField<String> parseFootprintWKT(
      CoordinateReferenceSystem footprintSRS, String footprintWKT) {
    try {
      MathTransform transform;
      if (footprintSRS == null) {
        transform = IdentityTransform.create(WGS84.getCoordinateSystem().getDimension());
      } else {
        transform = CRS.findMathTransform(footprintSRS, WGS84, true);
      }

      WKTReader wktReader = new WKTReader();
      Geometry geometry = wktReader.read(footprintWKT);
      return ParsedField.<String>builder()
          .result(JTS.transform(geometry, transform).toText())
          .successful(true)
          .build();
    } catch (FactoryException | ParseException | TransformException | IllegalArgumentException ex) {
      return ParsedField.fail(OccurrenceIssue.FOOTPRINT_WKT_INVALID.name());
    }
  }
}
