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

import static de.interactive_instruments.etf.testdriver.bsx.Types.TEST_ITEM_TYPES;
import static de.interactive_instruments.etf.testdriver.bsx.Types.TEST_OBJECT_TYPES;

import java.io.IOException;
import java.util.*;

import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dto.Dto;
import de.interactive_instruments.etf.dal.dto.capabilities.TagDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.dal.dto.test.TestItemTypeDto;
import de.interactive_instruments.etf.dal.dto.translation.TranslationTemplateBundleDto;
import de.interactive_instruments.etf.model.*;
import de.interactive_instruments.etf.testdriver.*;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;

/**
 * Used to propagate:
 * - Test Object Types (static),
 * - Test Item Types (static),
 * - Component Info (static),
 * - Executable Test Suites (dynamically reloaded on change),
 * - Translation Template Bun dles (dynamically reloaded on change)
 *
 * The Component Info is propagated by the Test Driver
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class BsxTypeLoader extends AbstractTypeLoader {

	private final ConfigProperties configProperties;

	/**
	 * Default constructor.
	 */
	public BsxTypeLoader(final DataStorage dataStorage) {
		super(dataStorage, new ArrayList<TypeBuildingFileVisitor.TypeBuilder<? extends Dto>>() {
			{
				add(new BsxEtsBuilder(dataStorage.getDao(ExecutableTestSuiteDto.class)));
				add(new TranslationTemplateBundleBuilder(dataStorage.getDao(TranslationTemplateBundleDto.class)));
				add(new TestObjectTypeBuilder(dataStorage.getDao(TestObjectTypeDto.class)));
				add(new TagBuilder(dataStorage.getDao(TagDto.class)));
			}
		});
		this.configProperties = new ConfigProperties(EtfConstants.ETF_PROJECTS_DIR);
	}

	@Override
	public void doInit()
			throws ConfigurationException, InitializationException, InvalidStateTransitionException {

		this.configProperties.expectAllRequiredPropertiesSet();
		this.etsDir = configProperties.getPropertyAsFile(EtfConstants.ETF_PROJECTS_DIR);
		try {
			this.etsDir.expectDirIsReadable();
		} catch (IOException e) {
			throw new InitializationException(e);
		}

		// First propagate static types
		final WriteDao<TestObjectTypeDto> testObjectTypeDao = ((WriteDao<TestObjectTypeDto>) dataStorageCallback.getDao(TestObjectTypeDto.class));
		final WriteDao<TestItemTypeDto> testItemTypeDao = ((WriteDao<TestItemTypeDto>) dataStorageCallback.getDao(TestItemTypeDto.class));
		try {
			testObjectTypeDao.deleteAllExisting(TEST_OBJECT_TYPES.keySet());
			testObjectTypeDao.addAll(TEST_OBJECT_TYPES.values());

			testItemTypeDao.deleteAllExisting(TEST_ITEM_TYPES.keySet());
			testItemTypeDao.addAll(TEST_ITEM_TYPES.values());
		} catch (final StorageException e) {
			try {
				testObjectTypeDao.deleteAllExisting(TEST_OBJECT_TYPES.keySet());
			} catch (StorageException e2) {
				ExcUtils.suppress(e2);
			}
			try {
				testItemTypeDao.deleteAllExisting(TEST_ITEM_TYPES.keySet());
			} catch (StorageException e3) {
				ExcUtils.suppress(e3);
			}
			throw new InitializationException(e);
		}
	}

	@Override
	public ConfigPropertyHolder getConfigurationProperties() {
		return configProperties;
	}

	TestObjectTypeDto getTestObjectTypeById(final EID id) {
		return TEST_OBJECT_TYPES.get(id);
	}

	Collection<TestObjectTypeDto> getTestObjectTypes() {
		return TEST_OBJECT_TYPES.values();
	}
}
