/**
 * Copyright 2017 European Union, interactive instruments GmbH
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * This work was supported by the EU Interoperability Solutions for
 * European Public Administrations Programme (http://ec.europa.eu/isa)
 * through Action 1.17: A Reusable INSPIRE Reference Platform (ARE3NA).
 */
package de.interactive_instruments.etf.testdriver.bsx;

import static de.interactive_instruments.etf.EtfConstants.ETF_DATA_STORAGE_NAME;
import static de.interactive_instruments.etf.testdriver.bsx.BsxTestDriver.BSX_TEST_DRIVER_EID;
import static de.interactive_instruments.etf.testdriver.bsx.Types.BSX_SUPPORTED_TEST_OBJECT_TYPES;

import java.util.Collection;
import java.util.Objects;

import org.basex.BaseX;

import de.interactive_instruments.etf.component.ComponentInfo;
import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.DataStorageRegistry;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dto.IncompleteDtoException;
import de.interactive_instruments.etf.dal.dto.capabilities.ComponentDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.result.TestTaskResultDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.testdriver.AbstractTestDriver;
import de.interactive_instruments.etf.testdriver.ComponentInitializer;
import de.interactive_instruments.etf.testdriver.TestTask;
import de.interactive_instruments.etf.testdriver.TestTaskInitializationException;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.exceptions.config.InvalidPropertyException;
import de.interactive_instruments.properties.ConfigProperties;

/**
 * BaseX test driver component
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
@ComponentInitializer(id = BSX_TEST_DRIVER_EID)
public class BsxTestDriver extends AbstractTestDriver {

	public static final String BSX_TEST_DRIVER_EID = "4dddc9e2-1b21-40b7-af70-6a2d156ad130";
	public static final long DEFAULT_MAX_CHUNK_SIZE = 33500000000L;
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

	public BsxTestDriver() {
		super(new ConfigProperties(ETF_DATA_STORAGE_NAME, BsxConstants.PROJECT_DIR_KEY));
	}

	@Override
	public Collection<TestObjectTypeDto> getTestObjectTypes() {
		return BSX_SUPPORTED_TEST_OBJECT_TYPES.values();
	}

	@Override
	final public ComponentInfo getInfo() {
		return info;
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
			return new BasexTestTask(testTaskDto, ((WriteDao) dataStorageCallback.getDao(TestObjectDto.class)),
					configProperties.getPropertyOrDefaultAsLong(BsxConstants.DB_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE));
		} catch (InvalidPropertyException | IncompleteDtoException e) {
			throw new TestTaskInitializationException(e);
		}

	}

	@Override
	protected void doInit() throws ConfigurationException, InitializationException, InvalidStateTransitionException {
		dataStorageCallback = DataStorageRegistry.instance().get(configProperties.getProperty(ETF_DATA_STORAGE_NAME));
		if (dataStorageCallback == null) {
			throw new InvalidStateTransitionException("Data Storage not set");
		}

		if (configProperties.getProperty("org.basex.path") != null) {
			System.setProperty("org.basex.path", configProperties.getProperty("org.basex.path"));
		}

		propagateComponents();

		typeLoader = new BsxTypeLoader(dataStorageCallback);
		typeLoader.getConfigurationProperties().setPropertiesFrom(configProperties, true);
	}

	private void propagateComponents() throws InitializationException {
		// Propagate Component info from here
		final WriteDao<ComponentDto> componentDao = ((WriteDao<ComponentDto>) dataStorageCallback.getDao(ComponentDto.class));
		try {
			try {
				componentDao.delete(this.getInfo().getId());
			} catch (ObjectWithIdNotFoundException e) {
				ExcUtils.suppress(e);
			}
			componentDao.add(new ComponentDto(this.getInfo()));
		} catch (StorageException e) {
			throw new InitializationException(e);
		}
	}

	@Override
	protected void doRelease() {

	}
}
