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
package de.interactive_instruments.etf.testdriver.bsx.partitioning;

import static de.interactive_instruments.etf.testdriver.bsx.BsxConstants.CHOP_WHITESPACES;

import java.nio.file.Path;
import java.util.Collection;

import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.cmd.*;
import org.slf4j.Logger;

import de.interactive_instruments.etf.testdriver.bsx.BsxDatabaseCtx;
import de.interactive_instruments.properties.ConfigPropertyHolder;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
final class DatabaseChunk {
    private final Context ctx;
    final long size;
    private int parameterHash;

    static DatabaseChunk newChunk(final ConfigPropertyHolder config, final BsxDatabaseCtx databaseCtx) throws BaseXException {
        final Context ctx = databaseCtx.newCtx();

        new Set("AUTOFLUSH", "false").execute(ctx);
        new Set("TEXTINDEX", "true").execute(ctx);
        new Set("ATTRINDEX", "true").execute(ctx);
        new Set("FTINDEX", "true").execute(ctx);
        new Set("MAXLEN", "160").execute(ctx);

        new Set("DTD", "false").execute(ctx);
        new Set("XINCLUDE", "false").execute(ctx);
        new Set("INTPARSE", "true").execute(ctx);

        new Set("ENFORCEINDEX", "true").execute(ctx);
        // new Set("COPYNODE", "false").execute(ctx);

        // Deactivate inlining. Issue found in version 9.3.3 .
        // Might be related to optimizations of xquery facade functions that
        // return empty-sequence()
        new Set("INLINELIMIT", "0").execute(ctx);

        new Set("CHOP",
                config.getPropertyOrDefault(CHOP_WHITESPACES, "true")).execute(ctx);
        // already filtered
        new Set("SKIPCORRUPT", "false").execute(ctx);

        new CreateDB(databaseCtx.dbName).execute(ctx);
        return new DatabaseChunk(ctx, 0);
    }

    private DatabaseChunk(final Context ctx, final long dbSize) {
        this.ctx = ctx;
        this.size = dbSize;
        this.parameterHash = 0;
    }

    DatabaseChunk incSize(final long newSize) {
        return new DatabaseChunk(ctx, this.size + newSize);
    }

    void add(final String fileName, final Path path, Collection<Set> parameters) throws BaseXException {
        if (parameters.hashCode() != parameterHash) {
            for (final Set parameter : parameters) {
                parameter.execute(ctx);
            }
            this.parameterHash = parameters.hashCode();
        }
        new Add(fileName, path.toString()).execute(ctx);
    }

    synchronized void close() {
        this.ctx.close();
    }

    void flush() throws BaseXException {
        new Flush().execute(this.ctx);
    }

    void executeChunkFile(final ChunkXqFile chunkXqFile, final Logger logger, final String dbName) throws BaseXException {
        new Optimize().execute(this.ctx);
        logger.info("Optimized");
        chunkXqFile.execute(logger, this.ctx, dbName);
        logger.info("ChunkXqFile executed");
        new Flush().execute(this.ctx);
        new Optimize().execute(this.ctx);
        logger.info("Index rebuilt");
    }

    public void check(final String currentDbName) throws BaseXException {
        new Open(currentDbName).execute(this.ctx);
        new Close().execute(this.ctx);
    }
}
