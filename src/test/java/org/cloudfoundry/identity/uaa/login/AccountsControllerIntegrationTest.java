package org.cloudfoundry.identity.uaa.login;

import com.dumbster.smtp.SimpleSmtpServer;
import org.cloudfoundry.identity.uaa.authentication.Origin;
import org.cloudfoundry.identity.uaa.authentication.UaaPrincipal;
import org.cloudfoundry.identity.uaa.login.test.UaaRestTemplateBeanFactoryPostProcessor;
import org.cloudfoundry.identity.uaa.test.YamlServletProfileInitializerContextInitializer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.support.XmlWebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

public class AccountsControllerIntegrationTest {

    XmlWebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private MockRestServiceServer mockUaaServer;
    private static SimpleSmtpServer mailServer;

    @BeforeClass
    public static void startMailServer() throws Exception {
        mailServer = SimpleSmtpServer.start(2525);
    }

    @Before
    public void setUp() throws Exception {
        webApplicationContext = new XmlWebApplicationContext();
        webApplicationContext.setEnvironment(new MockEnvironment());
        new YamlServletProfileInitializerContextInitializer().initializeContext(webApplicationContext, "login.yml");
        webApplicationContext.setConfigLocation("file:./src/main/webapp/WEB-INF/spring-servlet.xml");
        webApplicationContext.addBeanFactoryPostProcessor(new UaaRestTemplateBeanFactoryPostProcessor());
        webApplicationContext.refresh();
        FilterChainProxy springSecurityFilterChain = webApplicationContext.getBean("springSecurityFilterChain", FilterChainProxy.class);

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilter(springSecurityFilterChain)
            .build();

        mockUaaServer = MockRestServiceServer.createServer(webApplicationContext.getBean("authorizationTemplate", RestTemplate.class));
    }

    @AfterClass
    public static void stopMailServer() throws Exception {
        mailServer.stop();
    }

    @Test
    public void testCreateActivationEmailPage() throws Exception {
        ((MockEnvironment) webApplicationContext.getEnvironment()).setProperty("login.brand", "oss");

        mockMvc.perform(get("/accounts/new"))
                .andExpect(content().string(containsString("Create your account")))
                .andExpect(content().string(not(containsString("Pivotal ID"))));
    }

    @Test
    public void testCreateActivationEmailPageWithPivotalBrand() throws Exception {
        ((MockEnvironment) webApplicationContext.getEnvironment()).setProperty("login.brand", "pivotal");

        mockMvc.perform(get("/accounts/new"))
            .andExpect(content().string(containsString("Create your Pivotal ID")))
            .andExpect(content().string(not(containsString("Create your account"))));
    }

    @Test
    public void testActivationEmailSentPage() throws Exception {
        ((MockEnvironment) webApplicationContext.getEnvironment()).setProperty("login.brand", "oss");

        mockMvc.perform(get("/accounts/email_sent"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create your account")))
                .andExpect(xpath("//input[@disabled='disabled']/@value").string("Email successfully sent"))
                .andExpect(content().string(not(containsString("Pivotal ID"))));
    }

    @Test
    public void testActivationEmailSentPageWithPivotalBrand() throws Exception {
        ((MockEnvironment) webApplicationContext.getEnvironment()).setProperty("login.brand", "pivotal");

        mockMvc.perform(get("/accounts/email_sent"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create your Pivotal ID")))
                .andExpect(xpath("//input[@disabled='disabled']/@value").string("Email successfully sent"))
                .andExpect(content().string(not(containsString("Create your account"))));
    }

    @Test
    public void testCreateAccountPage() throws Exception {
        ((MockEnvironment) webApplicationContext.getEnvironment()).setProperty("login.brand", "oss");

        mockMvc.perform(get("/accounts/new").param("code", "the_secret_code").param("email", "user@example.com"))
            .andExpect(content().string(containsString("Create your account")))
            .andExpect(content().string(not(containsString("Pivotal ID"))));
    }

    @Test
    public void testCreateAccountPageWithPivotalBrand() throws Exception {
        ((MockEnvironment) webApplicationContext.getEnvironment()).setProperty("login.brand", "pivotal");

        mockMvc.perform(get("/accounts/new").param("code", "the_secret_code").param("email", "user@example.com"))
            .andExpect(content().string(containsString("Create your Pivotal ID")))
            .andExpect(content().string(not(containsString("Create account"))));
    }

    @Test
    public void testCreatingAnAccount() throws Exception {
        mockUaaServer.expect(requestTo("http://localhost:8080/uaa/Codes"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":\"rCvk9t\"," +
                                "\"expiresAt\":1406152741265," +
                                "\"data\":\"{\\\"username\\\":\\\"user@example.com\\\",\\\"client_id\\\":\\\"login\\\"}\"}",
                        APPLICATION_JSON));

        mockUaaServer.expect(requestTo("http://localhost:8080/uaa/create_account"))
            .andExpect(jsonPath("$.code").value("the_secret_code"))
            .andExpect(jsonPath("$.password").value("secret"))
            .andRespond(withSuccess("{" +
                    "\"user_id\":\"newly-created-user-id\"," +
                    "\"username\":\"user@example.com\"" +
                    "}", APPLICATION_JSON));

        mockMvc.perform(post("/accounts")
                    .param("email", "user@example.com")
                    .param("client_id", "login"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("accounts/email_sent"));

        MvcResult mvcResult = mockMvc.perform(post("/accounts")
                .param("email", "user@example.com")
                .param("code", "the_secret_code")
                .param("password", "secret")
                .param("password_confirmation", "secret"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("home"))
            .andReturn();

        SecurityContext securityContext = (SecurityContext) mvcResult.getRequest().getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = securityContext.getAuthentication();
        Assert.assertThat(authentication.getPrincipal(), instanceOf(UaaPrincipal.class));
        UaaPrincipal principal = (UaaPrincipal) authentication.getPrincipal();
        Assert.assertThat(principal.getId(), equalTo("newly-created-user-id"));
        Assert.assertThat(principal.getEmail(), equalTo("user@example.com"));
        Assert.assertThat(principal.getOrigin(), equalTo(Origin.UAA));
    }
}