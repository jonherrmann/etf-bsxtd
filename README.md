# Test driver for testing large XML files with BaseX XQuery based test projects

[![Apache License 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Latest version](http://img.shields.io/badge/latest%20version-1.0.5-blue.svg)](http://services.interactive-instruments.de/etfdev-af/release/de/interactive_instruments/etf/testdriver/etf-bsxtd/1.0.5/etf-bsxtd-1.0.5.zip)

The test driver is loaded by the ETF framework at runtime. The test driver
loads the test data into the integrated [BaseX database](http://basex.org/) and
executes XQuery data test scripts bundled as ETF project files.

Please use the [etf-webapp project](https://github.com/interactive-instruments/etf-webapp) for
reporting [issues](https://github.com/interactive-instruments/etf-webapp/issues) or
[further documentation](https://github.com/interactive-instruments/etf-webapp/wiki).

The project can be build and installed by running the gradlew.sh/.bat wrapper with:
```gradle
$ gradlew build install
```

ETF component version numbers comply with the [Semantic Versioning Specification 2.0.0](http://semver.org/spec/v2.0.0.html).

ETF is an open source test framework developed by [interactive instruments](http://www.interactive-instruments.de/en) for testing geo network services and data.

## Installation
Copy the JAR path to the _$driver_ directory. The $driver directory is configured in your _etf-config.properties_ configuration path as variable _etf.testdrivers.dir_. If the driver is loaded correctly, it is displayed on the status page.

## Updating
Remove the old JAR path from the _$driver_ directory and exchange it with the new version.
