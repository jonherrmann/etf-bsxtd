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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import de.interactive_instruments.SUtils;
import de.interactive_instruments.UriUtils;
import de.interactive_instruments.XmlUtils;
import de.interactive_instruments.etf.dal.dao.Dao;
import de.interactive_instruments.etf.dal.dao.StreamWriteDao;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.testdriver.TypeBuildingFileVisitor;
import de.interactive_instruments.exceptions.StorageException;

/**
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class BsxEtsBuilder implements TypeBuildingFileVisitor.TypeBuilder<ExecutableTestSuiteDto> {

	private final StreamWriteDao<ExecutableTestSuiteDto> writeDao;
	private final static Logger logger = LoggerFactory.getLogger(BsxEtsBuilder.class);

	BsxEtsBuilder(final Dao<ExecutableTestSuiteDto> writeDao) {
		this.writeDao = (StreamWriteDao<ExecutableTestSuiteDto>) writeDao;
	}

	private static class BsxEtsBuilderCmd extends TypeBuildingFileVisitor.TypeBuilderCmd<ExecutableTestSuiteDto> {

		private final StreamWriteDao<ExecutableTestSuiteDto> writeDao;

		BsxEtsBuilderCmd(final Path path, final StreamWriteDao<ExecutableTestSuiteDto> writeDao) throws IOException, XPathExpressionException {
			super(path);
			this.writeDao = writeDao;

			// Parse ID
			final XPath xpath = de.interactive_instruments.etf.XmlUtils.newXPath();
			// final XmlUtils.XmlHandle xmlHandle = XmlUtils.newXmlHandle(xpath, path.toFile());

			final String oid = XmlUtils.newXmlHandle(xpath, path.toFile()).evaluateValue("/etf:ExecutableTestSuite[1]/@id");
			if (SUtils.isNullOrEmpty(oid)) {
				throw new IOException("ETS ID not found in " + path);
			}
			if (oid.length() != 39) {
				throw new IOException("ID " + oid + " is invalid");
			}
			this.id = oid.substring(3);

			// Parse ETS dependencies
			final String[] etsStrIds = XmlUtils.newXmlHandle(xpath, path.toFile()).evaluateValues(
					"/etf:ExecutableTestSuite[1]/etf:dependencies[1]/etf:executableTestSuite/@ref");
			if (etsStrIds != null) {
				for (final String strId : etsStrIds) {
					if (strId.length() != 39) {
						throw new IOException("ID " + strId + " is invalid");
					}
					dependsOn(strId.substring(3));
				}
			}

			// Parse Translation Template Bundle
			final String ttbId = XmlUtils.newXmlHandle(xpath, path.toFile()).evaluateValue("/etf:ExecutableTestSuite[1]/etf:translationTemplateBundle[1]/@ref");
			if (!SUtils.isNullOrEmpty(ttbId)) {
				if (ttbId.length() != 39) {
					throw new IOException("ID " + ttbId + " is invalid");
				}
				dependsOn(ttbId.substring(3));
			}
		}

		@Override
		protected ExecutableTestSuiteDto build() {
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
			return null;
		}
	}

	@Override
	public TypeBuildingFileVisitor.TypeBuilderCmd<ExecutableTestSuiteDto> prepare(final Path path) {
		if (path.toString().endsWith(BsxConstants.ETS_DEF_FILE_SUFFIX)) {
			try {
				return new BsxEtsBuilderCmd(path, writeDao);
			} catch (IOException | XPathExpressionException e) {
				logger.error("Could not prepare ETS {} ", path, e);
			}
		}
		return null;
	}
}
