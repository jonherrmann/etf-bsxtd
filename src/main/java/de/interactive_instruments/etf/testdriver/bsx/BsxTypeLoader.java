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

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.*;
import java.util.stream.Collectors;

import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.StreamWriteDao;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dto.Dto;
import de.interactive_instruments.etf.dal.dto.test.TestItemTypeDto;
import de.interactive_instruments.etf.dal.dto.translation.TranslationTemplateBundleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.interactive_instruments.Configurable;
import de.interactive_instruments.IFile;
import de.interactive_instruments.Releasable;
import de.interactive_instruments.UriUtils;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.model.*;
import de.interactive_instruments.etf.testdriver.TypeBuildingFileVisitor;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.io.FileChangeListener;
import de.interactive_instruments.io.RecursiveDirWatcher;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;

/**
 * Used to propagate:
 * - Test Object Types (static),
 * - Test Item Types (static),
 * - Component Info (static),
 * - Executable Test Suites (dynamically reloaded on change),
 * - Translation Template Bundles (dynamically reloaded on change)
 *
 * The Component Info is propagated by the Test Driver
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class BsxTypeLoader implements Configurable, Releasable, FileChangeListener {

	// Supported Test Object Typess
	private final EidMap<TestObjectTypeDto> testObjectTypes = new DefaultEidMap<TestObjectTypeDto>() {
		{
			final TestObjectTypeDto testObjectTypeDto = new TestObjectTypeDto();
			testObjectTypeDto.setLabel("INSPIRE data set in GML");
			testObjectTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("e1d4a306-7a78-4a3b-ae2d-cf5f0810853e"));
			testObjectTypeDto.setDescription("A set of XML documents. "
					+ "Each document is either a WFS 2.0 FeatureCollection, a GML 3.2 Feature Collection "
					+ "or an INSPIRE Base 3.2 or 3.3 SpatialDataSet. All features are GML 3.2 Features.");
			put(testObjectTypeDto.getId(), testObjectTypeDto);
		}
	};

	// Supported Test Item Types
	private final EidMap<TestItemTypeDto> testItemTypes = new DefaultEidMap<TestItemTypeDto>() {
		{
			{
				final TestItemTypeDto testItemTypeDto = new TestItemTypeDto();
				testItemTypeDto.setLabel("Test Data Query Test Step");
				testItemTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("f483e8e8-06b9-4900-ab36-adad0d7f22f0"));
				testItemTypeDto.setDescription("TODO description");
				testItemTypeDto.setReference("https://github.com/interactive-instruments/etf-bsxtd/wiki/Test-Step-Types#teststeptype1");
				put(testItemTypeDto.getId(), testItemTypeDto);
			}
			{
				final TestItemTypeDto testItemTypeDto = new TestItemTypeDto();
				testItemTypeDto.setLabel("XQuery Test Assertion");
				testItemTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("f0edc596-49d2-48d6-a1a1-1ac581dcde0a"));
				testItemTypeDto.setDescription("In an XQuery Test Assertion an XQuery expression is used to select XML from a data source that should be validated against an expected value");
				testItemTypeDto.setReference("https://github.com/interactive-instruments/etf-bsxtd/wiki/Test-Assertion-Types#test-assertion-type-1");
				put(testItemTypeDto.getId(), testItemTypeDto);
			}
			{
				final TestItemTypeDto testItemTypeDto = new TestItemTypeDto();
				testItemTypeDto.setLabel("Manual Test Assertion");
				testItemTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("b48eeaa3-6a74-414a-879c-1dc708017e11"));
				testItemTypeDto.setDescription("A Manual Test Assertion requires that a tester manually validates a result");
				testItemTypeDto.setReference("https://github.com/interactive-instruments/etf-bsxtd/wiki/Test-Assertion-Types#test-assertion-type-2");
				put(testItemTypeDto.getId(), testItemTypeDto);
			}
			{
				final TestItemTypeDto testItemTypeDto = new TestItemTypeDto();
				testItemTypeDto.setLabel("Disabled Test Assertion");
				testItemTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("92f22a19-2ec2-43f0-8971-c2da3eaafcd2"));
				testItemTypeDto.setDescription("");
				testItemTypeDto.setReference("https://github.com/interactive-instruments/etf-bsxtd/wiki/Test-Assertion-Types#test-assertion-type-4");
				put(testItemTypeDto.getId(), testItemTypeDto);
			}

		}
	};

	private final ConfigProperties configProperties;
	private final DataStorage dataStorageCallback;
	private IFile etsDir;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private boolean initialized = false;
	private RecursiveDirWatcher watcher;
	private final List<TypeBuildingFileVisitor.TypeBuilder> builders;
	// Path -> Dto
	private Map<String, Dto> propagatedFiles = new LinkedHashMap<>();
	private EidMap<ExecutableTestSuiteDto> etsCache = new DefaultEidMap<>(new HashMap<>(32));

	static class EtsBuilder implements TypeBuildingFileVisitor.TypeBuilder {
		private final Logger logger;
		private final StreamWriteDao<ExecutableTestSuiteDto> writeDao;

		public EtsBuilder(final DataStorage dataStorageCallback, final Logger logger) {
			writeDao = ((StreamWriteDao<ExecutableTestSuiteDto>) dataStorageCallback.getDao(
					ExecutableTestSuiteDto.class));
			this.logger = logger;
		}

		@Override public Dto process(final Path path) {
			if(path.toString().endsWith(BsxConstants.ETS_DEF_FILE_SUFFIX)) {
				try {
					final File file = path.toFile();
					final byte[] hash = UriUtils.hashFromTimestampOrContent(file.toURI()).getBytes(StandardCharsets.UTF_8);
					final FileInputStream fileInputStream = new FileInputStream(file);
					return writeDao.add(fileInputStream, dto -> {
						dto.setItemHash(hash);
						dto.setLocalPath(file.getAbsolutePath());
						return dto;
					});
				} catch (IOException | StorageException e) {
					logger.error("Error creating Executable Test Suite from file {}", path, e);
				}
			}
			return null;
		}
	}

	static class TranslationTemplateBundleBuilder implements TypeBuildingFileVisitor.TypeBuilder {
		private final Logger logger;
		private final StreamWriteDao<TranslationTemplateBundleDto> writeDao;

		public TranslationTemplateBundleBuilder(final DataStorage dataStorageCallback, final Logger logger) {
			writeDao = ((StreamWriteDao<TranslationTemplateBundleDto>) dataStorageCallback.getDao(
					TranslationTemplateBundleDto.class));
			this.logger = logger;
		}

		@Override public Dto process(final Path path) {
			if(path.toString().endsWith(BsxConstants.TRANSLATION_TEMPLATE_BUNDLE_SUFFIX)) {
				try {
					final File file = path.toFile();
					final FileInputStream fileInputStream = new FileInputStream(file);
					return writeDao.add(fileInputStream, dto -> {
						dto.setSource(file.toURI());
						return dto;
					});
				} catch (IOException | StorageException e) {
					logger.error("Error creating Translation Template Bundle from file {}", path, e);
				}
			}
			return null;
		}
	}


	@Override
	public synchronized void filesChanged(final Map<Path, WatchEvent.Kind> eventMap, final Set<Path> dirs) {
		final TypeBuildingFileVisitor visitor =
				new TypeBuildingFileVisitor(builders);

		dirs.forEach(d -> {
			logger.info("Watch service reports changes in directory: " + d.toString());
			try {
				Files.walkFileTree(d, visitor);
			} catch (IOException e) {
				logger.error("Failed to walk file tree: " + e.getMessage());
			}
		});

		final HashSet<Map.Entry<String, Dto>> tmpCopy = new HashSet<>((propagatedFiles.entrySet()));
		tmpCopy.removeAll(visitor.getPropagatedFiles().entrySet());
		for (final Map.Entry<String, Dto> entry : tmpCopy) {
			final Dto dto = entry.getValue();
			logger.info("Unregistering {} as file {} has been removed ", dto.getDescriptiveLabel(), entry.getKey());
			try {
				((WriteDao)dataStorageCallback.getDao(dto.getClass())).delete(dto.getId());
			} catch (ObjectWithIdNotFoundException | StorageException e) {
				logger.error("Could not unregister {} : ",dto.getDescriptiveLabel(), e);
			}
		}
		propagatedFiles = visitor.getPropagatedFiles();
		etsCache.clear();
		propagatedFiles.values().stream().filter(dto ->
				dto instanceof ExecutableTestSuiteDto).forEach(dto ->
					etsCache.put(dto.getId(), (ExecutableTestSuiteDto) dto));
	}

	/**
	 * Default constructor.
	 */
	public BsxTypeLoader(final DataStorage dataStorage) {
		this.configProperties = new ConfigProperties(EtfConstants.ETF_PROJECTS_DIR);
		this.dataStorageCallback = dataStorage;
		builders = new ArrayList<TypeBuildingFileVisitor.TypeBuilder>() {{
			add(new EtsBuilder(dataStorageCallback,logger));
			add(new TranslationTemplateBundleBuilder(dataStorageCallback,logger));
		}};
	}

	private List<File> getSubdirs(File file) {
		List<File> subdirs = new ArrayList<>(
				Arrays.asList(file.listFiles(f -> f.isDirectory())));

		List<File> deepSubdirs = new ArrayList<>();
		for(File subdir : subdirs) {
			deepSubdirs.addAll(getSubdirs(subdir));
		}
		subdirs.addAll(deepSubdirs);
		return subdirs;
	}

	@Override
	public void init()
			throws ConfigurationException, InitializationException, InvalidStateTransitionException {
		if (initialized == true) {
			throw new InvalidStateTransitionException("Already initialized");
		}
		this.configProperties.expectAllRequiredPropertiesSet();
		// this.etsDir = configProperties.getPropertyAsFile(EtfConstants.ETF_PROJECTS_DIR).expandPath("bsx");
		this.etsDir = configProperties.getPropertyAsFile(EtfConstants.ETF_PROJECTS_DIR);
		this.etsDir.mkdirs();

		// First propagate static types
		final WriteDao<TestObjectTypeDto> testObjectTypeDao = ((WriteDao<TestObjectTypeDto>)
				dataStorageCallback.getDao(TestObjectTypeDto.class));
		final WriteDao<TestItemTypeDto> testItemTypeDao = ((WriteDao<TestItemTypeDto>)
				dataStorageCallback.getDao(TestItemTypeDto.class));
		try {
			testObjectTypeDao.deleteAllExisting(testObjectTypes.keySet());
			testObjectTypeDao.addAll(testObjectTypes.values());

			testItemTypeDao.deleteAllExisting(testItemTypes.keySet());
			testItemTypeDao.addAll(testItemTypes.values());
		} catch (final StorageException e) {
			try{
				testObjectTypeDao.deleteAllExisting(testObjectTypes.keySet());
			}catch(StorageException e2) {
				ExcUtils.suppress(e2);
			}
			try{
				testItemTypeDao.deleteAllExisting(testItemTypes.keySet());
			}catch(StorageException e3) {
				ExcUtils.suppress(e3);
			}
			throw new InitializationException(e);
		}

		// Parse all directories
		final List<File> subDirs = getSubdirs(etsDir);
		final Set<Path> subPaths = subDirs.stream().
				map(File::toPath).collect(Collectors.toSet());
		filesChanged(null,subPaths);

		/*
				// Parse all directories
		final List<File> subDirs = getSubdirs(etsDir);
		final Set<File> filesToProcess = new LinkedHashSet<>();
		for (final File subDir : subDirs) {
			final File[] files = subDir.listFiles(path ->
					path.toString().endsWith(BsxConstants.TRANSLATION_TEMPLATE_BUNDLE_SUFFIX)
							|| path.toString().endsWith(BsxConstants.ETS_DEF_FILE_SUFFIX));
			if (files != null || files.length > 0) {
				filesToProcess.addAll(Arrays.asList(files));
			}
		}
		final Set<File> addedFiles = new LinkedHashSet<>();
		for (final File filesToProces : filesToProcess) {

		}
		 */


		// Watch the ets directory
		watcher = RecursiveDirWatcher.create(this.etsDir.toPath(), this);
		try {
			watcher.start();
		} catch (IOException e) {
			logger.error("Failed to start watch service: " + e.getMessage());
			throw new InitializationException(e);
		}

		this.initialized = true;
	}

	@Override
	public boolean isInitialized() {
		return true;
	}

	@Override
	public void release() {
		this.etsCache.clear();
		this.propagatedFiles.clear();
		this.watcher.release();
	}

	@Override
	public ConfigPropertyHolder getConfigurationProperties() {
		return configProperties;
	}

	Collection<ExecutableTestSuiteDto> getExecutableTestSuites() {
		return etsCache.values();
	}

	ExecutableTestSuiteDto getExecutableTestSuiteById(final EID id) {
		return etsCache.get(id);
	}

	TestObjectTypeDto getTestObjectTypeById(final EID id) {
		return testObjectTypes.get(id);
	}

	Collection<TestObjectTypeDto> getTestObjectTypes() {
		return testObjectTypes.values();
	}
}
