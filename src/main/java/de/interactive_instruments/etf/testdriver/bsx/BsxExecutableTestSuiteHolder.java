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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

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
import de.interactive_instruments.etf.testengine.ExecutableTestSuiteDtoFileBuilder;
import de.interactive_instruments.etf.testengine.ExecutableTestSuiteUnavailable;
import de.interactive_instruments.etf.testengine.ProjectFileBuildVisitor;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.io.FileChangeListener;
import de.interactive_instruments.io.RecursiveDirWatcher;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;
import de.interactive_instruments.properties.Properties;

/**
 * File based data access object for Basex test projects.
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class BsxExecutableTestSuiteHolder implements Configurable, Releasable, FileChangeListener, ExecutableTestSuiteDtoFileBuilder {

	private final EidMap<TestObjectTypeDto> testObjectTypes = new DefaultEidMap<TestObjectTypeDto>() {
		{
			final TestObjectTypeDto testObjectTypeDto = new TestObjectTypeDto();
			testObjectTypeDto.setLabel("Basic BsxType");
			testObjectTypeDto.setId(EidFactory.getDefault().createAndPreserveStr("e1d4a306-7a78-4a3b-ae2d-cf5f0810853e"));
			testObjectTypeDto.setDescription("Basic BsxType");
			put(testObjectTypeDto.getId(), testObjectTypeDto);
		}
	};

	public final static String PROJECT_SUFFIX = "-basex.xq";
	private final ConfigProperties configProperties;

	private IFile projectDir;

	protected final ConcurrentMap<EID, ExecutableTestSuiteDto> cache = new ConcurrentLinkedHashMap.Builder()
			.maximumWeightedCapacity(45)
			.build();

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Only String values are allowed!
	 * Example pattern: declare variable $varName external := "-5";
	 * Results are in regex group 1 (key) and 2 (value)
	 */
	private final Pattern PROPERTY_SEARCH_PATTERN = Pattern.compile(
			"declare\\s+variable\\s+\\$([\\w\\u00e4\\u00c4\\u00d6\\u00f6\\u00fc\\u00dc\\u00df-]+)\\s+"
					+ "external\\s:=\\s\"([^;$]+)\";");
	private boolean initialized = false;
	private RecursiveDirWatcher watcher;
	private ProjectFileBuildVisitor visitor;

	@Override
	public void filesChanged(final Map<Path, WatchEvent.Kind> eventMap, final Set<Path> dirs) {
		dirs.forEach(d -> {
			logger.info("Watch service reports changes in directory: " + d.toString());
			try {
				Files.walkFileTree(d, visitor);
			} catch (IOException e) {
				logger.error("Failed to walk file tree: " + e.getMessage());
			}
		});
		cache.values().removeIf(p -> !new File(p.getLocalPath()).exists());
	}

	@Override
	public ExecutableTestSuiteDto createExecutableTestSuiteDto(File projectFile) {
		try {
			logger.info("Registering project: {}", projectFile.getAbsolutePath());
			return buildProjectDto(new IFile(projectFile));
		} catch (final StoreException e) {
			logger.error("Failed to create test project ({}): {}", projectFile.getAbsolutePath(),
					e.getMessage());
			return null;
		}
	}

	@Override
	public boolean accept(final Path path) {
		return path.getFileName().toString().endsWith(PROJECT_SUFFIX);
	}

	/**
	 * Default constructor.
	 */
	public BsxExecutableTestSuiteHolder() {
		this.configProperties = new ConfigProperties(EtfConstants.ETF_PROJECTS_DIR);
	}

	/**
	 * Create Dto from file
	 *
	 * @param projFile      project file
	 *
	 * @return BaseX test project dto
	 *
	 * @throws StoreException error reading project file
	 */
	private ExecutableTestSuiteDto buildProjectDto(IFile projFile) throws StoreException {
		final String id = EidFactory.getDefault().createUUID(projFile.toURI().toString()).toString();
		final String name = projFile.getName().substring(0, projFile.getName().indexOf(PROJECT_SUFFIX));

		byte[] hash;
		try {
			hash = UriUtils.hashFromTimestampOrContent(projFile.toURI()).getBytes(StandardCharsets.UTF_8);
		} catch (IOException e) {
			hash = new byte[0];
			ExcUtils.suppress(e);
		}

		// Extract parameters from project file
		final ParameterSet parameters = new ParameterSet();
		try {
			projFile.expectFileIsReadable();
			final IFile propertyCheckFile = new IFile(projFile.getParentFile(),
					projFile.getName().replace(PROJECT_SUFFIX, BsxConstants.PROJECT_CHECK_FILE_SUFFIX));
			final Matcher matcher;
			if (propertyCheckFile.exists()) {
				matcher = PROPERTY_SEARCH_PATTERN.matcher(propertyCheckFile.readContent());
			} else {
				matcher = PROPERTY_SEARCH_PATTERN.matcher(projFile.readContent());
			}
			while (matcher.find()) {
				parameters.addParameter(matcher.group(1), matcher.group(2));
			}
		} catch (IOException e) {
			throw new ExecutableTestSuiteUnavailable(name);
		}

		// Check for a property file
		final Properties properties = new Properties();
		final IFile propertyFile = new IFile(projFile.getParentFile(), projFile.getName()
				.replace(PROJECT_SUFFIX, EtfConstants.ETF_TESTPROJECT_PROPERTY_FILE_SUFFIX));
		if (propertyFile.exists()) {
			try {
				properties.setPropertiesFrom(propertyFile, true);
			} catch (IOException e) {
				throw new StoreException(
						"Failed to read custom test project property file: " + e.getMessage());
			}
		}
		// todo properties

		final ExecutableTestSuiteDto etsDto = new ExecutableTestSuiteDto();
		etsDto.setVersionFromStr("1.0.0");
		etsDto.setLastUpdateDate(new Date(projFile.lastModified()));
		etsDto.setCreationDate(new Date(projFile.lastModified()));
		etsDto.setItemHash(hash);
		etsDto.setLabel(name);
		// todo? etsDto.setId(EidFactory.getDefault().createUUID(id));
		etsDto.setId(EidFactory.getDefault().createUUID(name));
		etsDto.setLocalPath(projFile.toURI().toString());
		etsDto.setParameterSet(parameters);
		// etsDto.setProperties(properties);
		etsDto.setSupportedTestObjectTypes(testObjectTypes.asList());

		if (properties.hasProperty(EtfConstants.ETF_DESCRIPTION_PK)) {
			etsDto.setDescription(properties.getProperty(EtfConstants.ETF_DESCRIPTION_PK));
			properties.removeProperty(EtfConstants.ETF_DESCRIPTION_PK);
		}
		etsDto.ensureValid();
		return etsDto;
	}

	@Override
	public void init()
			throws ConfigurationException, InitializationException, InvalidStateTransitionException {
		if (initialized == true) {
			throw new InvalidStateTransitionException("Already initialized");
		}
		this.configProperties.expectAllRequiredPropertiesSet();
		this.projectDir = configProperties.getPropertyAsFile(EtfConstants.ETF_PROJECTS_DIR).expandPath("bsx");

		visitor = new ProjectFileBuildVisitor(this, cache);

		try {
			Files.walkFileTree(this.projectDir.toPath(), visitor);
		} catch (IOException e) {
			logger.error("Failed to walk file tree: " + e.getMessage());
		}

		watcher = RecursiveDirWatcher.create(this.projectDir.toPath(), this);
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
		this.cache.clear();
		this.watcher.release();
	}

	@Override
	public ConfigPropertyHolder getConfigurationProperties() {
		return configProperties;
	}

	Collection<ExecutableTestSuiteDto> getExecutableTestSuites() {
		return cache.values();
	}

	ExecutableTestSuiteDto getExecutableTestSuiteById(final EID id) {
		return cache.get(id);
	}

	Collection<TestObjectTypeDto> getTestObjectTypes() {
		return testObjectTypes.values();
	}

	TestObjectTypeDto getTestObjectTypeById(final EID id) {
		return testObjectTypes.get(id);
	}
}
