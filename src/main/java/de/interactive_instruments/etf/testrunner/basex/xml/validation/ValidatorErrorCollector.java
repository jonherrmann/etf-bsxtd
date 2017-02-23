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
package de.interactive_instruments.etf.testrunner.basex.xml.validation;

import java.io.File;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.interactive_instruments.Releasable;

/**
 * A synchronized collector for the error messages of the MultiThreadedSchemaValidator
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class ValidatorErrorCollector implements Releasable {

	private final StringBuilder sb = new StringBuilder();
	private final long maxErrors;
	private final AtomicLong testedFiles = new AtomicLong(0);
	private final AtomicLong errorCounter = new AtomicLong(0);
	private final ConcurrentMap<File, Integer> errorsPerFile = new ConcurrentSkipListMap<>();

	/**
	 * Saves an error message
	 *
	 * @param str message
	 */
	void collectError(final String str, final File file, final int errorsInFile) {
		if (errorCounter.getAndAdd(errorsInFile) < maxErrors) {
				sb.append(str);
		}
		errorsPerFile.put(file, errorsInFile);
	}

	/**
	 * Returns all errors as concatenated string.
	 *
	 * @return concatenated error messages
	 */
	public String getErrorMessages() {
		if (!errorsPerFile.isEmpty()) {
			final Iterator<Map.Entry<File, Integer>> iterator = errorsPerFile.entrySet().iterator();
			// list at least 25 invalid files
			long maxErrorsPerFileCounter = maxErrors + 25;
			long errorsPerFileCounter = errorCounter.get()<maxErrors ?
					errorCounter.get() : maxErrors;
			for (; iterator.hasNext(); errorsPerFileCounter++) {
				final Map.Entry<File, Integer> e = iterator.next();
				if (errorsPerFileCounter < maxErrorsPerFileCounter) {
					sb.append(e.getValue());
					sb.append(" errors in file ");
					sb.append(e.getKey().getName());
					if(iterator.hasNext()) {
						sb.append(System.lineSeparator());
					}
				} else {
					sb.append("The listing of additional files with errors is skipped.");
					sb.append(System.lineSeparator());
					break;
				}
			}
		}
		final long c = errorCounter.get();
		if(c <= maxErrors) {
			return sb.toString();
		}else {
			final StringBuilder out = new StringBuilder(sb.length()+128);
			out.append(sb);
			out.append(System.lineSeparator());
			out.append(c - maxErrors);
			out.append(" additional error messages were skipped. ");
			final long f = testedFiles.get();
			if(f==errorsPerFile.size()) {
				out.append("None of the ");
				out.append(f);
				out.append(" files is schema-valid.");
			}else {
				out.append(+errorsPerFile.size());
				out.append(" files of ");
				out.append(f);
				out.append(" (");
				final NumberFormat percentFormat = NumberFormat.getPercentInstance();
				percentFormat.setMinimumFractionDigits(1);
				percentFormat.setMaximumFractionDigits(2);
				out.append(percentFormat.format( ((double) errorsPerFile.size()) / ((double) f)));
				out.append(") are not schema-valid.");
			}
			return out.toString();
		}
	}

	public Set<File> getInvalidFiles() {
		return errorsPerFile.keySet();
	}

	/**
	 * Returns the number of errors.
	 *
	 * @return
	 */
	public int getErrorCount() {
		return errorsPerFile.size();
	}

	/**
	 * Default constructor.
	 *
	 * @param maxErrors maximum number of errors that will be saved
	 */
	public ValidatorErrorCollector(long maxErrors) {
		this.maxErrors = maxErrors<8 ? 8 : maxErrors;
	}

	/**
	 * Inner error handler SAX errors.
	 */
	class ValidatorErrorHandler implements ErrorHandler, Releasable {

		private final File file;
		private int errorsInFile;
		// avoid errorCounter sync between threads
		private long approxMax = maxErrors-errorCounter.get()-1;
		private final StringBuilder lSb = new StringBuilder();

		ValidatorErrorHandler(final File file) {
			this.file = file;
		}

		private void logError(final String severity, final SAXParseException e) {
			++errorsInFile;
			if(errorsInFile<approxMax) {
				final String message = e.getMessage();
				lSb.append(severity).append(" in file ").append(file.getName()).
						append("( line ").append(e.getLineNumber()).append(", column ").
						append(e.getColumnNumber()).append(") : ");
				if (message.length() > 130) {
					lSb.append(System.lineSeparator());
				}
				lSb.append(message).append(System.lineSeparator());
			}
		}

		@Override
		public void warning(SAXParseException e) throws SAXException {

		}

		@Override
		public void error(SAXParseException e) throws SAXException {
			logError("Error", e);
		}

		@Override
		public void fatalError(SAXParseException e) throws SAXException {
			logError("Fatal error", e);
		}

		@Override
		public void release() {
			if (errorsInFile > 0) {
				collectError(lSb.toString(), file, errorsInFile);
			}
			testedFiles.incrementAndGet();
		}
	}

	@Override
	public void release() {
		errorCounter.set(0);
		testedFiles.set(0);
		errorsPerFile.clear();
	}

	/**
	 * Creates a new error handler
	 *
	 * @param file file which is checked
	 *
	 * @return a SAX error handler
	 */
	public ValidatorErrorHandler newErrorHandler(final File file) {
		return new ValidatorErrorHandler(file);
	}
}
