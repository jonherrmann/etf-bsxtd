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

import de.interactive_instruments.IFile;
import de.interactive_instruments.concurrent.InvalidStateTransitionException;
import de.interactive_instruments.etf.dal.dao.TestProjectDao;
import de.interactive_instruments.etf.dal.dto.plan.TestRunDto;
import de.interactive_instruments.etf.driver.TestRunTask;
import de.interactive_instruments.etf.driver.TestRunTaskFactory;
import de.interactive_instruments.etf.model.plan.TestProject;
import de.interactive_instruments.etf.model.plan.TestRun;
import de.interactive_instruments.etf.testrunner.basex.dao.BasexTestProjectDao;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.ObjectWithIdNotFoundException;
import de.interactive_instruments.exceptions.StoreException;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;
import org.basex.query.QueryException;

import java.io.IOException;

public class BasexTestRunTaskFactory implements TestRunTaskFactory {

  public static final long DEFAULT_MAX_CHUNK_SIZE = 33500000000L;
  private TestProjectDao testProjectStore;
  final private ConfigProperties configProperties = new ConfigProperties(BsxConstants.PROJECT_DIR_KEY);

  @Override
  final public void init()
      throws ConfigurationException, InvalidStateTransitionException, InitializationException {
    configProperties.expectAllRequiredPropertiesSet();
    if (configProperties.getProperty("org.basex.path") != null) {
      System.setProperty("org.basex.path", configProperties.getProperty("org.basex.path"));
    }
    testProjectStore = new BasexTestProjectDao();
    testProjectStore.getConfigurationProperties().setPropertiesFrom(configProperties, true);
    testProjectStore.init();
  }

  @Override
  final public boolean isInitialized() {
    return testProjectStore != null;
  }

  @Override
  final public TestRunTask createTestRunTask(TestRunDto testRunDto)
      throws ConfigurationException {
    try {
      final TestProject project =
          getTestProjectStore().getById(testRunDto.getTestProject().getId());
      testRunDto.getTestReport().setLabel(testRunDto.getLabel());
      final BasexTestReport report =
          new BasexTestReport(testRunDto.getTestReport(), testRunDto.getUsernameOfInitiator());
      final TestRun testRun = new BasexTestRun(testRunDto, project, report);
      return new BasexTestRunTask(testRun, new IFile(testRunDto.getTestProject().getUri()),
          configProperties
              .getPropertyOrDefaultAsLong(BsxConstants.DB_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE));
    } catch (StoreException | ObjectWithIdNotFoundException | QueryException | IOException e) {
      ExcUtils.supress(e);
      throw new ConfigurationException(e.getMessage());
    }
  }

  @Override
  final public TestProjectDao getTestProjectStore() throws ConfigurationException {
    if (testProjectStore == null) {
      throw new ConfigurationException("ProjectStore not initialized");
    }
    return this.testProjectStore;
  }

  @Override
  final public String getTestDriverId() {
    return "BSX";
  }

  @Override
  final public void release() {
  }

  @Override
  final public ConfigPropertyHolder getConfigurationProperties() {
    return this.configProperties;
  }
}

