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
package de.interactive_instruments.etf.testdriver;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentMap;

import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.model.EID;

/**
 *
 * @author herrmann@interactive-instruments.de.
 */
public class ProjectFileBuildVisitor implements FileVisitor<Path> {

	private final ExecutableTestSuiteDtoFileBuilder builder;
	private final ConcurrentMap<EID, ExecutableTestSuiteDto> cache;

	/**
	 * Creates a new FileVisitor which uses the passed ExecutableTestSuiteDtoFileBuilder to find project files
	 * and build TestProjectDto objects.
	 * @param cache
	 */
	public ProjectFileBuildVisitor(
			final ExecutableTestSuiteDtoFileBuilder builder,
			final ConcurrentMap<EID, ExecutableTestSuiteDto> cache) {
		this.builder = builder;
		this.cache = cache;
	}

	@Override
	public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(final Path file, BasicFileAttributes attrs) throws IOException {
		if (builder.accept(file)) {
			final ExecutableTestSuiteDto dto = builder.createExecutableTestSuiteDto(file.toFile());
			cache.put(dto.getId(), dto);
		}
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(final Path file, IOException exc) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(final Path dir, IOException exc) throws IOException {
		return FileVisitResult.CONTINUE;
	}
}
