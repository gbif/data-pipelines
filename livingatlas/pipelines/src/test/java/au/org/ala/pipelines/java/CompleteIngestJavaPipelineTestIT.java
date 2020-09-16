package au.org.ala.pipelines.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import au.org.ala.pipelines.beam.ALAInterpretedToLatLongCSVPipeline;
import au.org.ala.pipelines.beam.ALASamplingToAvroPipeline;
import au.org.ala.pipelines.beam.ALAUUIDMintingPipeline;
import au.org.ala.pipelines.options.ALASolrPipelineOptions;
import au.org.ala.pipelines.options.UUIDPipelineOptions;
import au.org.ala.sampling.LayerCrawler;
import au.org.ala.util.SolrUtils;
import au.org.ala.util.TestUtils;
import au.org.ala.utils.ValidationUtils;
import java.io.File;
import java.util.UUID;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.io.FileUtils;
import org.gbif.pipelines.ingest.options.DwcaPipelineOptions;
import org.gbif.pipelines.ingest.options.InterpretationPipelineOptions;
import org.gbif.pipelines.ingest.options.PipelinesOptionsFactory;
import org.gbif.pipelines.ingest.pipelines.DwcaToVerbatimPipeline;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Complete pipeline tests that use the java variant of the pipeline where possible. Currently this
 * is for Interpretation and SOLR indexing only.
 *
 * <p>This needs to be ran with -Xmx128m
 */
public class CompleteIngestJavaPipelineTestIT {

  MockWebServer server;

  @Before
  public void setup() throws Exception {
    // clear up previous test runs
    server = TestUtils.createMockCollectory();
    server.start(TestUtils.getCollectoryPort());
  }

  @After
  public void teardown() throws Exception {
    server.shutdown();
  }

  /**
   * Tests for SOLR index creation.
   *
   * @throws Exception
   */
  @Test
  public void testIngestPipeline() throws Exception {

    // clear up previous test runs
    FileUtils.deleteQuietly(new File("/tmp/la-pipelines-test/complete-pipeline-java"));

    // clear SOLR index
    SolrUtils.setupIndex();

    String absolutePath = new File("src/test/resources").getAbsolutePath();

    // Step 1: load a dataset and verify all records have a UUID associated
    loadTestDataset("dr893", absolutePath + "/complete-pipeline-java/dr893");

    // reload
    SolrUtils.reloadSolrIndex();

    // validate SOLR index
    assertEquals(Long.valueOf(5), SolrUtils.getRecordCount("*:*"));

    // 1. includes UUIDs
    String documentId = (String) SolrUtils.getRecords("*:*").get(0).get("id");
    assertNotNull(documentId);
    UUID uuid = null;
    try {
      uuid = UUID.fromString(documentId);
      // do something
    } catch (IllegalArgumentException exception) {
      // handle the case where string is not valid UUID
    }

    assertNotNull(uuid);

    // 2. includes samples
    assertEquals(Long.valueOf(5), SolrUtils.getRecordCount("cl620:*"));
    assertEquals(Long.valueOf(5), SolrUtils.getRecordCount("cl927:*"));
  }

  public void loadTestDataset(String datasetID, String inputPath) throws Exception {

    DwcaPipelineOptions dwcaOptions =
        PipelinesOptionsFactory.create(
            DwcaPipelineOptions.class,
            new String[] {
              "--datasetId=" + datasetID,
              "--appName=DWCA",
              "--attempt=1",
              "--runner=DirectRunner",
              "--metaFileName=" + ValidationUtils.VERBATIM_METRICS,
              "--targetPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--inputPath=" + inputPath
            });
    DwcaToVerbatimPipeline.run(dwcaOptions);

    InterpretationPipelineOptions interpretationOptions =
        PipelinesOptionsFactory.create(
            InterpretationPipelineOptions.class,
            new String[] {
              "--datasetId=" + datasetID,
              "--attempt=1",
              "--runner=DirectRunner",
              "--interpretationTypes=ALL",
              "--metaFileName=" + ValidationUtils.INTERPRETATION_METRICS,
              "--targetPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--inputPath=/tmp/la-pipelines-test/complete-pipeline-java/dr893/1/verbatim.avro",
              "--properties=" + TestUtils.getPipelinesConfigFile(),
              "--useExtendedRecordId=true"
            });
    au.org.ala.pipelines.java.ALAVerbatimToInterpretedPipeline.run(interpretationOptions);

    UUIDPipelineOptions uuidOptions =
        PipelinesOptionsFactory.create(
            UUIDPipelineOptions.class,
            new String[] {
              "--datasetId=" + datasetID,
              "--attempt=1",
              "--runner=DirectRunner",
              "--metaFileName=" + ValidationUtils.UUID_METRICS,
              "--targetPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--inputPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--properties=" + TestUtils.getPipelinesConfigFile(),
              "--useExtendedRecordId=true"
            });
    ALAUUIDMintingPipeline.run(uuidOptions);

    // export lat lngs
    InterpretationPipelineOptions latLngOptions =
        PipelinesOptionsFactory.create(
            InterpretationPipelineOptions.class,
            new String[] {
              "--datasetId=" + datasetID,
              "--attempt=1",
              "--runner=DirectRunner",
              "--targetPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--inputPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--properties=" + TestUtils.getPipelinesConfigFile()
            });
    ALAInterpretedToLatLongCSVPipeline.run(latLngOptions);

    // sample
    LayerCrawler.run(latLngOptions);

    // sample -> avro
    InterpretationPipelineOptions samplingAvroOptions =
        PipelinesOptionsFactory.create(
            InterpretationPipelineOptions.class,
            new String[] {
              "--datasetId=" + datasetID,
              "--attempt=1",
              "--runner=DirectRunner",
              "--targetPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--inputPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--metaFileName=" + ValidationUtils.SAMPLING_METRICS,
              "--properties=" + TestUtils.getPipelinesConfigFile()
            });
    ALASamplingToAvroPipeline.run(samplingAvroOptions);

    // solr
    ALASolrPipelineOptions solrOptions =
        PipelinesOptionsFactory.create(
            ALASolrPipelineOptions.class,
            new String[] {
              "--datasetId=" + datasetID,
              "--attempt=1",
              "--runner=DirectRunner",
              "--metaFileName=" + ValidationUtils.INDEXING_METRICS,
              "--targetPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--inputPath=/tmp/la-pipelines-test/complete-pipeline-java",
              "--properties=" + TestUtils.getPipelinesConfigFile(),
              "--zkHost=" + SolrUtils.getZkHost(),
              "--solrCollection=" + SolrUtils.BIOCACHE_TEST_SOLR_COLLECTION,
              "--includeSampling=true"
            });

    au.org.ala.pipelines.java.ALAInterpretedToSolrIndexPipeline.run(solrOptions);
  }
}
