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

import static de.interactive_instruments.etf.EtfConstants.ETF_TESTDRIVERS_STORAGE_DIR;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Pattern;

import org.basex.core.Context;
import org.basex.core.StaticOptions;
import org.basex.query.QueryProcessor;
import org.basex.query.util.pkg.ModuleLoader;
import org.basex.util.JarLoader;
import org.basex.util.Reflect;

import de.interactive_instruments.IFile;
import de.interactive_instruments.etf.dal.dao.PreparedDto;
import de.interactive_instruments.etf.dal.dao.WriteDaoListener;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.config.MissingPropertyException;
import de.interactive_instruments.properties.ConfigProperties;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class BsxContextFactory implements WriteDaoListener {

    private final IFile storageDir;

    BsxContextFactory(ConfigProperties configProperties) throws MissingPropertyException {
        storageDir = configProperties.getPropertyAsFile(ETF_TESTDRIVERS_STORAGE_DIR).expandPath("bsx2");
        storageDir.secureExpandPathDown("data").mkdirs();
    }

    public Context create() {
        final Context ctx = new Context();
        ctx.soptions.put(StaticOptions.DBPATH, storageDir.secureExpandPathDown("data").getAbsolutePath());
        ctx.soptions.put(StaticOptions.REPOPATH, storageDir.secureExpandPathDown("repo").getAbsolutePath());
        return ctx;
    }

    public static void unloadModulesAndClose(final QueryProcessor processor) {
        if (processor != null) {
            if (!processor.stopped()) {
                processor.stop();
            }
            try {
                final ModuleLoader moduleLoader = processor.qc.resources.modules();
                final Class<? extends ModuleLoader> moduleLoaderClass = ModuleLoader.class;
                final Field loaderProperty = moduleLoaderClass.getDeclaredField("loader");
                loaderProperty.setAccessible(true);
                final ClassLoader loader = (ClassLoader) loaderProperty.get(moduleLoader);
                // Close ressources
                processor.close();
                if (loader instanceof JarLoader) {
                    final Set<JarLoader> closedLoaders = new HashSet<>();
                    // Close loaders recursively
                    closeLoader((JarLoader) loader, closedLoaders);
                }
                loaderProperty.set(moduleLoader, null);
            } catch (NoSuchFieldException | IllegalAccessException ign) {
                ExcUtils.suppress(ign);
            }
        }
    }

    private static void closeLoader(final JarLoader loader, final Set<JarLoader> closedLoaders) {
        if (!closedLoaders.contains(loader)) {
            closeClassLoaderJ11(loader);
            closedLoaders.add(loader);
        }
        if (loader.getParent() instanceof JarLoader) {
            closeLoader((JarLoader) loader.getParent(), closedLoaders);
        }
    }

    private static void closeClassLoaderJ11(final JarLoader jarLoader) {
        try {
            // invoke closeLoaders() on field ucp
            final Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
            ucpField.setAccessible(true);
            final Object ucp = ucpField.get(jarLoader);
            final Method closeLoadersMethod = Reflect.method(
                    ucp.getClass(), "closeLoaders");
            closeLoadersMethod.invoke(ucp);

            // invoke close() on each set entry of field closeables
            final Field closeablesField = URLClassLoader.class.getDeclaredField("closeables");
            closeablesField.setAccessible(true);
            final WeakHashMap<Closeable, Void> closeables = (WeakHashMap<Closeable, Void>) closeablesField.get(jarLoader);
            for (final Closeable c : closeables.keySet()) {
                try {
                    c.close();
                } catch (IOException ign) {
                    ExcUtils.suppress(ign);
                }
            }
            closeables.clear();
        } catch (final Exception ign2) {
            ExcUtils.suppress(ign2);
        }
    }

    @Override
    public void writeOperationPerformed(EventType eventType, PreparedDto preparedDto) {
        if (eventType == EventType.DELETE) {
            // Delete storageDirs for this DTO
            final List<IFile> dirs = storageDir.secureExpandPathDown("data").listDirs(Pattern.compile(".*" +
                    preparedDto.getDtoId().getId() + ".*"));
            try {
                for (IFile dir : dirs) {
                    dir.deleteDirectory();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
