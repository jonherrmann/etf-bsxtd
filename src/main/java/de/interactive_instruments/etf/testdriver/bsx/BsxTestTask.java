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
import java.util.Date;

import de.interactive_instruments.etf.dal.dto.Dto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.testengine.TaskState;
import de.interactive_instruments.etf.testengine.TaskStateEventListener;
import de.interactive_instruments.etf.testengine.TestTask;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.InvalidStateTransitionException;
import de.interactive_instruments.exceptions.config.ConfigurationException;

/**
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class BsxTestTask implements TestTask {

	public BsxTestTask(final TestTaskDto testTaskDto) {

	}

	@Override
	public File getLogFile() {
		return null;
	}

	@Override
	public String[] getLogMessager(final int firstMessagePos) {
		return new String[0];
	}

	@Override
	public int getMaxSteps() {
		return 0;
	}

	@Override
	public int getCurrentStepsCompleted() {
		return 0;
	}

	@Override
	public double getPercentStepsCompleted() {
		return 0;
	}

	@Override
	public Date getStartTimestamp() {
		return null;
	}

	@Override
	public void cancel() throws InvalidStateTransitionException {

	}

	@Override
	public void init() throws ConfigurationException, InitializationException, InvalidStateTransitionException {

	}

	@Override
	public boolean isInitialized() {
		return false;
	}

	@Override
	public void release() {

	}

	@Override
	public STATE getState() {
		return null;
	}

	@Override
	public void addStateEventListener(final TaskStateEventListener listener) {

	}

	@Override
	public Dto call() throws Exception {
		return null;
	}
}
