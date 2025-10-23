# Resilient Paradex Instrument Lookup

The `ParadexInstrumentLookup` class has been enhanced with **resilience4j** to provide automatic retry functionality for API calls. This makes the instrument lookup operations more robust by automatically retrying temporary failures while failing fast on permanent errors.

## Features

### Automatic Retry Logic

- **Read Operations**: Instrument lookups are treated as read operations with retry logic
- **Smart Error Detection**: Distinguishes between temporary and permanent failures
- **Configurable Retry Behavior**: Customizable retry attempts, wait durations, and patterns

### Retry Behavior

#### **Will Retry** (Temporary Errors):

- Network timeouts (`SocketTimeoutException`)
- Connection errors (`ConnectException`, `UnknownHostException`)
- Server errors (HTTP 5xx responses)
- Rate limiting (HTTP 429)
- Connection resets or refused connections

#### **Will NOT Retry** (Permanent Errors):

- Client errors (HTTP 4xx except 429)
- Authentication errors (HTTP 401, 403)
- Not found errors (HTTP 404)
- Bad request errors (HTTP 400)

### Default Configuration

- **Max Attempts**: 4 (1 original + 3 retries)
- **Wait Duration**: 1 second between retries
- **Retry Pattern**: Fixed wait duration

## Usage Examples

### Basic Usage (Default Configuration)

```java
// Uses default retry configuration automatically
ParadexInstrumentLookup lookup = new ParadexInstrumentLookup();

try {
    // This will automatically retry on temporary failures
    InstrumentDescriptor btc = lookup.lookupByExchangeSymbol("BTC-USD-PERP");
    InstrumentDescriptor eth = lookup.lookupByCommonSymbol("ETH");
    InstrumentDescriptor[] allInstruments = lookup.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES);
} catch (FueledByChaiException e) {
    // Thrown when all retries are exhausted or permanent error occurs
    logger.error("Lookup failed: {}", e.getMessage());
}
```

### Custom Retry Configuration

```java
// Create custom retry configuration
ParadexLookupRetryConfig customConfig = ParadexLookupRetryConfig.defaultConfig()
    .setMaxAttempts(6)                          // More aggressive retries
    .setWaitDuration(Duration.ofMillis(500));   // Shorter wait time

// Use custom configuration
ParadexInstrumentLookup lookup = new ParadexInstrumentLookup(
    ParadexApiFactory.getPublicApi(),
    customConfig
);
```

### Predefined Configurations

```java
// Aggressive retry (more attempts, shorter waits)
ParadexLookupRetryConfig aggressive = ParadexLookupRetryConfig.aggressiveConfig();

// Conservative retry (fewer attempts, longer waits)
ParadexLookupRetryConfig conservative = ParadexLookupRetryConfig.conservativeConfig();

// Default balanced approach
ParadexLookupRetryConfig defaultConfig = ParadexLookupRetryConfig.defaultConfig();
```

### Dependency Injection

```java
// For testing or when you want to provide your own API instance
IParadexRestApi mockApi = // ... your mock or custom implementation
ParadexInstrumentLookup lookup = new ParadexInstrumentLookup(mockApi);
```

## Error Handling

The resilient lookup provides clear error handling:

```java
try {
    InstrumentDescriptor instrument = lookup.lookupByExchangeSymbol("BTC-USD-PERP");
    // Success - instrument found (possibly after retries)

} catch (FueledByChaiException e) {
    // This exception is thrown in two cases:
    // 1. All retry attempts exhausted for temporary errors
    // 2. Permanent error occurred (no retries attempted)

    logger.error("Lookup failed: {}", e.getMessage());

    // Check the cause for more details
    if (e.getCause() instanceof IOException) {
        // Network/IO related failure
    }
}
```

## Monitoring and Logging

The resilient lookup provides comprehensive logging:

- **Retry Attempts**: Logs each retry attempt with reason
- **Success After Retries**: Logs when operation succeeds after retries
- **Error Classification**: Debug logs show why errors are/aren't retryable

```
WARN  - Instrument lookup retry #1 for operation due to: HTTP 503 Service Unavailable
WARN  - Instrument lookup retry #2 for operation due to: Connection timeout
INFO  - Instrument lookup succeeded after 2 retries
```

## Integration with Existing Code

The enhanced `ParadexInstrumentLookup` is **backward compatible**. Existing code using the class will automatically benefit from the retry functionality without any changes:

```java
// This existing code now gets automatic retry functionality
ParadexInstrumentLookup lookup = new ParadexInstrumentLookup();
InstrumentDescriptor btc = lookup.lookupByExchangeSymbol("BTC-USD-PERP");
```

## Dependencies

The resilient functionality requires:

- **resilience4j-all**: Version 2.3.0 (already included in pom.xml)

## Testing

The `ResilientParadexInstrumentLookupTest` class provides comprehensive tests demonstrating:

- Successful operations without retries
- Retry behavior on temporary failures
- No retries on permanent failures
- Retry exhaustion handling

Run tests with:

```bash
mvn test -Dtest=ResilientParadexInstrumentLookupTest
```
