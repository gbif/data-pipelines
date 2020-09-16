package au.org.ala.kvs;

import static org.junit.Assert.*;

import au.org.ala.kvs.cache.ALAAttributionKVStoreFactory;
import au.org.ala.kvs.cache.ALACollectionKVStoreFactory;
import au.org.ala.kvs.client.ALACollectionLookup;
import au.org.ala.kvs.client.ALACollectionMatch;
import au.org.ala.kvs.client.ALACollectoryMetadata;
import au.org.ala.kvs.client.ConnectionParameters;
import au.org.ala.util.TestUtils;
import okhttp3.mockwebserver.MockWebServer;
import org.gbif.kvs.KeyValueStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for Attribution KV store */
public class AttributionKVStoreTestIT {

  MockWebServer server;

  @Before
  public void setup() throws Exception {
    // clear up previous test runs
    server = TestUtils.createMockCollectory();
    server.start(3939);
  }

  @After
  public void teardown() throws Exception {
    server.shutdown();
  }

  @Test
  public void testAttributionLookup() throws Exception {

    KeyValueStore<String, ALACollectoryMetadata> kvs =
        ALAAttributionKVStoreFactory.create(TestUtils.getConfig());
    ALACollectoryMetadata m = kvs.get("dr893");
    ConnectionParameters connParams = m.getConnectionParameters();

    assertNotNull(m.getName());
    assertNotNull(connParams);
    assertNotNull(connParams.getUrl());
    assertNotNull(connParams.getTermsForUniqueKey());
    assertFalse(connParams.getTermsForUniqueKey().isEmpty());
    assertNotNull(m.getDefaultDarwinCoreValues());
    assertFalse(m.getDefaultDarwinCoreValues().isEmpty());
    assertNotNull(m.getProvenance());
    assertNotNull(m.getTaxonomyCoverageHints());
    assertTrue(m.getTaxonomyCoverageHints().size() == 0);

    kvs.close();
  }

  @Test
  public void testAttributionLookupMultiUrl() throws Exception {

    KeyValueStore<String, ALACollectoryMetadata> kvs =
        ALAAttributionKVStoreFactory.create(TestUtils.getConfig());
    ALACollectoryMetadata m = kvs.get("dr807");
    ConnectionParameters connParams = m.getConnectionParameters();

    assertNotNull(m.getName());
    assertNotNull(connParams);
    assertNotNull(connParams.getUrl());
    kvs.close();
  }

  @Test
  public void testAttributionConnectionIssues() throws Exception {
    KeyValueStore<String, ALACollectoryMetadata> kvs =
        ALAAttributionKVStoreFactory.create(TestUtils.getConfig());
    try {
      ALACollectoryMetadata m = kvs.get("dr893XXXXXX");
      fail("Exception not thrown");
    } catch (RuntimeException e) {
      // expected
    }
    kvs.close();
  }

  @Test
  public void testAttributionLookupFail() throws Exception {

    KeyValueStore<String, ALACollectoryMetadata> kvs =
        ALAAttributionKVStoreFactory.create(TestUtils.getConfig());
    try {
      ALACollectoryMetadata m = kvs.get("dr893XXXXXXX");
      fail("Exception not thrown");
    } catch (RuntimeException e) {
      // expected
    }
  }

  @Test
  public void testCollectionLookup() throws Exception {

    KeyValueStore<ALACollectionLookup, ALACollectionMatch> kvs =
        ALACollectionKVStoreFactory.create(TestUtils.getConfig());
    ALACollectionLookup lookup =
        ALACollectionLookup.builder().institutionCode("CSIRO").collectionCode("ANIC").build();
    ALACollectionMatch m = kvs.get(lookup);
    assertNotNull(m.getCollectionUid());
    assertEquals("co13", m.getCollectionUid());
  }

  @Test
  public void testCollectionLookupFail() throws Exception {

    KeyValueStore<ALACollectionLookup, ALACollectionMatch> kvs =
        ALACollectionKVStoreFactory.create(TestUtils.getConfig());
    ALACollectionLookup lookup =
        ALACollectionLookup.builder().institutionCode("CSIROCXXX").collectionCode("ANIC").build();
    ALACollectionMatch m = kvs.get(lookup);
    assertNull(m.getCollectionUid());
    assertEquals(ALACollectionMatch.EMPTY, m);
    kvs.close();
  }
}
