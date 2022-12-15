
## To compile and package project run:

<code>
mvn compile

mvn package
</code>

## To run app:

<code>
java -jar target/Github-0.0.1-SNAPSHOT.jar
</code>

## To run as docker container use:

<code>
sudo docker build -t interview.project.github .

sudo docker run -p 8080:8080 interview.project.github
</code>

## To url to api endpoint:
<code>
[HOST]:[PORT]/github_proxy/v1/repositories/[USER]
</code>

## Swagger is exposed at url:
<code>
[HOST]:[PORT]/swagger-ui/index.html
</code>

## By defualt github api allows very limitted requests number. To increase this limit use github personal access token. To configure go to <code> application.properites </code> and set 

<code>
use_personal_access_key=true

github.personal.access.key=[your token]
</code>

More info on https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token


## Application follows belows criteria:

Acceptance criteria:
1. As an api consumer, given username and header “Accept: application/json”, I would like to list all his github repositories, which are not forks. Information, which I require in the response, is:
Repository Name
Owner Login
For each branch it’s name and last commit sha

2. As an api consumer, given not existing github user, I would like to receive 404 response in such a format:
{
“status”: ${responseCode}
“Message”: ${whyHasItHappened}
}

3. As an api consumer, given header “Accept: application/xml”, I would like to receive 406 response in such a format:
{
“status”: ${responseCode}
“Message”: ${whyHasItHappened}
}

Notes:
- Please full-fill the given acceptance criteria, delivering us your best code compliant with industry standards.
- Please use https://developer.github.com/v3 as a backing API
- Please expose swagger
- Application should have a proper README.md file
- Add unit and integration test cases.
- Prepare Dockerfile so that app can be run in a container
- Make sure that data is downloaded in parallel, not in sequence
- U can either use reactive stack of Your choice, or stick to non-reactive/blocking approach

Technologies to use (required):
- Java
- Maven
- Spring Boot
