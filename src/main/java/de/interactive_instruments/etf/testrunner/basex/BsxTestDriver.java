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

import org.basex.BaseX;

import de.interactive_instruments.etf.component.ComponentInfo;
import de.interactive_instruments.etf.component.ComponentInitializer;
import de.interactive_instruments.etf.driver.TestDriver;
import de.interactive_instruments.etf.driver.TestRunTaskFactory;

/**
 * BaseX test driver component
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */

@ComponentInitializer(id = "BSX")
public class BsxTestDriver implements TestDriver {

	private final TestRunTaskFactory factory = new BasexTestRunTaskFactory();
	private final ComponentInfo info = new ComponentInfo() {

		@Override
		public String getName() {
			return "BaseX test driver";
		}

		@Override
		public String getId() {
			return "BSX";
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
	final public ComponentInfo getTestDriverInfo() {
		return info;
	}

	@Override
	final public TestRunTaskFactory getTestRunTaskFactory() {
		return factory;
	}

	@Override
	final public File getTestDriverLog() {
		return null;
	}

	@Override
	final public void release() {
		factory.release();
	}
}
