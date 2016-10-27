ManyWho Service Creator
=======================

> This tool is currently in development, and not yet recommended for use in production environments

[![Build Status](https://travis-ci.org/jonjomckay/service-creator.svg)](https://travis-ci.org/jonjomckay/service-creator)

This tools allows you to create a service from a template or an API specification. It currently supports the following:

* Swagger

## Running the Development Build

For now, this tool is only runnable by building, packaging and running from the repository, so you'll need a JDK 8 implementation and Maven. It's pretty easy though:

````bash
$ mvn clean package
$ java -jar target/service-creator.jar
````

## Examples

### Swagger

Currently, the tool has a rough outline of the process of generating a service based on a Swagger specification. You can try it out for yourself by running:

````bash
$ java -jar target/service-creator.jar swagger --group=com.jonjomckay.services --artifact=petstore --url=http://petstore.swagger.io/v2/swagger.json
````

This will bring up display a prompt for each operation in the specification, asking whether that operation should be a Message Action or a Database Action in the generated service (but nothing is actually generated for these yet - this is just a POC).

The generated artifact is currently created in a directory named `example` inside the current working directory.

## Contributing

Contributions are welcome to the project - whether they are feature requests, improvements or bug fixes! Refer to 
[CONTRIBUTING.md](CONTRIBUTING.md) for our contribution requirements.

## License

This service is released under the [MIT License](http://opensource.org/licenses/mit-license.php).