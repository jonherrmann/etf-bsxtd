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
package de.interactive_instruments.etf.testrunner.basex;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;

import de.interactive_instruments.io.PathFilter;

/**
 * A PathFilter and FileFilter implementation which accepts files with GML
 * and XML filename endings.
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class BasexFileFilter implements FileFilter, PathFilter {

	private final FileFilter ff;

	public BasexFileFilter(FileFilter ff) {
		this.ff = ff;
	}

	public BasexFileFilter() {
		ff = null;
	}

	@Override
	public boolean accept(File pathname) {
		final String p = pathname.getName().toUpperCase();
		return '.' != p.charAt(0) && (p.endsWith(".XML") || p.endsWith(".GML")) && (ff == null || ff
				.accept(pathname));
	}

	@Override
	public boolean accept(Path path) {
		return accept(path.toFile());
	}
}
