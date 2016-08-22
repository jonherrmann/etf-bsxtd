/**
 * Copyright 2010-2016 interactive instruments GmbH
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

import java.io.IOException;
import java.util.*;

import org.basex.BaseX;
import org.basex.query.QueryException;

import de.interactive_instruments.etf.component.ComponentInfo;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.run.TestRunDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.testdriver.ComponentInitializer;
import de.interactive_instruments.etf.testdriver.EtsLookupRequest;
import de.interactive_instruments.etf.testdriver.TestDriver;
import de.interactive_instruments.etf.testdriver.TestTask;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;

/**
 * BaseX test driver component
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */

@ComponentInitializer(id = "4dddc9e2-1b21-40b7-af70-6a2d156ad130")
public class BsxTestDriver implements TestDriver {

	private BsxExecutableTestSuiteHolder etsHolder;

	private final ComponentInfo info = new ComponentInfo() {
		@Override
		public String getName() {
			return "BaseX test driver";
		}

		@Override
		public EID getId() {
			return EidFactory.getDefault().createAndPreserveStr("4dddc9e2-1b21-40b7-af70-6a2d156ad130");
		}

		@Override
		public String getVersion() {
			return this.getClass().getPackage().getImplementationVersion();
		}

		@Override
		public String getVendor() {
			return this.getClass().getPackage().getImplementationVendor();
		}

		@Override
		public String getDescription() {
			return "Test driver for BaseX " + BaseX.class.getPackage().getImplementationVersion();
		}
	};

	public static final long DEFAULT_MAX_CHUNK_SIZE = 33500000000L;
	final private ConfigProperties configProperties = new ConfigProperties(BsxConstants.PROJECT_DIR_KEY);

	public BsxTestDriver() throws StoreException {}

	@Override
	public Collection<ExecutableTestSuiteDto> getExecutableTestSuites() {
		return etsHolder.getExecutableTestSuites();
	}

	@Override
	public Collection<TestObjectTypeDto> getTestObjectTypes() {
		return etsHolder.getTestObjectTypes();
	}

	@Override
	final public ComponentInfo getInfo() {
		return info;
	}

	@Override
	public void lookupExecutableTestSuites(final EtsLookupRequest etsLookupRequest) {
		final Set<EID> etsIds = etsLookupRequest.getUnknownEts();
		final Set<ExecutableTestSuiteDto> knownEts = new HashSet<>();
		for (final EID etsId : etsIds) {
			final ExecutableTestSuiteDto ets = etsHolder.getExecutableTestSuiteById(etsId);
			if (ets != null) {
				knownEts.add(ets);
			}
		}
		etsLookupRequest.addKnownEts(knownEts);
	}

	@Override
	public TestTask createTestTask(final TestTaskDto testTaskDto) throws ConfigurationException {
		/*
			try {
				final TestProject project = getTestProjectStore().getById(testRunDto.getTestProject().getId());
				testRunDto.getTestReport().setLabel(testRunDto.getLabel());
				final BasexTestReport report = new BasexTestReport(testRunDto.getTestReport(), testRunDto.getUsernameOfInitiator());
				final TestRun testRun = new BasexTestRun(testRunDto, project, report);
				return new BasexTestTask(testRun, new IFile(testRunDto.getTestProject().getUri()),
						configProperties
								.getPropertyOrDefaultAsLong(BsxConstants.DB_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE));
			} catch (StoreException | ObjectWithIdNotFoundException | QueryException | IOException e) {
				ExcUtils.suppress(e);
				throw new ConfigurationException(e.getMessage());
			}
			*/
		// get etse
		Objects.requireNonNull(testTaskDto, "Test Task not set").ensureValid();

		testTaskDto.ensureValid();
		// Get ETS
		testTaskDto.getTestObject().ensureValid();
		testTaskDto.getExecutableTestSuite().ensureValid();
		return new BasexTestTask(testTaskDto, configProperties
				.getPropertyOrDefaultAsLong(BsxConstants.DB_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE));

	}

	@Override
	public ConfigPropertyHolder getConfigurationProperties() {
		return this.configProperties;
	}

	@Override
	final public void init()
			throws ConfigurationException, IllegalStateException, InitializationException, InvalidStateTransitionException {
		configProperties.expectAllRequiredPropertiesSet();

		if (configProperties.getProperty("org.basex.path") != null) {
			System.setProperty("org.basex.path", configProperties.getProperty("org.basex.path"));
		}

		etsHolder = new BsxExecutableTestSuiteHolder();
		etsHolder.getConfigurationProperties().setPropertiesFrom(configProperties, true);
		etsHolder.init();
	}

	@Override
	public boolean isInitialized() {
		return false;
	}

	@Override
	public void release() {

	}
}
