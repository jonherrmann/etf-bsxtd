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
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.cmd.*;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.query.value.Value;
import org.xml.sax.SAXException;

import de.interactive_instruments.IFile;
import de.interactive_instruments.SUtils;
import de.interactive_instruments.etf.dal.dto.Dto;
import de.interactive_instruments.etf.dal.dto.run.TestRunDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.testdriver.bsx.xml.validation.MultiThreadedSchemaValidator;
import de.interactive_instruments.etf.testengine.AbstractTestTask;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.InvalidParameterException;
import de.interactive_instruments.exceptions.InvalidStateTransitionException;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.io.PathFilter;

// Version 8
// import org.basex.query.value.*;

/**
 * BaseX test run task for executing XQuery on a BaseX database.
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class BasexTestTask<T extends Dto> extends AbstractTestTask<T> {

	private final String dbName;
	private final Context ctx;
	private final IFile dsDir;
	private QueryProcessor proc;
	// The basex project file
	private final IFile projectFile;
	private final IFile projDir;
	private final long maxDbChunkSize;

	/**
	 * Default constructor.
	 *
	 * @param maxDbChunkSize maximum size of one database chunk
	 * @throws IOException I/O error
	 * @throws QueryException database error
	 */
	public BasexTestTask(TestTaskDto testTaskDto, long maxDbChunkSize)
			throws IOException, QueryException {
		super(new BsxTaskProgress(), testTaskDto);

		this.maxDbChunkSize = maxDbChunkSize;
		// tbd

		// TODO
		dsDir = new IFile(System.getProperty("etf.dsDir", "/Users/herrmann/Projects/etf-local/env1/ds/obj"));
		final IFile attachmentDir = new IFile(System.getProperty("etf.attachmentDir", "/Users/herrmann/Projects/etf-local/env1/ds/appendices/") + testTaskDto.getId());
		attachmentDir.mkdir();
		final IFile logFile = attachmentDir.secureExpandPathDown("testTask.log");

		this.dbName = BsxConstants.ETF_TESTDB_PREFIX + testTaskDto.getTestObject().getId();
		this.ctx = new Context();
		this.projectFile = new IFile(testTaskDto.getExecutableTestSuite().getLocalPath());
		this.projDir = new IFile(projectFile.getParentFile());

	}

	@Override
	protected T doStartTestEngine()
			throws IOException, QueryException, InvalidParameterException, InterruptedException, SAXException, InvalidStateTransitionException {
		Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

		this.projDir.expectDirIsReadable();
		this.projectFile.expectIsReadable();

		final IFile testDataDirDir = new IFile(
				testTaskDto.getTestObject().getResources().get(EidFactory.getDefault().createAndPreserveStr("data")).getUri(), this.dbName);
		testDataDirDir.expectDirIsReadable();

		getBsxTaskProgress().advance();
		checkUserParameters();

		final String regex = testTaskDto.getArguments().value("regex");

		final PathFilter filter;
		if (regex != null && !regex.isEmpty()) {
			filter = new BasexFileFilter(new RegexFileFilter(regex));
		} else {
			filter = new BasexFileFilter();
		}

		final BasexDbPartitioner partitioner = new BasexDbPartitioner(maxDbChunkSize, this.taskProgress.getLogger(),
				testDataDirDir.toPath(), this.dbName, filter);
		String skippedFiles = "";
		// if (testRun.isTestObjectResourceUpdateRequired()) {
		logInfo("The test object was just created or the resources of the test object "
				+ " changed: creating new tests databases!");

		for (int i = 0; i < 10000; i++) {
			boolean dropped = Boolean.valueOf(new DropDB(this.dbName + "-" + i).execute(ctx));
			if (dropped) {
				logInfo("Database " + i + " dropped");
			} else {
				break;
			}
		}
		skippedFiles = createDatabases(partitioner);
		/*
		} else {
			partitioner.dryRun();
			logInfo("Reusing existing database with " +
					partitioner.getFileCount() + " indexed files (" +
					FileUtils.byteCountToDisplaySize(partitioner.getSize()) + ")");
		}
		*/
		for (int i = 0; i < partitioner.getDbCount(); i++) {
			new Open(this.dbName + "-" + i).execute(ctx);
		}
		fireInitialized();

		checkCancelStatus();
		getBsxTaskProgress().advance();
		getBsxTaskProgress().advance();

		fireRunning();
		// Validate against schema if schema file is set
		String schemaFilePath = this.testTaskDto.getArguments().value("Schema_file");
		if (SUtils.isNullOrEmpty(schemaFilePath)) {
			// STD fallback: check for a schema.xsd named file
			final String stdSchemaFile = "schema.xsd";
			if (projDir.secureExpandPathDown(stdSchemaFile).exists()) {
				schemaFilePath = stdSchemaFile;
			} else {
				// project specific fallback
				// TODO: remove in future
				final String lodLevel = this.testTaskDto.getArguments().value("Level_of_Detail");
				if (!SUtils.isNullOrEmpty(lodLevel)) {
					schemaFilePath = "schema/citygml/CityGML_LOD" + lodLevel + ".xsd";
				}
			}
		}
		final MultiThreadedSchemaValidator mtsv;
		if (!SUtils.isNullOrEmpty(schemaFilePath)) {
			final IFile schemaFile = projDir.secureExpandPathDown(schemaFilePath);
			schemaFile.expectIsReadable();
			logInfo("Starting parallel schema validation");
			mtsv = new MultiThreadedSchemaValidator(testDataDirDir, filter, schemaFile);
			mtsv.validate();
			logInfo("Validation ended with " + mtsv.getErrorCount() + " error(s)");
			if (mtsv.getErrorCount() > 0) {
				logInfo(
						"Non-schema-compliant files can not be tested and are therefore excluded from further testing:");
				for (final File file : mtsv.getInvalidFiles()) {
					logInfo(" - " + file.getName());
					new Delete(file.getName()).execute(ctx);
				}
				new Flush().execute(ctx);
			}
		} else {
			mtsv = null;
			logInfo("Skipping validation due to no schema file was set.");
		}
		getBsxTaskProgress().advance();
		getBsxTaskProgress().advance();
		checkCancelStatus();

		// Load the test project as XQuery
		proc = new QueryProcessor(projectFile.readContent().toString(), ctx);
		getBsxTaskProgress().setQueryProc(proc);

		// Bind script variables
		// Workaround: Wrap File around URI for a clean path or basex will
		// throw an exception
		proc.bind("outputFile", new IFile(dsDir, "EID" + testTaskDto.getTestTaskResult().getId().toString() + ".xml"));
		proc.bind("projDir", projDir);
		proc.bind("dbBaseName", this.dbName);
		proc.bind("dbDir", testDataDirDir.getPath());
		proc.bind("dbCount", partitioner.getDbCount());
		proc.bind("testObjectId", testTaskDto.getTestObject().getId());
		proc.bind("testTaskResultId", testTaskDto.getTestTaskResult().getId());
		proc.bind("reportLabel", ((TestRunDto) testTaskDto.getParent()).getLabel());
		proc.bind("reportStartTimestamp", taskProgress.getStartDate().getTime());

		// Add errors about not well-formed or invalid XML data
		final String validationErrors;
		if (mtsv != null) {
			// Add Schema errors + not well-formed xml file errors (if applicable)
			validationErrors = skippedFiles + mtsv.getErrorMessages();
			mtsv.release();
		} else if (!SUtils.isNullOrEmpty(skippedFiles)) {
			// No schema file found, but not well-formed files
			validationErrors = skippedFiles;
		} else {
			// No errors
			validationErrors = "";
		}
		proc.bind("validationErrors", validationErrors);

		setUserParameters();

		logInfo("Compiling test script");
		proc.compile();
		getBsxTaskProgress().advance();
		checkCancelStatus();

		logInfo("Starting xquery tests");
		final Value result = proc.value();

		return (T) testTaskDto.getTestTaskResult();
	}

	@Override
	protected void doInit() throws ConfigurationException, InitializationException, InvalidStateTransitionException {

	}

	/**
	 * Create test databases.
	 * <p>
	 * Returns a concatenated error string about non well-formed files
	 * </p>
	 *
	 * @param partitioner BaseX database partitioner
	 * @return skipped non well formed files
	 * @throws IOException I/O error
	 * @throws InterruptedException interrupted by user
	 */
	private String createDatabases(final BasexDbPartitioner partitioner)
			throws IOException, InterruptedException {
		partitioner.dryRun();
		logInfo(partitioner.getFileCount() +
				" files (" + FileUtils.byteCountToDisplaySize(partitioner.getSize()) + ")"
				+ " will be added to " + partitioner.getDbCount() + " test database(s)");
		getBsxTaskProgress().advance();
		if (partitioner.getDbCount() > 1) {
			logInfo("This might take a while...");
		}
		partitioner.reset();
		logInfo("Creating a new test database " + partitioner.getFileCount());
		partitioner.createDatabases();
		logInfo("Added " + partitioner.getFileCount() + " files");
		if (!partitioner.getSkippedFiles().isEmpty()) {
			final StringBuilder skippedFiles = new StringBuilder();
			skippedFiles.append("Skipped " + partitioner.getSkippedFiles().size() +
					" not well formed file(s): " + SUtils.ENDL);
			for (String s : partitioner.getSkippedFiles()) {
				skippedFiles.append(s + SUtils.ENDL);
			}
			logInfo(skippedFiles.toString());
			return skippedFiles.toString();
		}
		// all files imported
		return "";
	}

	/**
	 * Returns the BsxTaskProgress.
	 *
	 * @return BsxTaskProgress
	 */
	private BsxTaskProgress getBsxTaskProgress() {
		return (BsxTaskProgress) this.taskProgress;
	}

	/**
	 * Check the user parameters by executing the Project check file
	 *
	 * @throws IOException I/O error reading check file
	 * @throws QueryException error executing check file
	 * @throws InvalidParameterException invalid user parameter detected
	 */
	private void checkUserParameters() throws IOException, QueryException, InvalidParameterException {
		// Check parameters by executing the xquery script
		final String checkParamXqFileName = projectFile.getName().replace(BsxConstants.PROJECT_SUFFIX, BsxConstants.PROJECT_CHECK_FILE_SUFFIX);
		final IFile checkParamXqFile = projDir.secureExpandPathDown(checkParamXqFileName);
		if (checkParamXqFile.exists()) {
			proc = new QueryProcessor(checkParamXqFile.readContent().toString(), ctx);
			try {
				setUserParameters();
				proc.compile();
				// Version 8
				proc.value();
			} catch (QueryException e) {
				logInfo("Invalid user parameters. Error message: " + e.getMessage());
				throw e;
			}
			logInfo("User parameters accepted");
			proc.close();
		}
	}

	/**
	 * Bind user parameters
	 *
	 * @throws QueryException database error
	 */
	private void setUserParameters() throws QueryException {
		// Bind additional user specified properties
		for (Map.Entry<String, String> property : this.testTaskDto.getArguments().values().entrySet()) {
			proc.bind(property.getKey(), property.getValue());
		}
	}

	@Override
	public void doRelease() {
		try {
			if (proc != null) {
				proc.close();
			}
			new Close().execute(ctx);
		} catch (BaseXException e) {
			ExcUtils.suppress(e);
		}
	}

	@Override
	protected void doHandleException(Exception e) {
		if (e.getMessage() != null) {
			logInfo("Testrun failed with an error: " + e.getMessage());

		} else {
			logInfo("Testrun failed");
		}
	}

	@Override
	protected void doCancel() throws InvalidStateTransitionException {
		proc.stop();
	}
}
