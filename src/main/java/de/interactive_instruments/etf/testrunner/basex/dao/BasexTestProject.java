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
package de.interactive_instruments.etf.testrunner.basex.dao;

import de.interactive_instruments.Version;
import de.interactive_instruments.etf.model.item.*;
import de.interactive_instruments.etf.model.plan.AbstractTestProject;
import de.interactive_instruments.etf.model.plan.TestObjectResourceType;
import de.interactive_instruments.etf.model.plan.TestProject;
import de.interactive_instruments.properties.Properties;
import de.interactive_instruments.properties.PropertyHolder;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Holds the test project
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class BasexTestProject extends AbstractTestProject {

  /**
   * Default constructor.
   *
   * @param id                 Id
   * @param label              Label
   * @param uri                File URI
   * @param description        Project description
   * @param versionData        Version data
   * @param supportedResources supported test object resource types
   * @param properties         Project properties
   */
  public BasexTestProject(EID id, String label, URI uri, String description,
      UpdateableVersionData versionData, EidMap<TestObjectResourceType> supportedResources,
      Properties properties) {
    super(uri, supportedResources);
    this.id = id;
    this.versionData = versionData;
    this.label = label;
    this.description = description;
    this.properties = properties;
  }
}
