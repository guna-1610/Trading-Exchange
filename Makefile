.PHONY: test run package docker clean

test:      ## run the full test suite (incl. the concurrency invariant test)
	mvn -B test

run:       ## start the exchange on http://localhost:8080
	mvn -B spring-boot:run

package:   ## build the executable fat jar
	mvn -B clean package

docker:    ## build and run in a container
	docker compose up --build

clean:
	mvn -B clean
