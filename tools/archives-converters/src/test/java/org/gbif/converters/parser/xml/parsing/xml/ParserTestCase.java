/*
 * Copyright 2011 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.converters.parser.xml.parsing.xml;

import java.io.File;
import java.util.List;
import org.gbif.converters.parser.xml.OccurrenceParser;
import org.gbif.converters.parser.xml.model.RawOccurrenceRecord;
import org.gbif.converters.parser.xml.parsing.RawXmlOccurrence;
import org.junit.Before;

public abstract class ParserTestCase {

  OccurrenceParser occurrenceParser;

  @Before
  public void setUp() {
    occurrenceParser = new OccurrenceParser();
  }

  List<RawOccurrenceRecord> setupRor(String fileName) {
    File response = new File(fileName);
    RawXmlOccurrence xmlRecord = occurrenceParser.parseResponseFileToRawXml(response).get(0);

    return XmlFragmentParser.parseRecord(xmlRecord);
  }
}
