package au.org.ala.pipelines.vocabulary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.gbif.kvs.geocode.LatLng;

/**
 * CentrePoints is used by countryCentres and stateProvinceCentres, so it can be a singleton.
 *
 * <p>Singleton should be implemented in a conconcrete class like, countryCentres / StateCentres
 *
 * <p>Simulate CentrePoints.scala Compare with predefined state centres in file Format: New South
 * Wales -31.2532183 146.921099 -28.1561921 153.903718 -37.5052772 140.9992122 Rounded decimal of
 * predefined state centres based on precision of the given coordinates
 *
 * <p>The first two is the coordinate of central point. The rest four are BBox
 *
 * <p>1st col: name
 *
 * <p>2nd, 3rd: centre
 *
 * <p>If only 4 columns, the 4th is country code
 *
 * <p>If 7 columns, from the 4th to 8th is BBOX
 *
 * <p>If 8 columns, the 8th is country code
 *
 * @author Bai187
 */
@Slf4j
public class CentrePoints {

  private static CentrePoints cp;
  private final Map<String, LatLng> centres = new HashMap();
  private final Map<String, BBox> BBox = new HashMap();
  // Only for country, map country code to country name
  private final Map<String, String> codes = new HashMap();

  private CentrePoints() {}

  public static CentrePoints getInstance(String filePath) throws FileNotFoundException {
    InputStream is = new FileInputStream(new File(filePath));
    return getInstance(is);
  }

  public static CentrePoints getInstance(InputStream is) {
    cp = new CentrePoints();
    // Use country as an example
    // 3 columns: country code, latitude, longitude,
    // 4 columns: country code, latitude, longitude,country name
    // 7 coluns: country code, latitude, longitude, bbox
    new BufferedReader(new InputStreamReader(is))
        .lines()
        .map(s -> s.trim())
        .filter(
            l ->
                l.split("\t").length == 7
                    || l.split("\t").length == 3
                    || l.split("\t").length == 4
                    || l.split("\t").length == 8)
        .forEach(
            l -> {
              String[] ss = l.split("\t");
              int length = ss.length;
              String name = ss[0].toUpperCase().replace("\"", ""); // Remove possible string quotes
              LatLng centre = new LatLng(Double.parseDouble(ss[1]), Double.parseDouble(ss[2]));
              // country code
              if (length == 4) {
                String code = ss[3].toUpperCase();
                cp.codes.put(code, name);
              }
              if (length == 8) {
                String code = ss[7].toUpperCase();
                cp.codes.put(code, name);
              }
              if (length == 7) {
                BBox bbox =
                    new BBox(
                        Double.parseDouble(ss[3]),
                        Double.parseDouble(ss[4]),
                        Double.parseDouble(ss[5]),
                        Double.parseDouble(ss[6]));
                cp.BBox.put(name, bbox);
              }
              cp.centres.put(name, centre);
            });
    return cp;
  }

  /**
   * Precision of coordinate is determined by the given lat and lng for example, given lat 14.39,
   * will only compare to the second decimal
   */
  public boolean coordinatesMatchCentre(
      String location, double decimalLatitude, double decimalLongitude) {

    LatLng supposedCentre = centres.get(location.toUpperCase());
    if (supposedCentre != null) {
      int latDecPlaces = noOfDecimalPlace(decimalLatitude);
      int longDecPlaces = noOfDecimalPlace(decimalLongitude);

      // approximate the centre points appropriately
      double approximatedLat = round(supposedCentre.getLatitude(), latDecPlaces);
      double approximatedLong = round(supposedCentre.getLongitude(), longDecPlaces);

      // compare approximated centre point with supplied coordinates
      if (log.isDebugEnabled()) {
        log.debug(
            "{} {} VS {} {}", decimalLatitude, decimalLongitude, approximatedLat, approximatedLong);
      }
      return approximatedLat == decimalLatitude && approximatedLong == decimalLongitude;
    } else {
      if (log.isWarnEnabled()) {
        log.warn("{} is not found in records", location);
      }
      return false;
    }
  }

  /** @return size of centres */
  public int size() {
    return centres.size();
  }

  /** @return keys */
  public Set keys() {
    return centres.keySet();
  }

  /**
   * Only for country centre file.
   *
   * @param key country code
   * @return country name if exists
   */
  public String getName(String key) {
    return codes.get(key);
  }

  private double round(double number, int decimalPlaces) {
    if (decimalPlaces > 0) {
      int x = 1;
      for (int i = 0; i < decimalPlaces; i++) {
        x = x * 10;
      }
      return ((double) (Math.round(number * x))) / x;
    } else {
      return Math.round(number);
    }
  }

  private int noOfDecimalPlace(double number) {
    String numberString = String.valueOf(number);
    int decimalPointLoc = numberString.indexOf(".");
    if (decimalPointLoc < 0) {
      return 0;
    } else {
      return numberString.substring(decimalPointLoc + 1).length();
    }
  }
}

class BBox {

  private double xmin;
  private double xmax;
  private double ymin;
  private double ymax;

  public BBox(double a_x, double a_y, double b_x, double b_y) {
    xmin = Math.min(a_x, b_x);
    xmax = Math.max(a_x, b_x);
    ymin = Math.min(a_y, b_y);
    ymax = Math.max(a_y, b_y);
    sanity();
  }

  private void sanity() {
    if (xmin < -180.0) {
      xmin = -180.0;
    }
    if (xmax > 180.0) {
      xmax = 180.0;
    }
    if (ymin < -90.0) {
      ymin = -90.0;
    }
    if (ymax > 90.0) {
      ymax = 90.0;
    }
  }

  public void add(LatLng c) {
    add(c.getLongitude(), c.getLatitude());
  }

  /** Extends this bbox to include the point (x, y) */
  public void add(double x, double y) {
    xmin = Math.min(xmin, x);
    xmax = Math.max(xmax, x);
    ymin = Math.min(ymin, y);
    ymax = Math.max(ymax, y);
    sanity();
  }

  public void add(BBox box) {
    add(box.getTopLeft());
    add(box.getBottomRight());
  }

  public LatLng getTopLeft() {
    return new LatLng(ymax, xmin);
  }

  public LatLng getBottomRight() {
    return new LatLng(ymin, xmax);
  }

  @Override
  public int hashCode() {
    return (int) (ymin * xmin);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof BBox) {
      BBox b = (BBox) o;
      return b.xmax == xmax && b.ymax == ymax && b.xmin == xmin && b.ymin == ymin;
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return "[ x: " + xmin + " -> " + xmax + ", y: " + ymin + " -> " + ymax + " ]";
  }
}
