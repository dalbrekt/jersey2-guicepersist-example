#Jersey-2 with Guice Persist
A project that demonstrates how to configure Google Guice with JPA in a Jersey2 (JAX-RS) container. This project does not 
use the Guice servlet module or the Guice persist filter - which anyway should be regarded as redundant components in a 
JAX-RS container (no state, no session awareness).

## Set up Guice persist with integration tests
The first step is to create an entity domain and a repository to store and retreive the entities.

### Domain
We'll use a simple domain model, only one class, for this example. 

```java
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@Entity
public class User implements Serializable {
    private static final long serialVersionUID = 3665349089500867570L;

    @Id
    @Column(length=36)
    private String id  = UUID.randomUUID().toString();

    @Version
    private Long version;

    @Column(unique = true)
    private String username;

    private String password;
    private boolean active;

    protected User() {}

    public User(final String username, final String password, final boolean active) {
        this.username = username;
        this.password = password;
        this.active = active;
    }
    public String getId() { 
        return id; 
    }
    public String getUsername() {
        return username;
    }
    public String getPassword() { 
        return password; 
    }
    public boolean isActive() { 
        return active; 
    }
}
```

### Repository
The repository to persist our domain. To ensure that each request get it's own thread safe entity manager, an 
[```Provider<EntityManager>```](https://github.com/google/guice/wiki/InjectingProviders) should be injected rather than 
[injecting the entity manager directly](http://stackoverflow.com/questions/12128552/guice-injected-entitymanager-in-multi-threaded-application).

```java
@Singleton
public class UserRepository {
    private Provider<EntityManager> emProvider;

    @Inject
    public UserRepository(Provider<EntityManager> emProvider) { 
        this.emProvider = emProvider; 
    }
    @Transactional
    public void persist(final User user) { 
        getEntityManager().persist(user); 
    }
    public User find(final String id) { 
        return getEntityManager().find(User.class, id);  
    }
    public List<User> findUserByName(final String username) {
        TypedQuery<User> q = getEntityManager()
                .createQuery("select u from User u where u.username like :username", User.class)
                .setParameter("username", username);
        return q.getResultList();
    }
    public EntityManager getEntityManager() { 
        return emProvider.get(); 
    }
}
```

### persistence.xml
Only a minimal ```persistence.xml``` is needed. Configuration of the database is performed in the ```PersistenceModule``` class.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence version="2.1"
     xmlns="http://xmlns.jcp.org/xml/ns/persistence" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence http://xmlns.jcp.org/xml/ns/persistence/persistence_2_1.xsd">

    <persistence-unit name="jpa-example" transaction-type="RESOURCE_LOCAL">
        <provider>org.eclipse.persistence.jpa.PersistenceProvider</provider>
        <mapping-file>META-INF/eclipselink-orm.xml</mapping-file>
        <exclude-unlisted-classes />
        <properties>
        </properties>
    </persistence-unit>

</persistence>
```

### eclipselink-orm.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<entity-mappings xmlns="http://www.eclipse.org/eclipselink/xsds/persistence/orm"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    version="2.5"
    xsi:schemaLocation="http://www.eclipse.org/eclipselink/xsds/persistence/orm http://www.eclipse.org/eclipselink/xsds/eclipselink_orm_2_5.xsd">

    <package>com.github.leifoolsen.jerseyguicepersist.domain</package>
    <entity class="User" />
</entity-mappings>
```

### GuiceModule
Bind Guice components.

```java
public class GuiceModule implements Module {
    @Override
    public void configure(Binder binder) {
        binder.bind(UserRepository.class);
    }
}
```

### PersistenceInitializer
Start the persistence service by invoking start() on ```PersistService```.

```java
@Singleton
public class PersistenceInitializer {
    @Inject
    public PersistenceInitializer(PersistService service) {
        service.start();
    }
}
```

### PersistenceModule
Configure database and install Guice Persist.

```java
public class PersistenceModule implements Module {
    @Override
    public void configure(Binder binder) {
        binder
          .install(new JpaPersistModule("jpa-example")
          .properties(getPersistenceProperties()));
          
        binder.bind(PersistenceInitializer.class).asEagerSingleton();
    }

    private static Properties getPersistenceProperties() {
        Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.driver", "org.h2.Driver");
        properties.put("javax.persistence.jdbc.url", "jdbc:h2:mem:mymemdb");
        properties.put("javax.persistence.jdbc.user", "sa");
        properties.put("javax.persistence.jdbc.password", "");
        ...
        ...
        return properties;
    }
}
```

### Repository Integration Tests
CRUD integration tests for our domain.

```java
public class UserRepositoryTest {
    private static Injector injector;

    @Inject
    private UnitOfWork unitOfWork;

    private static UserRepository userRepository = null;

    @BeforeClass
    public static void setUp() {
        injector = Guice.createInjector(new PersistenceModule(), new GuiceModule());
        userRepository = injector.getInstance(UserRepository.class);
    }
    @Before
    public void before() {
        if(unitOfWork == null) {
            injector.injectMembers(this);
            assertThat(unitOfWork, is(notNullValue()));
        }
        unitOfWork.begin();
    }
    @After
    public void after() {
        unitOfWork.end();
    }
    @Test
    public void addUser() {
        User user = new User("UserLOL", "lollol", true);
        userRepository.persist(user);
        assertThat(userRepository.find(user.getId()), is(notNullValue()));
    }
    @Test
    public void findUserByName() {
        User user = new User("User#2", "useruser", true);
        userRepository.persist(user);
        List<User> users = userRepository.findUserByName("User%");
        assertThat(users, hasSize(greaterThan(0)));
    }
}
```

## Set up JAX-RS with Client API Integration Tests
Typically, in a servlet environment, Guice is bootstrapped trough a ServletModule, and the HTTP request 
[Unit of Work](https://github.com/google/guice/wiki/Transactions) lifecycle is managed trough a PersistFilter.
The problem with this is that Servlet and JAX-RS are two separate containers, which in turn will lead to difficulties
maintainling various aspects of the application. For example, if we're using the Guice PersistFilter we must handle database
exceptions both in the Servlet container and in the JAX-RS container. A JAX-RS application shuld (in my opinion) have no 
knowledge of state and session - so servlets and servlet filters should not be required in JAX-RS. Rather, one can use a 
[JAX-RS Server Filter](https://jersey.java.net/documentation/latest/user-guide.html#d0e9579) to handle Unit of Work. 
 
With the Guice HK2 bridge in place, bootstrapping Guice in pure Java or in a JAX-RS container is no different.
 

### JAX-RS Application
The [JAX-RS Application Model](https://jersey.java.net/documentation/latest/user-guide.html#environmenmt.appmodel).

```java
@ApplicationPath("/api/*")
public class ApplicationModel extends ResourceConfig {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String APPLICATION_PATH;

    static {
        String appPath = "";
        if(ApplicationConfig.class.isAnnotationPresent(ApplicationPath.class)) {
            // Remove '/*' from @ApplicationPath, e.g:  "/api/*" -> /api
            appPath = ApplicationConfig.class.getAnnotation(ApplicationPath.class).value();
            appPath = appPath.substring(0, appPath.endsWith("/*") ? appPath.lastIndexOf("/*") : appPath.length()-1);
        }
        APPLICATION_PATH = appPath;
    }
    @Inject // Note: inject from HK2
    public ApplicationConfig(ServiceLocator serviceLocator) {

        // Guice
        Injector injector = Guice.createInjector(new PersistenceModule(), new GuiceModule());

        // Guice HK2 bridge
        // See e.g. https://github.com/t-tang/jetty-jersey-HK2-Guice-boilerplate
        GuiceBridge.getGuiceBridge().initializeGuiceBridge(serviceLocator);
        GuiceIntoHK2Bridge bridge = serviceLocator.getService(GuiceIntoHK2Bridge.class);
        bridge.bridgeGuiceInjector(injector);
        
        // Application startup and shutdown listener
        register(ApplicationLifecycleListener.class);

        // Scans during deployment for JAX-RS components in packages
        packages("com.github.leifoolsen.jerseyguicepersist.rest");
    }
    private static class ApplicationLifecycleListener extends AbstractContainerLifecycleListener {
        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Inject // Note: The HK2 bridge takes care of injecting from correct DI-container
        PersistService service;

        @Override
        public void onStartup(Container container) {
            logger.info(">>> Application Startup");
        }

        @Override
        public void onShutdown(Container container) {
            logger.info(">>> Application Shutdown");

            // Stop persistence service
            service.stop();
        }
    }
}
```

### Unit of Work Filter
To start and end a [Unit of Work](https://github.com/google/guice/wiki/Transactions) arbitrarily we'll use a 
[JAX-RS server filter](https://jersey.java.net/documentation/latest/user-guide.html#d0e9579).

```java
@Provider
public class UnitOfWorkFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private UnitOfWork unitOfWork;

    @Inject
    public UnitOfWorkFilter(UnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
    }
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        unitOfWork.begin();
    }
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        unitOfWork.end();
    }
}
```

### Catch all Exception Mapper
An unhandled exception from the JAX-RS container will break the Unit of Work Filter; i.e. the response filter will not
execute. To keep the Unit of Work begin/end balanced, we must as a minimum implement a "catch all" exception mapper.

```java
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private UriInfo uriInfo; // actual uri info provided by parent resource (threadsafe)

    public GenericExceptionMapper(@Context UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }
    @Override
    public Response toResponse(Throwable t) {
        logger.error("Unhandeled exception: {}", t.toString());

        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Unhandeled exception: " + t.toString())
                .location(uriInfo.getRequestUri()) // uriInfo.getAbsolutePath()
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
```

### Rest API
A rest api which injects a Guice component. The Guice-HK2 bridge is responsible for injection from correct DI-container.

```java
@Singleton
@Path("users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    public static final String RESOURCE_PATH;

    static {
        RESOURCE_PATH = UserResource.class.isAnnotationPresent(Path.class)
                ? UserResource.class.getAnnotation(Path.class).value() : "";
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private UserRepository userRepository;

    @Inject
    public UserResource(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void create(final User user) {
        userRepository.persist(user);
    }
    @GET
    @Path("{id}")
    public User find(@PathParam("id") final String id) {
        return userRepository.find(id);
    }
    @GET
    @Path("test-unsupported-exception")
    public Object unsupportedException() {
        // The GenericExceptionMapper class should catch this exception and return
        // a Response.Status.INTERNAL_SERVER_ERROR status to the client
        throw new UnsupportedOperationException("UNSUPPORTED!!!");
    }
}
```

### Client API Integration Tests
We'll use the standard [JAX-RS2 client api](https://jersey.java.net/documentation/latest/user-guide.html#client) to test our resource.

```java
public class UserResourceTest {
    private static final int PORT = 8080;
    private static final String DEFAULT_CONTEXT_PATH = "/";

    private static Server server;
    private static WebTarget target;

    private static String idU1;
    private static String idU2;

    @BeforeClass
    public static void setUp() throws Exception {
        // Start the server
        server = JettyBootstrap.start(DEFAULT_CONTEXT_PATH, PORT);

        // create the client
        Client c = ClientBuilder.newClient();
        target = c.target(server.getURI()).path(ApplicationConfig.APPLICATION_PATH);

        User u1 = new User("U1", "u1u1", true);
        idU1 = u1.getId();

        User u2 = new User("U2", "u2u2", true);
        idU2 = u2.getId();

        target.path(UserResource.RESOURCE_PATH)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(u1, MediaType.APPLICATION_JSON_TYPE));

        target.path(UserResource.RESOURCE_PATH)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(u2, MediaType.APPLICATION_JSON_TYPE));
    }
    @AfterClass
    public static void tearDown() throws Exception {
        JettyBootstrap.stop(server);
    }
    @Test
    public void shouldFindUserByGivenId() {
        final Response response = target
                .path("users")
                .path(idU1)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        assertThat(response.getStatus(), equalTo(Response.Status.OK.getStatusCode()));

        User u = response.readEntity(User.class);
        assertNotNull(u);
        assertThat(u.getId(), equalTo(idU1));
    }
    @Test
    public void unhandeledExceptionShouldReturn_INTERNAL_SERVER_ERROR() {
        final Response response = target
                .path("users")
                .path("test-unsupported-exception")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();

        assertThat(response.getStatus(), equalTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
    }
    @Test
    public void getApplicationWadl() throws Exception {
        final Response response = target
                .path("application.wadl")
                .request(MediaType.APPLICATION_XML)
                .get();

        assertThat(response.getStatus(), equalTo(Response.Status.OK.getStatusCode()));
        String wadl = response.readEntity(String.class);
        assertThat(wadl.length(), greaterThan(0));
    }
}
```

##Steps to run this project
* Fork, Clone or Download ZIP
* Build project: *mvn clean install -U*
* Start Jetty from project folder: *mvn exec:java*
* Application.wadl: *http://localhost:8080/api/application.wadl*
* Example usage: *http://localhost:8080/api/users*
* Import project into your favourite IDE
* Open `UserResourceTest.java` to start exploring code

###Note
The project can be packaged with the [appassembler-maven-plugin](http://mojo.codehaus.org/appassembler/appassembler-maven-plugin/)

* Build the project with the *appassembler* profile: *mvn install -Pappassembler* 
* ... then run the app from the project folder with the following command:<br/>sh _target/appassembler/bin/startapp_
* Open a browser and hit *http://localhost:8087/api/users*
