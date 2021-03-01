package au.org.ala.clustering;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import org.gbif.pipelines.core.parsers.clustering.OccurrenceFeatures;

/** A POJO implementation for simple tests. */
@Builder
public class HashKeyOccurrence implements OccurrenceFeatures {
  private final String hashKey;
  private final String id;
  private final String datasetKey;
  private final String speciesKey;
  private final String taxonKey;
  private final String basisOfRecord;
  private final Double decimalLatitude;
  private final Double decimalLongitude;
  private final Integer year;
  private final Integer month;
  private final Integer day;
  private final String eventDate;
  private final String scientificName;
  private final String countryCode;
  private final String typeStatus;
  private final String occurrenceID;
  private final String recordedBy;
  private final String fieldNumber;
  private final String recordNumber;
  private final String catalogNumber;
  private final String otherCatalogNumbers;

  public String getHashKey() {
    return hashKey;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getDatasetKey() {
    return datasetKey;
  }

  @Override
  public String getSpeciesKey() {
    return speciesKey;
  }

  @Override
  public String getTaxonKey() {
    return taxonKey;
  }

  @Override
  public String getBasisOfRecord() {
    return basisOfRecord;
  }

  @Override
  public Double getDecimalLatitude() {
    return decimalLatitude;
  }

  @Override
  public Double getDecimalLongitude() {
    return decimalLongitude;
  }

  @Override
  public Integer getYear() {
    return year;
  }

  @Override
  public Integer getMonth() {
    return month;
  }

  @Override
  public Integer getDay() {
    return day;
  }

  @Override
  public String getEventDate() {
    return eventDate;
  }

  @Override
  public String getScientificName() {
    return scientificName;
  }

  @Override
  public String getCountryCode() {
    return countryCode;
  }

  @Override
  public String getTypeStatus() {
    return typeStatus;
  }

  @Override
  public String getOccurrenceID() {
    return occurrenceID;
  }

  @Override
  public String getRecordedBy() {
    return recordedBy;
  }

  @Override
  public String getFieldNumber() {
    return fieldNumber;
  }

  @Override
  public String getRecordNumber() {
    return recordNumber;
  }

  @Override
  public String getCatalogNumber() {
    return catalogNumber;
  }

  @Override
  public String getOtherCatalogNumbers() {
    return otherCatalogNumbers;
  }

  @Override
  public List<String> getIdentifiers() {
    return Stream.of(
            getOccurrenceID(),
            getFieldNumber(),
            getRecordNumber(),
            getCatalogNumber(),
            getOtherCatalogNumbers())
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
