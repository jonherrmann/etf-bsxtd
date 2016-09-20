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

import java.util.*;

import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.DataStorageRegistry;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dto.IncompleteDtoException;
import de.interactive_instruments.etf.dal.dto.capabilities.ComponentDto;
import de.interactive_instruments.etf.dal.dto.result.TestTaskResultDto;
import de.interactive_instruments.etf.testdriver.*;
import de.interactive_instruments.exceptions.config.InvalidPropertyException;
import org.basex.BaseX;

import de.interactive_instruments.etf.component.ComponentInfo;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;

import static de.interactive_instruments.etf.EtfConstants.ETF_DATA_STORAGE_NAME;
import static de.interactive_instruments.etf.testdriver.bsx.BsxTestDriver.BSX_TEST_DRIVER_EID;

/**
 * BaseX test driver component
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
@ComponentInitializer(id = BSX_TEST_DRIVER_EID)
public class BsxTestDriver implements TestDriver {

	public static final String BSX_TEST_DRIVER_EID = "4dddc9e2-1b21-40b7-af70-6a2d156ad130";
	private BsxTypeLoader typeLoader;
	public static final long DEFAULT_MAX_CHUNK_SIZE = 33500000000L;
	final private ConfigProperties configProperties = new ConfigProperties(ETF_DATA_STORAGE_NAME, BsxConstants.PROJECT_DIR_KEY);
	private DataStorage dataStorageCallback;

	private final ComponentInfo info = new ComponentInfo() {
		@Override
		public String getName() {
			return "BaseX test driver";
		}

		@Override
		public EID getId() {
			return EidFactory.getDefault().createAndPreserveStr(BSX_TEST_DRIVER_EID);
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

	@Override
	public Collection<ExecutableTestSuiteDto> getExecutableTestSuites() {
		return typeLoader.getExecutableTestSuites();
	}

	@Override
	public Collection<TestObjectTypeDto> getTestObjectTypes() {
		return typeLoader.getTestObjectTypes();
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
			final ExecutableTestSuiteDto ets = typeLoader.getExecutableTestSuiteById(etsId);
			if (ets != null) {
				knownEts.add(ets);
			}
		}
		etsLookupRequest.addKnownEts(knownEts);
	}

	@Override
	public TestTask createTestTask(final TestTaskDto testTaskDto) throws TestTaskInitializationException {
		try {
			Objects.requireNonNull(testTaskDto, "Test Task not set").ensureBasicValidity();

			// Get ETS
			testTaskDto.getTestObject().ensureBasicValidity();
			testTaskDto.getExecutableTestSuite().ensureBasicValidity();
			final TestTaskResultDto testTaskResult = new TestTaskResultDto();
			testTaskResult.setId(EidFactory.getDefault().createRandomId());
			testTaskDto.setTestTaskResult(testTaskResult);
			return new BasexTestTask(testTaskDto, configProperties
					.getPropertyOrDefaultAsLong(BsxConstants.DB_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE), dataStorageCallback);
		}catch (InvalidPropertyException | IncompleteDtoException e) {
			throw new TestTaskInitializationException(e);
		}

	}

	@Override
	public ConfigPropertyHolder getConfigurationProperties() {
		return this.configProperties;
	}

	@Override
	final public void init()
			throws ConfigurationException, IllegalStateException, InitializationException, InvalidStateTransitionException {
		configProperties.expectAllRequiredPropertiesSet();
		dataStorageCallback = DataStorageRegistry.instance().get(configProperties.getProperty(ETF_DATA_STORAGE_NAME));
		if(dataStorageCallback== null) {
			throw new InvalidStateTransitionException("Data Storage not set");
		}

		if (configProperties.getProperty("org.basex.path") != null) {
			System.setProperty("org.basex.path", configProperties.getProperty("org.basex.path"));
		}

		propagateComponents();

		typeLoader = new BsxTypeLoader(dataStorageCallback);
		typeLoader.getConfigurationProperties().setPropertiesFrom(configProperties, true);
		typeLoader.init();
	}

	private void propagateComponents() throws InitializationException {
		// Propagate Component info from here
		final WriteDao<ComponentDto> componentDao = ((WriteDao<ComponentDto>)
				dataStorageCallback.getDao(ComponentDto.class));
		try {
			try {
				componentDao.delete(this.getInfo().getId());
			} catch (ObjectWithIdNotFoundException e) {
				ExcUtils.suppress(e);
			}
			componentDao.add(new ComponentDto(this.getInfo()));
		}catch (StorageException e) {
			throw new InitializationException(e);
		}
	}

	@Override
	public boolean isInitialized() {
		return dataStorageCallback!=null && typeLoader !=null && typeLoader.isInitialized();
	}

	@Override
	public void release() {
		typeLoader.release();
	}
}
