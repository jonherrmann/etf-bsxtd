/**
 * Copyright 2017-2020 European Union, interactive instruments GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * This work was supported by the EU Interoperability Solutions for
 * European Public Administrations Programme (http://ec.europa.eu/isa)
 * through Action 1.17: A Reusable INSPIRE Reference Platform (ARE3NA).
 */
package de.interactive_instruments.etf.testdriver.bsx;

import static de.interactive_instruments.etf.EtfConstants.ETF_DATA_STORAGE_NAME;
import static de.interactive_instruments.etf.testdriver.bsx.BsxTestDriver.BSX_TEST_DRIVER_EID;
import static de.interactive_instruments.etf.testdriver.bsx.Types.BSX_SUPPORTED_TEST_OBJECT_TYPES;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.basex.BaseX;
import org.basex.core.Context;
import org.basex.query.QueryException;
import org.basex.query.util.pkg.Pkg;
import org.basex.query.util.pkg.RepoManager;
import org.deegree.cs.CRSCodeType;
import org.deegree.cs.exceptions.CRSConfigurationException;
import org.deegree.cs.persistence.CRSManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.interactive_instruments.IFile;
import de.interactive_instruments.IoUtils;
import de.interactive_instruments.JarUtils;
import de.interactive_instruments.etf.component.ComponentInfo;
import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.DataStorageRegistry;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dto.IncompleteDtoException;
import de.interactive_instruments.etf.dal.dto.capabilities.ComponentDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.result.TestTaskResultDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.testdriver.AbstractTestDriver;
import de.interactive_instruments.etf.testdriver.ComponentInitializer;
import de.interactive_instruments.etf.testdriver.TestTask;
import de.interactive_instruments.etf.testdriver.TestTaskInitializationException;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;

/**
 * BaseX test driver component
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
@ComponentInitializer(id = BSX_TEST_DRIVER_EID)
public class BsxTestDriver extends AbstractTestDriver {

    public static final String BSX_TEST_DRIVER_EID = "c8f39ab3-b0e4-4e06-924a-f31cc99a4932";
    private static final String GMLGEOX_SRSCONFIG_DIR = "etf.testdrivers.bsx.gmlgeox.srsconfig.dir";
    private final static Logger logger = LoggerFactory.getLogger(BsxTestDriver.class);

    private DataStorage dataStorageCallback;
    private BsxContextFactory contextFactory;

    private final ComponentInfo info = new ComponentInfo() {
        @Override
        public String getName() {
            return "BaseX test driver";
        }

        @Override
        public EID getId() {
            return EidFactory.getDefault().createAndPreserveStr(BSX_TEST_DRIVER_EID);
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

    public BsxTestDriver() {
        super(new ConfigProperties(ETF_DATA_STORAGE_NAME, BsxConstants.PROJECT_DIR_KEY));
    }

    @Override
    public Collection<TestObjectTypeDto> getTestObjectTypes() {
        return BSX_SUPPORTED_TEST_OBJECT_TYPES.values();
    }

    @Override
    final public ComponentInfo getInfo() {
        return info;
    }

    @Override
    public TestTask createTestTask(final TestTaskDto testTaskDto) throws TestTaskInitializationException {
        try {
            Objects.requireNonNull(testTaskDto, "Test Task not set").ensureBasicValidity();

            // Get ETS
            testTaskDto.getTestObject().ensureBasicValidity();
            testTaskDto.getExecutableTestSuite().ensureBasicValidity();
            final TestTaskResultDto testTaskResult = new TestTaskResultDto();
            testTaskResult.setId(EidFactory.getDefault().createRandomId());
            testTaskDto.setTestTaskResult(testTaskResult);
            return new BasexTestTask(testTaskDto, ((WriteDao) dataStorageCallback.getDao(TestObjectDto.class)),
                    configProperties, contextFactory);
        } catch (IncompleteDtoException e) {
            throw new TestTaskInitializationException(e);
        }

    }

    @Override
    protected void doInit() throws ConfigurationException, InitializationException {
        dataStorageCallback = DataStorageRegistry.instance().get(configProperties.getProperty(ETF_DATA_STORAGE_NAME));
        if (dataStorageCallback == null) {
            throw new InitializationException("Data Storage not set");
        }

        contextFactory = new BsxContextFactory(configProperties);
        ((WriteDao) dataStorageCallback.getDao(TestObjectDto.class)).registerListener(contextFactory);

        installFunctx();
        configureDeegree();
        propagateComponents(installGmlGeoX(), installTopoX(), installExtensions());

        loader = new BsxFileLoaderFactory(dataStorageCallback);
        loader.getConfigurationProperties().setPropertiesFrom(configProperties, true);
    }

    private void installFunctx() throws InitializationException {
        final Context ctx = contextFactory.create();
        final RepoManager repoManger = new RepoManager(ctx);
        final String functxInstallationUrl = "https://files.basex.org/modules/expath/functx-1.0.xar";
        try {
            // Check for functx
            boolean functxFound = false;
            for (final Pkg pkg : repoManger.packages()) {
                if ("http://www.functx.com".equals(pkg.name())) {
                    functxFound = true;
                }
            }
            if (!functxFound) {
                // Install it
                logger.info("Installing FunctX XQuery Function Library from " + functxInstallationUrl);
                repoManger.install(functxInstallationUrl);
            }
        } catch (final QueryException e) {
            throw new InitializationException("FunctX XQuery Function Library installation failed. "
                    + "If a proxy server is used, set the Java Virtual Machine parameter 'http.proxyHost'. "
                    + "Otherwise download functx-1.0.xar manually from " + functxInstallationUrl + ", "
                    + "extract the file (it is a ZIP file) and copy "
                    + "it to the BaseXRepo 'repo' folder " + ctx.repo.path(), e);
        }
    }

    private void propagateComponents(ComponentInfo... info) throws InitializationException {
        // Propagate Component info from here
        final WriteDao<ComponentDto> componentDao = ((WriteDao<ComponentDto>) dataStorageCallback.getDao(ComponentDto.class));
        try {
            // Remove existing Test Driver
            try {
                componentDao.delete(this.getInfo().getId());
            } catch (final ObjectWithIdNotFoundException e) {
                ExcUtils.suppress(e);
            }
            for (final ComponentInfo componentInfo : info) {
                try {
                    componentDao.delete(componentInfo.getId());
                } catch (final ObjectWithIdNotFoundException e) {
                    ExcUtils.suppress(e);
                }
            }
            componentDao.add(new ComponentDto(this.getInfo()));
            for (final ComponentInfo componentInfo : info) {
                componentDao.add(new ComponentDto(componentInfo));
            }
        } catch (StorageException e) {
            throw new InitializationException(e);
        }
    }

    private void loadDeegreeSrsConfiguration(final IFile srsConfigDir) throws InitializationException, ConfigurationException {
        final CRSManager crsMgr = new CRSManager();
        try {
            srsConfigDir.expectDirIsReadable();
            crsMgr.init(srsConfigDir);
            if (CRSManager.get("default") == null || CRSManager.get("default").getCRSByCode(
                    CRSCodeType.valueOf("epsg:4326")) == null) {
                throw new InitializationException("Failed to load SRS configuration from " +
                        srsConfigDir.getAbsolutePath());
            }
        } catch (CRSConfigurationException e) {
            throw new InitializationException(
                    "Failed to load SRS configuration from '" + srsConfigDir.getAbsolutePath() + "' : " + e.getMessage());
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Could not load SRS configuration files from directory: " + e.getMessage());
        }
    }

    private void extractDeegreeSrsConfiguration(final IFile srsConfigDir) throws InitializationException {
        try {
            srsConfigDir.mkdirs();
            srsConfigDir.expectDirIsWritable();
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/default.xml", new IFile(srsConfigDir,
                            "default.xml"));
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/deegree/d3/config/ntv2/beta2007.gsb",
                    new IFile(srsConfigDir, "deegree/d3/config/ntv2/beta2007.gsb"));
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/deegree/d3/parser-files.xml",
                    new IFile(srsConfigDir, "deegree/d3/parser-files.xml"));
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/deegree/d3/config/crs-definitions.xml",
                    new IFile(srsConfigDir, "deegree/d3/config/crs-definitions.xml"));
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/deegree/d3/config/datum-definitions.xml",
                    new IFile(srsConfigDir, "deegree/d3/config/datum-definitions.xml"));
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/deegree/d3/config/ellipsoid-definitions.xml",
                    new IFile(srsConfigDir, "deegree/d3/config/ellipsoid-definitions.xml"));
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/deegree/d3/config/pm-definitions.xml",
                    new IFile(srsConfigDir, "deegree/d3/config/pm-definitions.xml"));
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/deegree/d3/config/projection-definitions.xml",
                    new IFile(srsConfigDir, "deegree/d3/config/projection-definitions.xml"));
            IoUtils.copyResourceToFile(this,
                    "/plugins/etf-gmlgeox.xar!/geox/gmlgeox.jar!/srsconfig/deegree/d3/config/transformation-definitions.xml",
                    new IFile(srsConfigDir, "deegree/d3/config/transformation-definitions.xml"));
        } catch (IOException e) {
            throw new InitializationException(
                    "Exception occurred while extracting the SRS configuration files provided by GmlGeoX to a temporary "
                            + "directory and loading them from there. Exception message is: "
                            + e.getMessage());
        }
    }

    private void configureDeegree() throws ConfigurationException, InitializationException {
        // Temporary switch the context classloader to a classloader that can access the deegree libs
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(CRSManager.class.getClassLoader());
        try {
            final IFile srsConfigDir;
            if (configProperties.getProperty(GMLGEOX_SRSCONFIG_DIR) != null) {
                System.setProperty(GMLGEOX_SRSCONFIG_DIR, configProperties.getProperty(GMLGEOX_SRSCONFIG_DIR));
                srsConfigDir = configProperties.getPropertyAsFile(GMLGEOX_SRSCONFIG_DIR);
                // Load user defined configuration and fail-fast
                loadDeegreeSrsConfiguration(srsConfigDir);
            } else {
                srsConfigDir = new IFile(System.getProperty("java.io.tmpdir"))
                        .secureExpandPathDown("srsconfig").secureExpandPathDown(IFile.sanitize(this.getInfo().getVersion()));
                System.setProperty(GMLGEOX_SRSCONFIG_DIR, srsConfigDir.getAbsolutePath());
                // Try to use the existing configuration files and fall back to the provided configuration files,
                // if that does not work.
                if (srsConfigDir.exists()) {
                    try {
                        loadDeegreeSrsConfiguration(srsConfigDir);
                    } catch (InitializationException | ConfigurationException e) {
                        logger.warn("The reuse of existing temporary configuration files has failed. "
                                + "New files are written. ", e);
                        FileUtils.deleteQuietly(srsConfigDir);
                        extractDeegreeSrsConfiguration(srsConfigDir);
                    }
                } else {
                    extractDeegreeSrsConfiguration(srsConfigDir);
                }
            }
            loadDeegreeSrsConfiguration(srsConfigDir);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    private ComponentInfo installGmlGeoX() throws InitializationException {
        final Context ctx = contextFactory.create();
        final RepoManager repoManger = new RepoManager(ctx);
        try {
            repoManger.delete("ETF GmlGeoX");
        } catch (QueryException e) {
            ExcUtils.suppress(e);
        }
        try {
            // Extract gmlGeoX to temporary directory and install it
            final IFile tmpGmlGeoXFile = IFile.createTempFile("gmlgeox", "etf.xar");
            IoUtils.copyResourceToFile(this, "/plugins/etf-gmlgeox.xar", tmpGmlGeoXFile);
            String v;
            try {
                v = JarUtils.getManifest(tmpGmlGeoXFile).getMainAttributes().getValue("Implementation-Version");
            } catch (IOException e) {
                v = "unknown";
            }
            final String version = v;

            final ComponentInfo gmlGeoXInfo = new ComponentInfo() {
                @Override
                public EID getId() {
                    return EidFactory.getDefault().createUUID("etf-GmlGeoX");
                }

                @Override
                public String getName() {
                    return "GmlGeoX";
                }

                @Override
                public String getVersion() {
                    return version;
                }

                @Override
                public String getVendor() {
                    return "interactive instruments GmbH";
                }

                @Override
                public String getDescription() {
                    return "BaseX test driver extension module "
                            + "to validate GML geometries within XML documents, "
                            + "perform geometry operations and index GML geometries";
                }
            };
            repoManger.install(tmpGmlGeoXFile.getAbsolutePath());
            tmpGmlGeoXFile.delete();

            return gmlGeoXInfo;
        } catch (IOException | QueryException e) {
            throw new InitializationException("GmlGeoX installation failed: ", e);
        }
    }

    // Todo refactor this, think about packaging
    private ComponentInfo installTopoX() throws InitializationException {
        final Context ctx = contextFactory.create();
        final RepoManager repoManger = new RepoManager(ctx);
        try {
            repoManger.delete("ETF TopoX");
        } catch (QueryException e) {
            ExcUtils.suppress(e);
        }
        try {
            // Extract gmlGeoX to temporary directory and install it
            final IFile tmpTopoxXFile = IFile.createTempFile("topox", "etf.xar");
            IoUtils.copyResourceToFile(this, "/plugins/etf-topox.xar", tmpTopoxXFile);
            String v;
            try {
                v = JarUtils.getManifest(tmpTopoxXFile).getMainAttributes().getValue("Implementation-Version");
            } catch (IOException e) {
                v = "0.0.0";
            }
            final String version = v;

            final ComponentInfo topoxInfo = new ComponentInfo() {
                @Override
                public EID getId() {
                    return EidFactory.getDefault().createUUID("etf-TopoX");
                }

                @Override
                public String getName() {
                    return "TopoX";
                }

                @Override
                public String getVersion() {
                    return version;
                }

                @Override
                public String getVendor() {
                    return "interactive instruments GmbH";
                }

                @Override
                public String getDescription() {
                    return "BaseX test driver extension module "
                            + "to validate the topology of geometric Features";
                }
            };
            repoManger.install(tmpTopoxXFile.getAbsolutePath());
            tmpTopoxXFile.delete();

            return topoxInfo;
        } catch (IOException | QueryException e) {
            throw new InitializationException("TopoX installation failed: ", e);
        }
    }

    // Todo refactor this, think about packaging
    private ComponentInfo installExtensions() throws InitializationException {
        final Context ctx = contextFactory.create();
        final RepoManager repoManger = new RepoManager(ctx);
        try {
            repoManger.delete("ETF BaseX bridge");
        } catch (QueryException e) {
            ExcUtils.suppress(e);
        }
        try {
            // Extract gmlGeoX to temporary directory and install it
            final IFile tmpTdBridgeFileFile = IFile.createTempFile("extensions", "etf.xar");
            IoUtils.copyResourceToFile(this, "/plugins/etf-extensions.xar", tmpTdBridgeFileFile);
            String v;
            try {
                v = JarUtils.getManifest(tmpTdBridgeFileFile).getMainAttributes().getValue("Implementation-Version");
            } catch (IOException e) {
                v = "0.0.0";
            }
            final String version = v;

            final ComponentInfo extensionInfo = new ComponentInfo() {
                @Override
                public EID getId() {
                    return EidFactory.getDefault().createUUID("etf-bsxtd-extensions");
                }

                @Override
                public String getName() {
                    return "BaseX extensions";
                }

                @Override
                public String getVersion() {
                    return version;
                }

                @Override
                public String getVendor() {
                    return "interactive instruments GmbH";
                }

                @Override
                public String getDescription() {
                    return "BaseX test driver extension module";
                }
            };
            repoManger.install(tmpTdBridgeFileFile.getAbsolutePath());
            tmpTdBridgeFileFile.delete();

            return extensionInfo;
        } catch (IOException | QueryException e) {
            throw new InitializationException("ETF bridge installation failed: ", e);
        }
    }

    @Override
    protected void doRelease() {

    }
}
