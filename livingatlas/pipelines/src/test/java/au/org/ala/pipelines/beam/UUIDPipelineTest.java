package au.org.ala.pipelines.beam;

import au.org.ala.util.AvroUtils;
import org.apache.commons.io.FileUtils;
import org.gbif.pipelines.ingest.options.DwcaPipelineOptions;
import org.gbif.pipelines.ingest.options.InterpretationPipelineOptions;
import org.gbif.pipelines.ingest.options.PipelinesOptionsFactory;
import org.gbif.pipelines.ingest.pipelines.DwcaToVerbatimPipeline;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UUIDPipelineTest {

    @Before
    public void setup() throws Exception {
        //clear up previous test runs
        FileUtils.deleteQuietly(new File("/tmp/la-pipelines-test/uuid-management"));
    }

    @Test
    public void testNonDwC() throws Exception {
        //dr1864 - has deviceId
        String absolutePath = new File("src/test/resources").getAbsolutePath();
        // Step 1: load a dataset and verify all records have a UUID associated
        loadTestDataset("dr1864", absolutePath + "/uuid-management/dr1864");

        Map<String, String> uniqueKeyToUUID = AvroUtils.readKeysForPath(
                "/tmp/la-pipelines-test/uuid-management/dr1864/1/identifiers/ala_uuid/interpret-*");

        //check generated keys are present
        assertTrue(uniqueKeyToUUID.containsKey("dr1864|2|12/12/01"));
        assertTrue(uniqueKeyToUUID.containsKey("dr1864|3|12/12/01"));
    }

    /**
     * Tests for UUID creation. This test simulates a dataset being:
     *
     * 1) Loaded
     * 2) Re-loaded
     * 3) Re-loaded with records removed
     * 4) Re-loaded with  removed records being added back & UUID being preserved.
     *
     * @throws Exception
     */
    @Test
    public void testUuidsPipeline() throws Exception {

        //clear up previous test runs
        FileUtils.deleteQuietly(new File("/tmp/la-pipelines-test/uuid-management"));

        String absolutePath = new File("src/test/resources").getAbsolutePath();

        // Step 1: load a dataset and verify all records have a UUID associated
        loadTestDataset("dr893", absolutePath + "/uuid-management/dr893");

        //validation function
        Map<String, String> keysAfterFirstRun = AvroUtils.readKeysForPath("/tmp/la-pipelines-test/uuid-management/dr893/1/identifiers/ala_uuid/interpret-*");
        assertEquals(5, keysAfterFirstRun.size());

        // Step 2: Check UUIDs where preserved
        loadTestDataset("dr893", absolutePath + "/uuid-management/dr893");
        Map<String, String> keysAfterSecondRun = AvroUtils.readKeysForPath("/tmp/la-pipelines-test/uuid-management/dr893/1/identifiers/ala_uuid/interpret-*");

        //validate
        assertTrue(keysAfterFirstRun.size() == keysAfterSecondRun.size());
        for (Map.Entry<String, String> key  : keysAfterFirstRun.entrySet()){
            assertTrue(keysAfterSecondRun.containsKey(key.getKey()));
            assertEquals(keysAfterSecondRun.get(key.getKey()), key.getValue());
        }

        // Step 3: Check UUIDs where preserved for the removed records
        loadTestDataset("dr893", absolutePath + "/uuid-management/dr893-reduced");
        Map<String, String> keysAfterThirdRun = AvroUtils.readKeysForPath("/tmp/la-pipelines-test/uuid-management/dr893/1/identifiers/ala_uuid/interpret-*");
        //validate
        for (Map.Entry<String, String> key  : keysAfterThirdRun.entrySet()){
            assertTrue(keysAfterFirstRun.containsKey(key.getKey()));
            assertEquals(keysAfterFirstRun.get(key.getKey()),key.getValue());
        }

        // Step 4: Check UUIDs where preserved for the re-added records
        loadTestDataset("dr893", absolutePath + "/uuid-management/dr893-readded");
        Map<String, String> keysAfterFourthRun = AvroUtils.readKeysForPath("/tmp/la-pipelines-test/uuid-management/dr893/1/identifiers/ala_uuid/interpret-*");
        assertEquals(6, keysAfterFourthRun.size());
        //validate
        for (Map.Entry<String, String> key  : keysAfterFirstRun.entrySet()){
            assertTrue(keysAfterFourthRun.containsKey(key.getKey()));
            assertEquals(keysAfterFourthRun.get(key.getKey()), key.getValue());
        }
    }

    public void loadTestDataset(String datasetID, String inputPath) throws Exception {

        DwcaPipelineOptions dwcaOptions = PipelinesOptionsFactory.create(DwcaPipelineOptions.class, new String[]{
                "--datasetId=" + datasetID,
                "--attempt=1",
                "--pipelineStep=DWCA_TO_VERBATIM",
                "--runner=DirectRunner",
                "--metaFileName=dwca-metrics.yml",
                "--targetPath=/tmp/la-pipelines-test/uuid-management",
                "--inputPath=" + inputPath
        });
        DwcaToVerbatimPipeline.run(dwcaOptions);

        InterpretationPipelineOptions interpretationOptions = PipelinesOptionsFactory.create(InterpretationPipelineOptions.class, new String[]{
                "--datasetId=" + datasetID,
                "--attempt=1",
                "--runner=DirectRunner",
                "--interpretationTypes=ALL",
                "--metaFileName=interpretation-metrics.yml",
                "--targetPath=/tmp/la-pipelines-test/uuid-management",
                "--inputPath=/tmp/la-pipelines-test/uuid-management/"+ datasetID+"/1/verbatim.avro",
                "--properties=src/test/resources/pipelines.yaml",
                "--useExtendedRecordId=true"
        });
        ALAVerbatimToInterpretedPipeline.run(interpretationOptions);

        InterpretationPipelineOptions uuidOptions = PipelinesOptionsFactory.create(InterpretationPipelineOptions.class, new String[]{
                "--datasetId=" + datasetID,
                "--attempt=1",
                "--runner=DirectRunner",
                "--metaFileName=uuid-metrics.yml",
                "--targetPath=/tmp/la-pipelines-test/uuid-management",
                "--inputPath=/tmp/la-pipelines-test/uuid-management/" + datasetID + "/1/verbatim.avro",
                "--properties=src/test/resources/pipelines.yaml",
                "--useExtendedRecordId=true"
        });
        ALAUUIDMintingPipeline.run(uuidOptions);
    }
}
