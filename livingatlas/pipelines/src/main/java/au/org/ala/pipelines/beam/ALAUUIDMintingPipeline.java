package au.org.ala.pipelines.beam;

import au.org.ala.kvs.ALAPipelinesConfig;
import au.org.ala.kvs.ALAPipelinesConfigFactory;
import au.org.ala.kvs.cache.ALAAttributionKVStoreFactory;
import au.org.ala.kvs.client.ALACollectoryMetadata;
import au.org.ala.pipelines.common.ALARecordTypes;
import au.org.ala.pipelines.options.UUIDPipelineOptions;
import au.org.ala.pipelines.util.VersionInfo;
import au.org.ala.utils.CombinedYamlConfiguration;
import au.org.ala.utils.ValidationResult;
import au.org.ala.utils.ValidationUtils;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.file.CodecFactory;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.AvroIO;
import org.apache.beam.sdk.metrics.*;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.hadoop.fs.*;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.kvs.KeyValueStore;
import org.gbif.pipelines.common.beam.metrics.MetricsHandler;
import org.gbif.pipelines.common.beam.options.PipelinesOptionsFactory;
import org.gbif.pipelines.core.utils.FsUtils;
import org.gbif.pipelines.io.avro.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;

/**
 * Pipeline responsible for minting UUIDs on new records and rematching existing UUIDs to records
 * that have been previously loaded.
 *
 * <p>This works by:
 *
 * <p>1. Creating a map from ExtendedRecord of UniqueKey -> ExtendedRecord.getId(). The UniqueKey is
 * constructed using the unique terms specified in the collectory.
 *
 * <p>2. Creating a map from source of UniqueKey -> UUID from previous ingestions (this will be
 * blank for newly imported datasets)
 *
 * <p>3. Joining the two maps by the UniqueKey.
 *
 * <p>4. Writing out ALAUUIDRecords containing ExtendedRecord.getId() -> (UUID, UniqueKey). These
 * records are then used in SOLR index generation.
 *
 * <p>5. Backing up previous AVRO ALAUUIDRecords.
 *
 * <p>The end result is a AVRO export ALAUUIDRecords which are then used as a mandatory extension in
 * the generation of the SOLR index.
 *
 * @see ALAUUIDRecord
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ALAUUIDMintingPipeline {

  public static final String NEW_UUIDS = "newUuids";
  private static final CodecFactory BASE_CODEC = CodecFactory.snappyCodec();

  public static final String NO_ID_MARKER_STRING = "NO_ID";
  public static final ALAUUIDRecord NO_ID_MARKER =
      ALAUUIDRecord.newBuilder().setId(NO_ID_MARKER_STRING).build();
  public static final String REMOVED_PREFIX_MARKER = "REMOVED_";
  public static final String IDENTIFIERS_DIR = "identifiers";
  public static final String UNIQUE_COMPOSITE_KEY_JOIN_CHAR = "|";

  private static final long NOW = System.currentTimeMillis();
  public static final String BACKUP_SUFFIX = "_backup_";
  public static final String PRESERVED_UUIDS = "preservedUuids";
  public static final String ORPHANED_UNIQUE_KEYS = "orphanedUniqueKeys";

  public static void main(String[] args) throws Exception {
    VersionInfo.print();
    String[] combinedArgs = new CombinedYamlConfiguration(args).toArgs("general", "uuid");
    UUIDPipelineOptions options =
        PipelinesOptionsFactory.create(UUIDPipelineOptions.class, combinedArgs);
    options.setMetaFileName(ValidationUtils.UUID_METRICS);
    MDC.put("datasetId", options.getDatasetId());
    MDC.put("attempt", options.getAttempt().toString());
    MDC.put("step", "UUID");
    PipelinesOptionsFactory.registerHdfs(options);
    run(options);
    // FIXME: Issue logged here: https://github.com/AtlasOfLivingAustralia/la-pipelines/issues/105
    System.exit(0);
  }

  public static void run(UUIDPipelineOptions options) throws Exception {

    // delete metrics if it exists
    MetricsHandler.deleteMetricsFile(options);

    // run the validation pipeline
    log.info("Running validation pipeline");
    ALAUUIDValidationPipeline.run(options);

    // check validation results
    ValidationResult validationResult = ValidationUtils.checkValidationFile(options);

    log.info("Validation result: {} ", validationResult.getMessage());

    if (!validationResult.getValid()) {
      log.error(
          "Unable to run UUID pipeline. Please check validation file: "
              + ValidationUtils.getValidationFilePath(options));
      return;
    }

    Pipeline p = Pipeline.create(options);

    ALAPipelinesConfig config =
        ALAPipelinesConfigFactory.getInstance(
                options.getHdfsSiteConfig(), options.getCoreSiteConfig(), options.getProperties())
            .get();

    // build the directory path for existing identifiers
    String alaRecordDirectoryPath =
        String.join(
            "/",
            options.getTargetPath(),
            options.getDatasetId().trim(),
            options.getAttempt().toString(),
            IDENTIFIERS_DIR,
            ALARecordTypes.ALA_UUID.name().toLowerCase());
    log.info("Output path {}", alaRecordDirectoryPath);

    // create key value store for data resource metadata
    KeyValueStore<String, ALACollectoryMetadata> dataResourceKvStore =
        ALAAttributionKVStoreFactory.create(config);

    // lookup collectory metadata for this data resource
    ALACollectoryMetadata collectoryMetadata = dataResourceKvStore.get(options.getDatasetId());
    if (collectoryMetadata.equals(ALACollectoryMetadata.EMPTY)) {
      log.error("Unable to retrieve dataset metadata for dataset: " + options.getDatasetId());
      System.exit(1);
    }

    // construct unique list of darwin core terms
    List<String> uniqueTerms = collectoryMetadata.getConnectionParameters().getTermsForUniqueKey();
    Boolean stripSpaces = collectoryMetadata.getConnectionParameters().getStrip();
    final boolean stripSpacesFinal = stripSpaces != null ? stripSpaces : false;
    Map<String, String> defaultValues = collectoryMetadata.getDefaultDarwinCoreValues();
    final Map<String, String> defaultValuesFinal =
        defaultValues != null ? defaultValues : Collections.emptyMap();

    if ((uniqueTerms == null || uniqueTerms.isEmpty())) {
      log.error(
          "Unable to proceed, No unique terms specified for dataset: " + options.getDatasetId());
      return;
    }

    final List<Term> uniqueDwcTerms = new ArrayList<>();
    for (String uniqueTerm : uniqueTerms) {
      Optional<DwcTerm> dwcTerm = getDwcTerm(uniqueTerm);
      if (dwcTerm.isPresent()) {
        uniqueDwcTerms.add(dwcTerm.get());
      } else {
        // create a UnknownTerm for non DWC fields
        uniqueDwcTerms.add(UnknownTerm.build(uniqueTerm.trim()));
      }
    }

    final String datasetID = options.getDatasetId();

    log.info(
        "Transform 1: ExtendedRecord er ->  <uniqueKey, er.getId()> - this generates the UniqueKey.....");
    PCollection<KV<String, String>> extendedRecords =
        p.apply(
                AvroIO.read(ExtendedRecord.class)
                    .from(
                        String.join(
                            "/",
                            options.getTargetPath(),
                            options.getDatasetId().trim(),
                            options.getAttempt().toString(),
                            "interpreted",
                            "verbatim",
                            "*.avro")))
            .apply(
                ParDo.of(
                    new DoFn<ExtendedRecord, KV<String, String>>() {
                      @ProcessElement
                      public void processElement(
                          @Element ExtendedRecord source,
                          OutputReceiver<KV<String, String>> out,
                          ProcessContext c) {
                        out.output(
                            KV.of(
                                ValidationUtils.generateUniqueKey(
                                    datasetID,
                                    source,
                                    uniqueDwcTerms,
                                    defaultValuesFinal,
                                    stripSpacesFinal,
                                    true),
                                source.getId()));
                      }
                    }));

    PCollection<KV<String, ALAUUIDRecord>> alaUuids;

    log.info("Transform 2: ALAUUIDRecord ur ->  <uniqueKey, uuid> (assume incomplete)");
    FileSystem fs =
        FsUtils.getFileSystem(
            options.getHdfsSiteConfig(), options.getCoreSiteConfig(), options.getInputPath());
    Path path = new Path(alaRecordDirectoryPath);

    boolean initialLoad = !fs.exists(path);

    // load existing UUIDs if available
    if (fs.exists(path)) {
      alaUuids =
          p.apply(AvroIO.read(ALAUUIDRecord.class).from(alaRecordDirectoryPath + "/*.avro"))
              .apply(ParDo.of(new ALAUUIDRecordKVFcn()));
    } else {

      TypeDescriptor<KV<String, ALAUUIDRecord>> td =
          new TypeDescriptor<KV<String, ALAUUIDRecord>>() {};
      log.warn(
          "Previous ALAUUIDRecord records where not found. This is expected for new datasets, but is a problem "
              + "for previously loaded datasets - will mint new ones......");
      alaUuids = p.apply(Create.empty(td));
    }

    log.info("Create join collection");
    PCollection<KV<String, KV<String, ALAUUIDRecord>>> joinedPcollection =
        org.apache.beam.sdk.extensions.joinlibrary.Join.fullOuterJoin(
            extendedRecords, alaUuids, NO_ID_MARKER_STRING, NO_ID_MARKER);

    log.info("Create ALAUUIDRecords and write out to AVRO");
    joinedPcollection
        .apply(ParDo.of(new CreateALAUUIDRecordFcn()))
        .apply(
            AvroIO.write(ALAUUIDRecord.class)
                .to(alaRecordDirectoryPath + "_new/interpret")
                .withSuffix(".avro")
                .withCodec(BASE_CODEC));

    log.info("Running the pipeline");
    PipelineResult result = p.run();
    result.waitUntilFinish();

    Path existingVersionUUids = new Path(alaRecordDirectoryPath);
    Path newVersionUUids = new Path(alaRecordDirectoryPath + "_new");

    if (!initialLoad) {
      log.info("Checking the percentage change in new UUIDs:");

      // has anything changed ????
      Double newUuids = getMetricCount(result, NEW_UUIDS);
      Double preservedUuid = getMetricCount(result, PRESERVED_UUIDS);
      Double orphaned = getMetricCount(result, ORPHANED_UNIQUE_KEYS);
      Integer percentageChange = (int) ((newUuids / (newUuids + preservedUuid)) * 100d);

      log.info(
          "{}: {}, {}: {}, {}: {}",
          NEW_UUIDS,
          newUuids,
          PRESERVED_UUIDS,
          preservedUuid,
          ORPHANED_UNIQUE_KEYS,
          orphaned);

      log.info(
          "Percentage UUID change: {}, allowed percentage: {}, override percentage check: {}",
          percentageChange,
          options.getPercentageChangeAllowed(),
          options.getOverridePercentageCheck());

      if (percentageChange > options.getPercentageChangeAllowed()
          && !options.getOverridePercentageCheck()) {
        log.warn(
            "The number of records with new identifiers has changed by 50%. This update will be blocked.");
        fs.delete(newVersionUUids, true);
        return;
      }

    } else {
      log.info("As this is an initial load for this dataset, UUID change checks are not ran.");
    }

    // rename backup existing
    if (fs.exists(existingVersionUUids)) {
      String backupPath = alaRecordDirectoryPath + BACKUP_SUFFIX + System.currentTimeMillis();
      log.info("Backing up existing UUIDs to {}", backupPath);
      fs.rename(existingVersionUUids, new Path(backupPath));
    }

    // rename new version to current path
    fs.rename(newVersionUUids, existingVersionUUids);
    log.info("Pipeline complete.");

    // prune backups, reducing the number of backups kept to conserve disk space
    pruneBackups(options, fs);

    log.info("Writing metrics.....");
    MetricsHandler.saveCountersToTargetPathFile(options, result.metrics());
    log.info("Writing metrics written.");
  }

  /**
   * Prune backups of UUIDs.
   *
   * @param options
   * @param fs
   * @throws Exception
   */
  public static void pruneBackups(UUIDPipelineOptions options, FileSystem fs) throws Exception {

    String alaRecordDirectoryPath =
        String.join(
            "/",
            options.getTargetPath(),
            options.getDatasetId().trim(),
            options.getAttempt().toString(),
            IDENTIFIERS_DIR);

    log.info("Checking for backups to prune....{}", alaRecordDirectoryPath);
    FileStatus[] fileStatuses = fs.listStatus(new Path(alaRecordDirectoryPath));

    // retrieve a list backup directories
    List<FileStatus> backups = new ArrayList<>();
    for (FileStatus fileStatus : fileStatuses) {
      String name = fileStatus.getPath().getName();
      if (name.startsWith(ALARecordTypes.ALA_UUID.name().toLowerCase() + BACKUP_SUFFIX)) {
        backups.add(fileStatus);
      }
    }

    // sort by newest to oldest
    backups.sort(
        new Comparator<FileStatus>() {
          @Override
          public int compare(FileStatus o1, FileStatus o2) {
            return o1.getModificationTime() < o2.getModificationTime() ? 1 : -1;
          }
        });

    if (log.isDebugEnabled()) {
      for (FileStatus fileStatus : backups) {
        String name = fileStatus.getPath().getName();
        long modifiedTime = fileStatus.getModificationTime();
        log.info(
            "{} = {}", name, new SimpleDateFormat("dd MMM HH:mm").format(new Date(modifiedTime)));
      }
    }

    if (backups.size() > options.getNumberOfBackupsToKeep()) {
      List<FileStatus> toPrune =
          backups.subList(options.getNumberOfBackupsToKeep(), backups.size());
      toPrune.stream()
          .forEach(
              toPruneFs -> {
                try {
                  log.info("Pruning backup {} ", toPruneFs.getPath().getName());
                  fs.delete(toPruneFs.getPath(), true);
                } catch (Exception e) {
                  log.error("Unable to prune {} ", toPruneFs.getPath().getName());
                }
              });
    }
  }

  @NotNull
  private static Double getMetricCount(PipelineResult result, String countName) {
    Iterator<MetricResult<Long>> iter =
        result
            .metrics()
            .queryMetrics(
                MetricsFilter.builder()
                    .addNameFilter(MetricNameFilter.named(CreateALAUUIDRecordFcn.class, countName))
                    .build())
            .getCounters()
            .iterator();

    if (iter.hasNext()) {
      return iter.next().getAttempted().doubleValue();
    }

    return 0d;
  }

  /** Function to create ALAUUIDRecords. */
  static class CreateALAUUIDRecordFcn
      extends DoFn<KV<String, KV<String, ALAUUIDRecord>>, ALAUUIDRecord> {

    // TODO this counts are inaccurate when using SparkRunner
    private final Counter orphanedUniqueKeys =
        Metrics.counter(CreateALAUUIDRecordFcn.class, ORPHANED_UNIQUE_KEYS);
    private final Counter newUuids = Metrics.counter(CreateALAUUIDRecordFcn.class, NEW_UUIDS);
    private final Counter preservedUuids =
        Metrics.counter(CreateALAUUIDRecordFcn.class, PRESERVED_UUIDS);

    @ProcessElement
    public void processElement(
        @Element KV<String, KV<String, ALAUUIDRecord>> uniqueKeyMap,
        OutputReceiver<ALAUUIDRecord> out) {

      // get the matched ExtendedRecord.getId()
      String id = uniqueKeyMap.getValue().getKey();

      // get the UUID
      ALAUUIDRecord uuidRecord = uniqueKeyMap.getValue().getValue();

      ALAUUIDRecord uuidRecordToEmit;

      // get the constructed key
      String uniqueKey = uniqueKeyMap.getKey();

      // if UUID == NO_ID_MARKER, we have a new record so we need a new UUID.
      if (Objects.equals(uuidRecord.getId(), NO_ID_MARKER_STRING)) {
        newUuids.inc();

        // reassign
        uuidRecordToEmit =
            ALAUUIDRecord.newBuilder()
                .setFirstLoaded(ALAUUIDMintingPipeline.NOW)
                .setUniqueKey(uniqueKey)
                .setUuid(UUID.randomUUID().toString())
                .setId(id)
                .build();

      } else if (Objects.equals(id, NO_ID_MARKER_STRING)) {
        uuidRecordToEmit =
            ALAUUIDRecord.newBuilder()
                .setFirstLoaded(uuidRecord.getFirstLoaded())
                .setUniqueKey(uuidRecord.getUniqueKey())
                .setUuid(uuidRecord.getUuid())
                .setId(REMOVED_PREFIX_MARKER + UUID.randomUUID().toString())
                .build();

        orphanedUniqueKeys.inc();
      } else {
        uuidRecordToEmit =
            ALAUUIDRecord.newBuilder()
                .setFirstLoaded(uuidRecord.getFirstLoaded())
                .setUniqueKey(uuidRecord.getUniqueKey())
                .setUuid(uuidRecord.getUuid())
                .setId(id)
                .build();

        preservedUuids.inc();
      }

      out.output(uuidRecordToEmit);
    }
  }

  /** Transform to create a map of unique keys built from previous runs and UUID. */
  static class ALAUUIDRecordKVFcn extends DoFn<ALAUUIDRecord, KV<String, ALAUUIDRecord>> {
    @ProcessElement
    public void processElement(
        @Element ALAUUIDRecord alaUUIDRecord, OutputReceiver<KV<String, ALAUUIDRecord>> out) {
      out.output(KV.of(alaUUIDRecord.getUniqueKey(), alaUUIDRecord));
    }
  }

  /**
   * Match the darwin core term which has been supplied in simple camel case format e.g.
   * catalogNumber.
   */
  static Optional<DwcTerm> getDwcTerm(String name) {
    try {
      return Optional.of(DwcTerm.valueOf(name));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
