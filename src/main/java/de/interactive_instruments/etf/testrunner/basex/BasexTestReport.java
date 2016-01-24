/**
 * Copyright 2016 interactive instruments GmbH
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
package de.interactive_instruments.etf.testrunner.basex;

import de.interactive_instruments.container.ContainerFactory;
import de.interactive_instruments.etf.dal.dto.result.TestReportDto;
import de.interactive_instruments.etf.model.item.EID;
import de.interactive_instruments.etf.model.result.AbstractTestReport;

import java.net.URI;
import java.util.Date;

/**
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class BasexTestReport extends AbstractTestReport {

    /*
    protected BasexTestReport(EID id, String username, URI publicationLocation) {
        super(id, username, appendixFactory);
        this.publicationLocation=publicationLocation;
        this.startTimestamp=new Date();
    }
    */

  public BasexTestReport(TestReportDto testReport, String usernameOfInitiator) {
    super(testReport.getId(), usernameOfInitiator, testReport.getContainerFactory());
    this.publicationLocation = testReport.getPublicationLocation();
    this.testObject = new BasexTestObject(testReport.getTestObject());
    this.label = testReport.getLabel();
  }


}
