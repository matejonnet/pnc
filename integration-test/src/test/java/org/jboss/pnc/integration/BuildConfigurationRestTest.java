/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package org.jboss.pnc.integration;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.pnc.AbstractTest;
import org.jboss.pnc.integration.assertions.ResponseAssertion;
import org.jboss.pnc.integration.deployments.Deployments;
import org.jboss.pnc.integration.matchers.JsonMatcher;
import org.jboss.pnc.integration.template.JsonTemplateBuilder;
import org.jboss.pnc.rest.endpoint.BuildConfigurationEndpoint;
import org.jboss.pnc.rest.provider.BuildConfigurationProvider;
import org.jboss.pnc.rest.restmodel.BuildConfigurationRest;
import org.jboss.pnc.rest.restmodel.BuildEnvironmentRest;
import org.jboss.pnc.rest.restmodel.ProjectRest;
import org.jboss.pnc.restclient.BuildConfigurationRestClient;
import org.jboss.pnc.restclient.EnvironmentRestClient;
import org.jboss.pnc.restclient.ProjectRestClient;
import org.jboss.pnc.restclient.util.RestResponse;
import org.jboss.pnc.test.category.ContainerTest;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jayway.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.integration.env.IntegrationTestEnv.getHttpPort;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
@Category(ContainerTest.class)
public class BuildConfigurationRestTest extends AbstractTest {

    public static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String CLONE_PREFIX_DATE_FORMAT = "yyyyMMddHHmmss";

    private static final String CONFIGURATION_DEPENDENCIES_REST_ENDPOINT = CONFIGURATION_REST_ENDPOINT +"%d/dependencies";
    private static final String CONFIGURATION_CLONE_REST_ENDPOINT = CONFIGURATION_REST_ENDPOINT +"%d/clone";
    
    private static final String PME_PARAMS_LONG = "dependencyManagement=org.jboss.eap:jboss-eap-parent:${EAPBOM:version},"
            + "dependencyRelocations.org.wildfly:@org.jboss.eap:=${EAPBOM:version},"
            + "dependencyExclusion.org.freemarker:freemarker@*=${freemarker-2.3.23:version},"
            + "dependencyExclusion.org.liquibase:liquibase-core@*=${liquibase-3.4.1:version},"
            + "dependencyExclusion.org.twitter4j:twitter4j-core@*=${twitter4j-4.0.4:version},"
            + "dependencyExclusion.com.google.zxing:core@*=${zxing-3.2.1:version},"
            + "dependencyExclusion.org.infinispan:infinispan-core@*=8.1.4.Final-redhat-1,"
            + "dependencyExclusion.io.undertow:undertow-core@*=1.3.24.Final-redhat-1,"
            + "dependencyExclusion.org.wildfly.core:wildfly-version@*=${WFCORE:version},"
            + "dependencyExclusion.org.jboss.as:jboss-as-server@*=7.5.11.Final-redhat-1,"
            + "dependencyExclusion.org.hibernate:hibernate-entitymanager@*=5.0.9.Final-redhat-1,"
            + "dependencyExclusion.org.jboss.logging:jboss-logging-annotations@*=2.0.1.Final-redhat-1,"
            + "dependencyExclusion.org.jboss.resteasy:resteasy-jaxrs@*=3.0.18.Final-redhat-1,"
            + "dependencyExclusion.org.osgi:org.osgi.core@*=5.0.0,"
            + "dependencyExclusion.org.jboss.spec.javax.servlet:jboss-servlet-api_3.0_spec@*=1.0.2.Final-redhat-2,"
            + "dependencyExclusion.org.drools:drools-bom@*=6.4.0.Final-redhat-10,"
            + "dependencyExclusion.org.jboss.integration-platform:jboss-integration-platform-bom@*=6.0.6.Final-redhat-3";


    private static final String VALID_EXTERNAL_REPO = "https://github.com/project-ncl/pnc.git";
    private static final String VALID_INTERNAL_REPO = "git+ssh://git-repo-user@git-repo.devvm.devcloud.example.com:12839/boo.git";

    private static int productId;
    private static int projectId;
    private static int configurationId;
    private static int environmentId;
    
    private static int createdConfigurationId;

    private static AtomicBoolean isInitialized = new AtomicBoolean();


    private static ProjectRestClient projectRestClient;
    private static EnvironmentRestClient environmentRestClient;
    private static BuildConfigurationRestClient buildConfigurationRestClient;

    @Deployment(testable = false)
    public static EnterpriseArchive deploy() {
        EnterpriseArchive enterpriseArchive = Deployments.baseEar();

        WebArchive restWar = enterpriseArchive.getAsType(WebArchive.class, REST_WAR_PATH);
        restWar.addClass(BuildConfigurationProvider.class);
        restWar.addClass(BuildConfigurationEndpoint.class);
        restWar.addClass(BuildConfigurationRest.class);

        logger.info(enterpriseArchive.toString(true));
        return enterpriseArchive;
    }

    @SuppressWarnings("unchecked")
    @Before
    public void prepareData() throws Exception {
        if (!isInitialized.getAndSet(true)) {
            given().headers(testHeaders)
                    .contentType(ContentType.JSON).port(getHttpPort()).when().get(CONFIGURATION_REST_ENDPOINT).then()
                    .statusCode(200).body(JsonMatcher.containsJsonAttribute(FIRST_CONTENT_ID,
                            value -> configurationId = Integer.valueOf(value)));

            given().headers(testHeaders)
                    .contentType(ContentType.JSON).port(getHttpPort()).when().get(PRODUCT_REST_ENDPOINT).then().statusCode(200)
                    .body(JsonMatcher.containsJsonAttribute(FIRST_CONTENT_ID, value -> productId = Integer.valueOf(value)));

            given().headers(testHeaders)
                    .contentType(ContentType.JSON).port(getHttpPort()).when()
                    .get(String.format(EnvironmentRestClient.ENVIRONMENT_REST_ENDPOINT, productId)).then().statusCode(200)
                    .body(JsonMatcher.containsJsonAttribute(FIRST_CONTENT_ID, value -> environmentId = Integer.valueOf(value)));

            given().headers(testHeaders)
                    .contentType(ContentType.JSON).port(getHttpPort()).when().get(PROJECT_REST_ENDPOINT).then().statusCode(200)
                    .body(JsonMatcher.containsJsonAttribute(FIRST_CONTENT_ID, value -> projectId = Integer.valueOf(value)));
        }

        if (projectRestClient == null) {
            projectRestClient = new ProjectRestClient();
        }
        if (environmentRestClient == null) {
            environmentRestClient = new EnvironmentRestClient();
        }
        if (buildConfigurationRestClient == null) {
            buildConfigurationRestClient = new BuildConfigurationRestClient();
        }
    }

    @Test
    @InSequence(1)
    public void shouldCreateNewBuildConfiguration() throws IOException {
        createBuildConfigurationAndValidateResults(String.valueOf(projectId), String.valueOf(environmentId), UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
    }

    public void shouldCreateBuildConfigurationWithLongGenericParameter() throws Exception {
        createBuildConfigurationAndValidateResults(String.valueOf(projectId), String.valueOf(environmentId), UUID.randomUUID().toString(),
                PME_PARAMS_LONG);
    }

    private void createBuildConfigurationAndValidateResults(String projectId, String environmentId, String name, String genericParameterValue1) throws IOException {
        JsonTemplateBuilder configurationTemplate = JsonTemplateBuilder.fromResource("buildConfiguration_create_template");
        configurationTemplate.addValue("_projectId", projectId);
        configurationTemplate.addValue("_environmentId", environmentId);
        configurationTemplate.addValue("_name", name);
        configurationTemplate.addValue("_genParamValue1", genericParameterValue1);

        Response response = given().headers(testHeaders)
                .body(configurationTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .post(CONFIGURATION_REST_ENDPOINT);
        assertEquals(201, response.getStatusCode());
        createdConfigurationId = response.jsonPath().<Integer>get(CONTENT_ID);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void shouldGetSpecificBuildConfiguration() {
        given().headers(testHeaders)
                .contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(CONFIGURATION_SPECIFIC_REST_ENDPOINT, configurationId)).then().statusCode(200)
                .body(JsonMatcher.containsJsonAttribute(CONTENT_ID));
    }


    @Test
    public void shouldFailToCreateBuildConfigurationWhichDoesntMatchRegexp() throws IOException {
        JsonTemplateBuilder configurationTemplate = JsonTemplateBuilder.fromResource("buildConfiguration_create_template");
        configurationTemplate.addValue("_projectId", String.valueOf(projectId));
        configurationTemplate.addValue("_environmentId", String.valueOf(environmentId));
        configurationTemplate.addValue("_name", ":");

        given().headers(testHeaders)
                .body(configurationTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .post(CONFIGURATION_REST_ENDPOINT).then().statusCode(400);
    }

    @Test
    public void shouldNotCreateWithInternalUrlNotMatchingPattern() throws IOException {
        JsonTemplateBuilder configurationTemplate = JsonTemplateBuilder
                .fromResource("buildConfiguration_WithEmptyCreateDate_template");
        configurationTemplate.addValue("_projectId", String.valueOf(projectId));
        configurationTemplate.addValue("_environmentId", String.valueOf(environmentId));
        configurationTemplate.addValue("_name", UUID.randomUUID().toString());
        configurationTemplate.addValue("_scmRepoUrl", VALID_EXTERNAL_REPO);

        Response response = given().headers(testHeaders)
                .body(configurationTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .post(CONFIGURATION_REST_ENDPOINT);

        ResponseAssertion.assertThat(response).hasStatus(400);
    }

    @Test
    public void shouldCreateNewBuildConfigurationWithCreateAndModifiedTime() throws IOException {
        JsonTemplateBuilder configurationTemplate = JsonTemplateBuilder
                .fromResource("buildConfiguration_WithEmptyCreateDate_template");
        configurationTemplate.addValue("_projectId", String.valueOf(projectId));
        configurationTemplate.addValue("_environmentId", String.valueOf(environmentId));
        configurationTemplate.addValue("_name", UUID.randomUUID().toString());
        configurationTemplate.addValue("_scmRepoUrl", VALID_INTERNAL_REPO);

        Response response = given().headers(testHeaders)
                .body(configurationTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .post(CONFIGURATION_REST_ENDPOINT);

        ResponseAssertion.assertThat(response).hasStatus(201);
        ResponseAssertion.assertThat(response).hasJsonValueNotNullOrEmpty("content.creationTime")
                .hasJsonValueNotNullOrEmpty("content.lastModificationTime");
    }

    /**
     * Reproducer NCL-2615 - big generic parameters cannot be ubdated in the BuildConfiguration
     * 
     * @throws Exception
     */
    @Test
    public void shouldUpdateBuildConfiguration() throws IOException {
        // given
        final String updatedBuildScript = "mvn clean deploy -Dmaven.test.skip=true";
        final String updatedName = UUID.randomUUID().toString();
        final String updatedProjectId = String.valueOf(projectId);
        final String updatedGenParamValue = PME_PARAMS_LONG;

        JsonTemplateBuilder configurationTemplate = JsonTemplateBuilder.fromResource("buildConfiguration_update_template");
        configurationTemplate.addValue("_name", updatedName);
        configurationTemplate.addValue("_buildScript", updatedBuildScript);
        configurationTemplate.addValue("_scmRepoURL", VALID_INTERNAL_REPO);
        configurationTemplate.addValue("_creationTime", String.valueOf(1518382545038L));
        configurationTemplate.addValue("_lastModificationTime", String.valueOf(155382545038L));
        configurationTemplate.addValue("_projectId", updatedProjectId);
        configurationTemplate.addValue("_environmentId", String.valueOf(environmentId));
        configurationTemplate.addValue("_genParamValue1", updatedGenParamValue);

        Response projectResponseBeforeTheUpdate = given().headers(testHeaders).contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(PROJECT_SPECIFIC_REST_ENDPOINT, projectId));
        Response environmentResponseBeforeTheUpdate = given().headers(testHeaders).contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(SPECIFIC_ENVIRONMENT_REST_ENDPOINT, environmentId));

        // when
        given().headers(testHeaders)
                .body(configurationTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .put(String.format(CONFIGURATION_SPECIFIC_REST_ENDPOINT, configurationId)).then().statusCode(200);

        Response response = given().headers(testHeaders)
                .contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(CONFIGURATION_SPECIFIC_REST_ENDPOINT, configurationId));

        // then
        Response projectResponseAfterTheUpdate = given().headers(testHeaders).contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(PROJECT_SPECIFIC_REST_ENDPOINT, projectId));
        Response environmentResponseAfterTheUpdate = given().headers(testHeaders).contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(SPECIFIC_ENVIRONMENT_REST_ENDPOINT, environmentId));

        ResponseAssertion.assertThat(response).hasStatus(200);
        ResponseAssertion.assertThat(response).hasJsonValueEqual(CONTENT_ID, configurationId)
                .hasJsonValueEqual(CONTENT_NAME, updatedName).hasJsonValueEqual("content.buildScript", updatedBuildScript)
                .hasJsonValueEqual("content.scmRepoURL", VALID_INTERNAL_REPO).hasJsonValueEqual("content.project.id", updatedProjectId)
                .hasJsonValueEqual("content.genericParameters.KEY1", updatedGenParamValue)
                .hasJsonValueEqual("content.environment.id", environmentId);
        assertThat(projectResponseBeforeTheUpdate.getBody().print()).isEqualTo(projectResponseAfterTheUpdate.getBody().print());
        assertThat(environmentResponseBeforeTheUpdate.getBody().print())
                .isEqualTo(environmentResponseAfterTheUpdate.getBody().print());
    }

    @Test
    @InSequence(2)
    public void shouldCloneBuildConfiguration() {
        String buildConfigurationRestURI = String.format(CONFIGURATION_CLONE_REST_ENDPOINT, createdConfigurationId);
        Response response = given().headers(testHeaders)
                .body("").contentType(ContentType.JSON).port(getHttpPort()).when().post(buildConfigurationRestURI);

        String location = response.getHeader("Location");
        Integer clonedBuildConfigurationId = Integer.valueOf(location.substring(location.lastIndexOf("/") + 1));
        logger.info("Cloned id of buildConfiguration: " + clonedBuildConfigurationId);

        Response originalBuildConfiguration = given().headers(testHeaders).contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(CONFIGURATION_SPECIFIC_REST_ENDPOINT, createdConfigurationId));

        Response clonedBuildConfiguration = given().headers(testHeaders).contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(CONFIGURATION_SPECIFIC_REST_ENDPOINT, clonedBuildConfigurationId));

        ResponseAssertion.assertThat(response).hasStatus(201)
                .hasLocationMatches(".*\\/pnc-rest\\/rest\\/build-configurations\\/\\d+");

        assertThat(originalBuildConfiguration.body().jsonPath().getString("content.creationTime"))
                .isNotEqualTo(clonedBuildConfiguration.body().jsonPath().getString("content.creationTime"));
        assertThat(originalBuildConfiguration.body().jsonPath().getInt(CONTENT_ID))
                .isNotEqualTo("_" + clonedBuildConfiguration.body().jsonPath().getInt(CONTENT_ID));

        assertThat("_" + originalBuildConfiguration.body().jsonPath().getString(CONTENT_NAME))
                .isNotEqualTo(clonedBuildConfiguration.body().jsonPath().getString(CONTENT_NAME));

        String prefix = clonedBuildConfiguration.body().jsonPath().getString(CONTENT_NAME).substring(0,
                clonedBuildConfiguration.body().jsonPath().getString(CONTENT_NAME).indexOf("_"));

        Date clonedBcPrefixDate = null;
        try {
            clonedBcPrefixDate = new SimpleDateFormat(CLONE_PREFIX_DATE_FORMAT).parse(prefix);
        } catch (ParseException ex) {
            clonedBcPrefixDate = null;
        }

        assertThat(clonedBcPrefixDate).isNotNull();
        assertThat(originalBuildConfiguration.body().jsonPath().getString("content.buildScript"))
                .isEqualTo(clonedBuildConfiguration.body().jsonPath().getString("content.buildScript"));
        assertThat(originalBuildConfiguration.body().jsonPath().getString("content.scmRepoURL"))
                .isEqualTo(clonedBuildConfiguration.body().jsonPath().getString("content.scmRepoURL"));
        assertTrue(originalBuildConfiguration.body().jsonPath().getString("content.genericParameters.KEY1")
                .equals(clonedBuildConfiguration.body().jsonPath().getString("content.genericParameters.KEY1")));
    }

    @Test
    public void shouldFailToCreateNewBuildConfigurationBecauseIdIsNotNull() throws IOException {
        // given
        JsonTemplateBuilder configurationTemplate = JsonTemplateBuilder.fromResource("buildConfiguration_with_id_template");
        configurationTemplate.addValue("_id", String.valueOf(Integer.MAX_VALUE));
        configurationTemplate.addValue("_name", UUID.randomUUID().toString());
        configurationTemplate.addValue("_projectId", String.valueOf(projectId));
        configurationTemplate.addValue("_environmentId", String.valueOf(environmentId));
        configurationTemplate.addValue("_creationTime", String.valueOf(1518382545038L));
        configurationTemplate.addValue("_lastModificationTime", String.valueOf(155382545038L));

        given().headers(testHeaders)
                .body(configurationTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .post(CONFIGURATION_REST_ENDPOINT).then().statusCode(400);
    }

    @Test
    public void shouldGetConflictWhenCreatingNewBuildConfigurationWithTheSameNameAndProjectId() throws IOException {
        JsonTemplateBuilder configurationTemplate = JsonTemplateBuilder
                .fromResource("buildConfiguration_WithEmptyCreateDate_template");
        configurationTemplate.addValue("_projectId", String.valueOf(projectId));
        configurationTemplate.addValue("_environmentId", String.valueOf(environmentId));
        configurationTemplate.addValue("_name", UUID.randomUUID().toString());
        configurationTemplate.addValue("_scmRepoUrl", VALID_INTERNAL_REPO);

        Response firstAttempt = given().headers(testHeaders)
                .body(configurationTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .post(CONFIGURATION_REST_ENDPOINT);

        Response secondAttempt = given().headers(testHeaders)
                .body(configurationTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .post(CONFIGURATION_REST_ENDPOINT);

        ResponseAssertion.assertThat(firstAttempt).hasStatus(201);
        ResponseAssertion.assertThat(secondAttempt).hasStatus(409);
    }

    @Test
    public void shouldGetAuditedBuildConfigurations() throws Exception {
        Response response = given().headers(testHeaders)
                .contentType(ContentType.JSON).port(getHttpPort()).when()
                .get(String.format(CONFIGURATION_SPECIFIC_REST_ENDPOINT + "/revisions", configurationId));

        ResponseAssertion.assertThat(response).hasStatus(Status.OK.getStatusCode());
        ResponseAssertion.assertThat(response).hasJsonValueNotNullOrEmpty(FIRST_CONTENT_ID);
    }

    @Test
    public void shouldAddDependencyBuildConfiguration() throws Exception {
        // given
        RestResponse<ProjectRest> projectRestClient = BuildConfigurationRestTest.projectRestClient.firstNotNull();
        RestResponse<BuildEnvironmentRest> environmentRestClient = BuildConfigurationRestTest.environmentRestClient.firstNotNull();

        BuildConfigurationRest buildConfiguration = new BuildConfigurationRest();
        buildConfiguration.setName(UUID.randomUUID().toString());
        buildConfiguration.setProject(projectRestClient.getValue());
        buildConfiguration.setEnvironment(environmentRestClient.getValue());
        buildConfiguration.setScmRepoURL(VALID_INTERNAL_REPO);

        BuildConfigurationRest dependencyBuildConfiguration = new BuildConfigurationRest();
        dependencyBuildConfiguration.setName(UUID.randomUUID().toString());
        dependencyBuildConfiguration.setProject(projectRestClient.getValue());
        dependencyBuildConfiguration.setEnvironment(environmentRestClient.getValue());
        dependencyBuildConfiguration.setScmRepoURL(VALID_INTERNAL_REPO);

        // when
        RestResponse<BuildConfigurationRest> configurationResponse = buildConfigurationRestClient
                .createNew(buildConfiguration);
        RestResponse<BuildConfigurationRest> depConfigurationResponse = buildConfigurationRestClient
                .createNew(dependencyBuildConfiguration);

        Integer configId = configurationResponse.getValue().getId();
        Integer depConfigId = depConfigurationResponse.getValue().getId();

        String buildConfigDepRestURI = String.format(CONFIGURATION_DEPENDENCIES_REST_ENDPOINT, configId);
        
        Response addDepResponse = given().headers(testHeaders)
                .body("{ \"id\": " + depConfigId + " }").contentType(ContentType.JSON).port(getHttpPort()).when().post(buildConfigDepRestURI);

        RestResponse<BuildConfigurationRest> getUpdatedConfigResponse = buildConfigurationRestClient
                .get(configId);

        // then
        ResponseAssertion.assertThat(addDepResponse).hasStatus(200);
        assertThat(getUpdatedConfigResponse.getValue().getDependencyIds()).containsExactly(depConfigId);
    }
    

    @Ignore("Deleting a build configuration is no longer allowed")
    @Test
    @InSequence(999)
    public void shouldDeleteBuildConfiguration() throws Exception {
        // given
        RestResponse<BuildConfigurationRest> configuration = buildConfigurationRestClient.firstNotNull();

        // when
        buildConfigurationRestClient.delete(configuration.getValue().getId());
        RestResponse<BuildConfigurationRest> returnedConfiguration = buildConfigurationRestClient
                .get(configuration.getValue().getId(), false);

        // then
        assertThat(returnedConfiguration.hasValue()).isEqualTo(false);
    }
}
