/**
 * Copyright 2010-2017 interactive instruments GmbH
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
package de.interactive_instruments.etf.testdriver.bsx.partitioning;

import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.util.Set;

import de.interactive_instruments.Releasable;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface DatabaseVisitor extends Releasable, FileVisitor<Path> {

	Set<String> getSkippedFiles();

	/**
	 * Number of databases.
	 *
	 * @return number of databases
	 */
	int getDbCount();

	/**
	 * Total size of files.
	 *
	 * @return files size in bytes
	 */
	long getSize();

	/**
	 * Number of files.
	 *
	 * @return number of files
	 */
	long getFileCount();
}
