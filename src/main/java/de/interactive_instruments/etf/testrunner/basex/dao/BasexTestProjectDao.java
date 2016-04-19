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
package de.interactive_instruments.etf.testrunner.basex.dao;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.interactive_instruments.IFile;
import de.interactive_instruments.UriUtils;
import de.interactive_instruments.Version;
import de.interactive_instruments.concurrent.InvalidStateTransitionException;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.dal.ProjectDtoFileBuilder;
import de.interactive_instruments.etf.dal.ProjectFileBuildVisitor;
import de.interactive_instruments.etf.dal.dao.AbstractTestProjectDao;
import de.interactive_instruments.etf.dal.dto.item.VersionDataDto;
import de.interactive_instruments.etf.dal.dto.plan.TestProjectDto;
import de.interactive_instruments.etf.dal.dto.plan.TestProjectDtoBuilder;
import de.interactive_instruments.etf.driver.TestProjectUnavailable;
import de.interactive_instruments.etf.model.item.EID;
import de.interactive_instruments.etf.model.item.EidDefaultMap;
import de.interactive_instruments.etf.model.item.EidFactory;
import de.interactive_instruments.etf.model.plan.AbstractTestObjectResourceType;
import de.interactive_instruments.etf.model.plan.TestObjectResourceType;
import de.interactive_instruments.etf.testrunner.basex.BsxConstants;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.ObjectWithIdNotFoundException;
import de.interactive_instruments.exceptions.StoreException;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.io.FileChangeListener;
import de.interactive_instruments.io.RecursiveDirWatcher;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.Properties;

/**
 * File based data access object for Basex test projects.
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class BasexTestProjectDao extends AbstractTestProjectDao
		implements FileChangeListener, ProjectDtoFileBuilder {

	public final static String PROJECT_SUFFIX = "-basex.xq";

	private IFile projectDir;

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
		cache.values().removeIf(p -> !UriUtils.exists(p.getUri()));
	}

	@Override
	public TestProjectDto createProjectDto(File projectFile) {
		try {
			logger.info("Registering project: {}", projectFile.getAbsolutePath());
			return buildProjectDto(new IFile(projectFile), this.resourceTypes);
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
	 * Xml document resource type
	 */
	static class XmlDocObjectResourceType extends AbstractTestObjectResourceType {

		XmlDocObjectResourceType() {
			super("data", "Path to XML data", "file",
					Pattern.compile(".*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
					Pattern.compile(".*.(xml).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
		}
	}

	/**
	 * Default constructor.
	 */
	public BasexTestProjectDao() {
		this.assembler = new ProjectAssembler();
		this.configProperties = new ConfigProperties(EtfConstants.ETF_PROJECTS_DIR);
		final XmlDocObjectResourceType xmlRes = new XmlDocObjectResourceType();
		this.resourceTypes.put(xmlRes.getId(), xmlRes);
	}

	/**
	 * Create Dto from file
	 *
	 * @param projFile      project file
	 * @param resourceTypes the supported resource types
	 *
	 * @return BaseX test project dto
	 *
	 * @throws StoreException error reading project file
	 */
	private TestProjectDto buildProjectDto(IFile projFile,
			final Map<EID, TestObjectResourceType> resourceTypes) throws StoreException {
		final String id = EidFactory.getDefault().createFromStrAsUUID(projFile.toURI().toString()).toString();
		final String name = projFile.getName().substring(0, projFile.getName().indexOf(PROJECT_SUFFIX));

		byte[] hash;
		try {
			hash = UriUtils.hashFromTimestampOrContent(projFile.toURI()).getBytes(StandardCharsets.UTF_8);
		} catch (IOException e) {
			hash = new byte[0];
			ExcUtils.supress(e);
		}

		// Extract properties from project file
		final Properties properties = new Properties();
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
				properties.setProperty(matcher.group(1), matcher.group(2));
			}
		} catch (IOException e) {
			throw new TestProjectUnavailable(name);
		}

		// Check for a property file
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

		final TestProjectDtoBuilder builder = new TestProjectDtoBuilder().setVersion(
				new VersionDataDto(new Date(projFile.lastModified()), new Date(projFile.lastModified()),
						new Version(), hash))
				.setLabel(name).setId(EidFactory.getDefault().createFromStrAsUUID(id)).setTestDriverId("BSX").setUri(projFile.toURI()).setProperties(properties).setSupportedResourceTypes(new EidDefaultMap<TestObjectResourceType>() {
					{
						final TestObjectResourceType res = resourceTypes.get(EidFactory.getDefault().createFromStrAsStr("data"));
						if (res == null) {
							throw new StoreException("TestObjectResourceType with ID \"data\" not found");
						}
						put(res.getId(), res);
					}
				});
		if (properties.hasProperty(EtfConstants.ETF_DESCRIPTION_PK)) {
			builder.setDescription(properties.getProperty(EtfConstants.ETF_DESCRIPTION_PK));
			properties.removeProperty(EtfConstants.ETF_DESCRIPTION_PK);
		}
		return builder.createTestProjectDto();
	}

	@Override
	public void delete(EID id) throws StoreException, ObjectWithIdNotFoundException {
		throw new StoreException("Unimplemented");
	}

	@Override
	public void update(TestProjectDto dto)
			throws StoreException, ObjectWithIdNotFoundException {
		throw new StoreException("Unimplemented");
	}

	@Override
	public void doInit()
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
	public List<TestProjectDto> getAll() throws StoreException {
		return new ArrayList<>(cache.values());
	}

	@Override
	public TestProjectDto getDtoByName(String name) throws StoreException {
		for (final TestProjectDto dto : cache.values()) {
			if (dto.getLabel().equals(name)) {
				return dto;
			}
		}
		return null;
	}

	@Override
	public TestProjectDto cacheMissGetDtoById(EID id) throws StoreException {
		return null;
	}

	@Override
	public void doRelease() {
		this.cache.clear();
		this.watcher.release();
	}
}
