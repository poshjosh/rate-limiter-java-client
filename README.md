# rate limiter java client

### Java client for [rate-limiter-service](https://github.com/poshjosh/rate-limiter-service)

- Light weight (only 7 classes)
- Easy to use
- 2 dependencies: 
  - `com.fasterxml.jackson.core:jackson-databind`
  - `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`

### Benefits

- The consuming service does not need to concern itself about rate limiting logic.
- All the consuming service needs is basically:
  - `POST /rates '{"id":"login", "rate":"6/m"}'`
  - `GET /permits?id=login`
- Automated deployment to cloud platforms like AWS, Azure, GCP, etc. __(COMING SOON)__
  
### Sample Usage

pom.xml

```xml
        <dependency>
            <groupId>io.github.poshjosh</groupId>
            <artifactId>rate-limiter-java-client</artifactId>
        </dependency>
```

rest controller

```java
import io.github.poshjosh.ratelimiter.client.RateLimiterServiceClient;

@RestController("/messages") 
public class MessageController {
    
    private final RateLimiterServiceClient rateLimiter = 
            new RateLimiterServiceClient("http://localhost:8080");
    
    @PostMapping("/greet")
    public ResponseEntity<String> greet(HttpServletRequest request) {
        
        rateLimiter.checkLimit(request, "messages.greet", 
                "6/m", "web.request.header[X-RATE-LIMITED] = true");
        
        return ResponseEntity.ok("Hello World!");
    }
}
```

The above class is equivalent to the below, where we are rate limiting on site.

pom.xml

```xml
        <dependency>
            <groupId>io.github.poshjosh</groupId>
            <artifactId>rate-limiter-spring</artifactId>
        </dependency>
```

rest controller

```java
@RestController("/messages") 
public class MessageController {
    
    @PostMapping("/greet")
    @Rate(rate = "6/m", when = "web.request.header[X-RATE-LIMITED] = true")
    public ResponseEntity<String> greet(HttpServletRequest request) {
        
        return ResponseEntity.ok("Hello World!");
    }
}
```