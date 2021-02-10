package org.gbif.pipelines.core.interpreters.extension;

import java.util.Objects;
import lombok.NoArgsConstructor;
import org.gbif.api.vocabulary.Extension;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.core.interpreters.ExtensionInterpretation;
import org.gbif.pipelines.core.interpreters.ExtensionInterpretation.Result;
import org.gbif.pipelines.core.interpreters.ExtensionInterpretation.TargetHandler;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.MeasurementOrFact;
import org.gbif.pipelines.io.avro.MeasurementOrFactRecord;

/**
 * Interpreter for the MeasurementsOrFacts extension, Interprets form {@link ExtendedRecord} to
 * {@link MeasurementOrFactRecord}.
 *
 * @see <a href="http://rs.gbif.org/extension/dwc/measurements_or_facts.xml</a>
 */
@NoArgsConstructor(staticName = "create")
public class MeasurementOrFactInterpreter {

  private static final TargetHandler<MeasurementOrFact> HANDLER =
      ExtensionInterpretation.extension(Extension.MEASUREMENT_OR_FACT)
          .to(MeasurementOrFact::new)
          .map(DwcTerm.measurementType, MeasurementOrFact::setMeasurementType)
          .map(DwcTerm.measurementUnit, MeasurementOrFact::setMeasurementUnit)
          .map(DwcTerm.measurementValue, MeasurementOrFact::setMeasurementValue);

  /**
   * Interprets measurements or facts of a {@link ExtendedRecord} and populates a {@link
   * MeasurementOrFactRecord} with the interpreted values.
   */
  public static void interpret(ExtendedRecord er, MeasurementOrFactRecord mfr) {
    Objects.requireNonNull(er);
    Objects.requireNonNull(mfr);

    Result<MeasurementOrFact> result = HANDLER.convert(er);

    mfr.setMeasurementOrFactItems(result.getList());
    mfr.getIssues().setIssueList(result.getIssuesAsList());
  }
}
