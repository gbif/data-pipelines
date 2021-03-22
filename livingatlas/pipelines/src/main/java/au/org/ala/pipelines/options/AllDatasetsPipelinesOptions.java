package au.org.ala.pipelines.options;

import org.apache.beam.sdk.options.*;
import org.gbif.pipelines.common.beam.options.InterpretationPipelineOptions;

public interface AllDatasetsPipelinesOptions
    extends PipelineOptions, InterpretationPipelineOptions {

  @Description("Directory for writing output for pipelines operating on all datasets")
  String getAllDatasetsInputPath();

  void setAllDatasetsInputPath(String allDatasetsInputPath);
}
