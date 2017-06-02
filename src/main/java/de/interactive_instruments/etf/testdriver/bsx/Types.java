/**
 * Copyright 2010-2017 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.testdriver.bsx;

import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.test.TestItemTypeDto;
import de.interactive_instruments.etf.detector.TestObjectTypeDetectorManager;
import de.interactive_instruments.etf.model.DefaultEidMap;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.model.EidMap;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class Types {

	private Types() {}

	// Supported Test Object Types
	public static final EidMap<TestObjectTypeDto> BSX_SUPPORTED_TEST_OBJECT_TYPES = TestObjectTypeDetectorManager
			.getTypes("e1d4a306-7a78-4a3b-ae2d-cf5f0810853e");

	// Supported Test Item Types
	public static final EidMap<TestItemTypeDto> TEST_ITEM_TYPES = new DefaultEidMap<TestItemTypeDto>() {
		{
			{
				final TestItemTypeDto testItemTypeDto = new TestItemTypeDto();
				testItemTypeDto.setLabel("Test Data Query Test Step");
				testItemTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("f483e8e8-06b9-4900-ab36-adad0d7f22f0"));
				testItemTypeDto.setDescription("Test Data Query Test Step");
				testItemTypeDto.setReference(
						"https://github.com/interactive-instruments/etf-bsxtd/wiki/Test-Step-Types#teststeptype1");
				put(testItemTypeDto.getId(), testItemTypeDto);
			}
			{
				final TestItemTypeDto testItemTypeDto = new TestItemTypeDto();
				testItemTypeDto.setLabel("XQuery Test Assertion");
				testItemTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("f0edc596-49d2-48d6-a1a1-1ac581dcde0a"));
				testItemTypeDto.setDescription(
						"In an XQuery Test Assertion an XQuery expression is used to select XML from a data source that should be validated against an expected value");
				testItemTypeDto.setReference(
						"https://github.com/interactive-instruments/etf-bsxtd/wiki/Test-Assertion-Types#test-assertion-type-1");
				put(testItemTypeDto.getId(), testItemTypeDto);
			}
			// TODO move to SPI
			{
				final TestItemTypeDto testItemTypeDto = new TestItemTypeDto();
				testItemTypeDto.setLabel("Manual Test Assertion");
				testItemTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("b48eeaa3-6a74-414a-879c-1dc708017e11"));
				testItemTypeDto.setDescription("A Manual Test Assertion requires that a tester manually validates a result");
				testItemTypeDto.setReference(
						"https://github.com/interactive-instruments/etf-bsxtd/wiki/Test-Assertion-Types#test-assertion-type-2");
				put(testItemTypeDto.getId(), testItemTypeDto);
			}
			// TODO move to SPI
			{
				final TestItemTypeDto testItemTypeDto = new TestItemTypeDto();
				testItemTypeDto.setLabel("Disabled Test Assertion");
				testItemTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("92f22a19-2ec2-43f0-8971-c2da3eaafcd2"));
				testItemTypeDto.setDescription("");
				testItemTypeDto.setReference(
						"https://github.com/interactive-instruments/etf-bsxtd/wiki/Test-Assertion-Types#test-assertion-type-4");
				put(testItemTypeDto.getId(), testItemTypeDto);
			}
		}
	};
}
