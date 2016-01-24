/**
 * Copyright 2016 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.testrunner.basex;

import de.interactive_instruments.concurrent.AbstractTaskProgress;
import de.interactive_instruments.etf.model.result.TestReport;
import org.basex.core.InfoListener;
import org.basex.core.Proc;
import org.basex.query.QueryProcessor;

/**
 * Holds the progress of the test run task.
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class BasexTaskProgress extends AbstractTaskProgress<TestReport> implements InfoListener {

  private Proc proc;

  /**
   * Default constructor.
   */
  public BasexTaskProgress() {
    remainingSteps = 100;
    stepsCompleted = 1;
  }

  /**
   * Registers a observer for the QueryProcessor.
   *
   * @param proc BaseX QueryProcessor
   */
  void setQueryProc(QueryProcessor proc) {
    this.proc = proc;
    proc.listen(this);
  }

  @Override public void info(String info) {
    this.logger.info(info);
  }

  @Override public int getCurrentStepsCompleted() {
    if (this.getState() == STATE.COMPLETED) {
      return this.remainingSteps;
    } else if (proc != null && proc.progress() != 0) {
      return (int) (proc.progress() * 100);
    }
    return stepsCompleted;
  }

  /**
   * Indicate progress with the testing task.
   */
  public void advance() {
    stepsCompleted += 10;
  }
}
