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

import de.interactive_instruments.io.DirSizeVisitor;
import de.interactive_instruments.io.PathFilter;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.cmd.Add;
import org.basex.core.cmd.Close;
import org.basex.core.cmd.CreateDB;
import org.basex.core.cmd.Flush;
import org.basex.core.cmd.Open;
import org.basex.core.cmd.OptimizeAll;
import org.basex.core.cmd.Set;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The database partitioner is used to collect files for the BaseX import and
 * to import all data in smaller chunks -due to the maximum size restrictions
 * of the BaseX databases.
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class BasexDbPartitioner {

  private final Path dbDir;
  private final long maxDbSizeSizePerChunk;
  private final Context ctx = new Context();
  private final String dbBaseName;
  private final PathFilter filter;
  private final Logger logger;
  private static final int MAX_DIR_DEPTH = 5;


  private int currentDbIndex = 0;
  private long fileCount = 0L;
  private long size;
  private List<String> skippedFiles = new ArrayList<>();


  /**
   * Flushes database buffers and optimizes the database.
   *
   * @param ctx BaseX context
   *
   * @throws BaseXException flush or optimization failed
   */
  private static void flushAndOptimize(final Context ctx) throws BaseXException {
    new Flush().execute(ctx);
    new OptimizeAll().execute(ctx);
    new Close().execute(ctx);
  }

  /**
   * File visitor for directly importing test data.
   */
  class BasexFileVisitorPartitioner implements FileVisitor<Path> {

    private final long maxDbSize;
    private long currentDbSize = 0;
    private String currentDbName = dbBaseName + "-0";

    /**
     * Default Constructor.
     * <p>
     * Will deactivate autoflush to get best performance results
     * </p>
     *
     * @param maxDbSize max db chunk size
     *
     * @throws BaseXException first database creation failed
     */
    BasexFileVisitorPartitioner(long maxDbSize) throws BaseXException {
      currentDbIndex = 0;
      fileCount = 0L;
      this.maxDbSize = maxDbSize;
      new Set("AUTOFLUSH", "false").execute(ctx);
      new Set("TEXTINDEX", "true").execute(ctx);
      new Set("ATTRINDEX", "true").execute(ctx);
      new Set("FTINDEX", "true").execute(ctx);
      new Set("MAXLEN", "160").execute(ctx);

      new CreateDB(currentDbName).execute(ctx);
    }

    @Override public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
        throws IOException {

      if (Thread.currentThread().isInterrupted()) {
        return FileVisitResult.TERMINATE;
      }

      if (!filter.accept(file)) {
        return FileVisitResult.CONTINUE;
      }


      if (currentDbSize >= maxDbSize) {
        // Flush and optimize the current database
        logger.info(
            "Added " + FileUtils.byteCountToDisplaySize(currentDbSize) + " to test database "
                + currentDbName);
        logger.info("Flushing the current test database due to the size limit of " + FileUtils
            .byteCountToDisplaySize(maxDbSizeSizePerChunk));
        logger.info("Optimizing database " + currentDbName);
        flushAndOptimize(ctx);

        logger.info("Creating next database");
        // Create a new one
        currentDbIndex++;
        currentDbName = dbBaseName + "-" + currentDbIndex;
        new CreateDB(currentDbName).execute(ctx);
        currentDbSize = 0;
      }
      try {
        new Add(file.getFileName().toString(), file.toString()).execute(ctx);
        currentDbSize += attrs.size();
        fileCount++;
        size += attrs.size();
      } catch (BaseXException bsxEx) {
        // Skip not well-formed files
        logger.info("Data import of file " + file.toString() + " failed : " + bsxEx.getMessage());
        skippedFiles.add(file.getFileName().toString());
      }
      return FileVisitResult.CONTINUE;
    }

    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      return FileVisitResult.CONTINUE;
    }

    @Override public FileVisitResult visitFileFailed(Path file, IOException exc)
        throws IOException {
      if (!filter.accept(file)) {
        return FileVisitResult.CONTINUE;
      }
      logger.info("Data import failed for path " + file.toString() + " : " + exc.getMessage());
      skippedFiles.add(file.getFileName().toString());
      return FileVisitResult.CONTINUE;
    }

    @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc)
        throws IOException {
      return FileVisitResult.CONTINUE;
    }
  }


  /**
   * File visitor which performs a dry run to gather information about the number
   * of files which will be imported and the number of databases that will be created.
   */
  class BasexFileVisitorDryRun implements FileVisitor<Path> {

    private PathFilter filter;
    private final long maxDbSize;
    private long currentDbSize = 0;

    public BasexFileVisitorDryRun(PathFilter filter, long maxDbSize) throws BaseXException {
      currentDbIndex = 0;
      fileCount = 0L;
      this.filter = filter;
      this.maxDbSize = maxDbSize;
    }

    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        throws IOException {

      if (Thread.currentThread().isInterrupted()) {
        return FileVisitResult.TERMINATE;
      }

      if (!filter.accept(file)) {
        return FileVisitResult.CONTINUE;
      }

      if (currentDbSize >= maxDbSize) {
        currentDbIndex++;
        currentDbSize = 0;
      }
      currentDbSize += attrs.size();
      size += attrs.size();
      fileCount++;
      return FileVisitResult.CONTINUE;
    }

    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      return FileVisitResult.CONTINUE;
    }

    @Override public FileVisitResult visitFileFailed(Path file, IOException exc)
        throws IOException {
      return FileVisitResult.CONTINUE;
    }

    @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc)
        throws IOException {
      return FileVisitResult.CONTINUE;
    }
  }

  /**
   * Default Constructor.
   *
   * @param maxDbChunkSize configured chunk size
   * @param logger         logger
   * @param dbDir          database directory
   * @param dbName         database name
   * @param filter         file filter for excluding files
   */
  public BasexDbPartitioner(long maxDbChunkSize, final Logger logger, final Path dbDir,
      final String dbName, final PathFilter filter) {
    this.dbDir = dbDir;
    this.dbBaseName = dbName;
    this.filter = filter;
    this.logger = logger;
    this.maxDbSizeSizePerChunk = maxDbChunkSize;
  }

  /**
   * Calculate single db chunk size.
   *
   * @return long chunk size
   *
   * @throws IOException I/O error in visitor method
   */
  private long getMaxChunkSize() throws IOException {
    // Calculate db chunk size
    DirSizeVisitor dbSizeVisitor = new DirSizeVisitor(filter);
    Files.walkFileTree(dbDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DIR_DEPTH,
        dbSizeVisitor);
    final long chunkSize = dbSizeVisitor.getSize() / maxDbSizeSizePerChunk + (
        dbSizeVisitor.getSize() % maxDbSizeSizePerChunk == 0 ? 0 : 1);
    return dbSizeVisitor.getSize() / chunkSize + 1;
  }

  /**
   * Creates all databases.
   *
   * @throws IOException          I/O error in visitor method
   * @throws InterruptedException thread is interrupted
   */
  public void createDatabases() throws IOException, InterruptedException {
    final long maxChunkSize = getMaxChunkSize();
    // Create databases
    FileVisitor<Path> basexPartitioner = new BasexFileVisitorPartitioner(maxChunkSize);
    Files.walkFileTree(dbDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DIR_DEPTH,
        basexPartitioner);
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException();
    }
    logger.info("Added " + fileCount + " files (" + FileUtils.byteCountToDisplaySize(size) + ") to "
        + getDbCount() + " database(s) ");
    logger.info("Optimizing database " + dbBaseName + "-0");
    flushAndOptimize(ctx);
    new Open(dbBaseName + "-0").execute(ctx);
    new Close().execute(ctx);
    ctx.close();
    logger.info("Import completed");

  }

  /**
   * Perform a dry run to gather information about the number of files which will be imported
   * and the number of databases that will be created.
   * <p>
   * Important: call reset() after a dry run
   * </p>
   *
   * @throws IOException I/O error in visitor method
   */
  public void dryRun() throws IOException {
    // Calculate db chunk size
    final long maxChunkSize = getMaxChunkSize();
    // Create databases
    FileVisitor<Path> basexPartitioner = new BasexFileVisitorDryRun(filter, maxChunkSize);
    Files.walkFileTree(dbDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DIR_DEPTH,
        basexPartitioner);
  }

  /**
   * Number of databases.
   *
   * @return number of databases
   */
  public int getDbCount() {
    return currentDbIndex + 1;
  }

  /**
   * Total size of files.
   *
   * @return files size in bytes
   */
  public long getSize() {
    return size;
  }


  /**
   * Number of files.
   *
   * @return number of files
   */
  public long getFileCount() {
    return fileCount;
  }

  /**
   * List of skipped files (not excluded!).
   *
   * @return number of skipped files
   */
  public List<String> getSkippedFiles() {
    return skippedFiles;
  }

  /**
   * Reset the results (file count, etc.).
   */
  public void reset() {
    this.currentDbIndex = 0;
    this.fileCount = 0L;
    this.size = 0;
    this.skippedFiles.clear();
  }
}
