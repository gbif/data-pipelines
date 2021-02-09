package org.gbif.pipelines.core.converters;

import static org.gbif.pipelines.core.utils.ModelUtils.extractNullAwareValue;
import static org.gbif.pipelines.core.utils.ModelUtils.hasValueNullAware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.core.parsers.dynamic.DynamicProperty;
import org.gbif.pipelines.core.parsers.dynamic.LengthParser;
import org.gbif.pipelines.core.parsers.dynamic.MassParser;
import org.gbif.pipelines.io.avro.ExtendedRecord;

public class MeasurementOrFactConverter {

  public static List<Map<String, String>> extractFromDynamicProperties(ExtendedRecord er) {
    if (hasValueNullAware(er, DwcTerm.dynamicProperties)) {
      String value = extractNullAwareValue(er, DwcTerm.dynamicProperties);
      List<Map<String, String>> map = new ArrayList<>(2);
      LengthParser.parse(value).map(MeasurementOrFactConverter::map).ifPresent(map::add);
      MassParser.parse(value).map(MeasurementOrFactConverter::map).ifPresent(map::add);
      return map;
    }
    return Collections.emptyList();
  }

  private static Map<String, String> map(DynamicProperty property) {
    Map<String, String> map = new HashMap<>(3);
    map.put(DwcTerm.measurementType.qualifiedName(), property.getKey());
    map.put(DwcTerm.measurementValue.qualifiedName(), property.getValue());
    map.put(DwcTerm.measurementUnit.qualifiedName(), property.getType());
    return map;
  }
}
