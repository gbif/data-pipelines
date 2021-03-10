package au.org.ala.pipelines.vocabulary;

import au.org.ala.kvs.LocationInfoConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.Strings;

/** Load centres of stateProvince from resources */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StateProvinceCentrePoints {

  private static CentrePoints cp;

  public static CentrePoints getInstance(LocationInfoConfig config) throws FileNotFoundException {
    if (cp == null) {
      InputStream is;
      String classpathFile = "/stateProvinceCentrePoints.txt";
      if (config != null) {
        String externalFilePath = config.getStateProvinceCentrePointsFile();
        if (Strings.isNullOrEmpty(externalFilePath)) {
          is = CentrePoints.class.getResourceAsStream(classpathFile);
        } else {
          File externalFile = new File(externalFilePath);
          is = new FileInputStream(externalFile);
        }
      } else {
        is = CentrePoints.class.getResourceAsStream(classpathFile);
      }
      cp = CentrePoints.getInstance(is, "STATEPROVINCE");
      log.info("We found {} state centres", cp.size());
    }
    return cp;
  }
}
