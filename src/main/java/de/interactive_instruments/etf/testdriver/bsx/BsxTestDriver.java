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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.basex.BaseX;

import de.interactive_instruments.etf.component.ComponentInfo;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.run.TestRunDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.testengine.ComponentInitializer;
import de.interactive_instruments.etf.testengine.TestEngine;
import de.interactive_instruments.etf.testengine.TestRun;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;

/**
 * BaseX test driver component
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */

@ComponentInitializer(id = "BSX")
public class BsxTestDriver implements TestEngine {

	private BsxExecutableTestSuiteHolder etsHolder;

	private final ComponentInfo info = new ComponentInfo() {
		@Override
		public String getName() {
			return "BaseX test driver";
		}

		@Override
		public String getId() {
			return "BSX";
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
	public TestRun createTestRun(final TestRunDto testRun) throws ConfigurationException {
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
		Objects.requireNonNull(testRun, "Test Run not set").ensureValid();

		final List<TestTaskDto> testTaskDtos = testRun.getTestTasks();
		final List<BsxTestTask> bsxTestTasks = new ArrayList();

		for (final TestTaskDto testTaskDto : testTaskDtos) {
			testTaskDto.ensureValid();
			// Get ETS
			final EID etsId = testTaskDto.getExecutableTestSuite().getId();
			final ExecutableTestSuiteDto etsDto = etsHolder.getExecutableTestSuiteById(etsId);
			testTaskDto.setExecutableTestSuite(etsDto);
			testTaskDto.getTestObject().ensureValid();
			bsxTestTasks.add(new BsxTestTask(testTaskDto));
		}
		return new BsxTestRun(bsxTestTasks);

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
