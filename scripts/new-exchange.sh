#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

WITH_MARKET_DATA=false
WITH_BROKER=false
WITH_HISTORICAL=false
REGISTER_MODULES=true
UPDATE_EXCHANGE_ENUM=false
DRY_RUN=false

usage() {
    cat <<'EOF'
Usage:
  scripts/new-exchange.sh <exchange-slug> [options]

Description:
  Scaffolds a new exchange integration with the current provider/factory pattern.
  By default this creates only the common module:
    commons/<exchange>-common-api

  Optional flags add implementation modules:
    implementations/market-data-api/<exchange>-market-data-impl
    implementations/broker-api/<exchange>-broker-api-impl
    implementations/historical-data-api/<exchange>-historical-data-api-impl

Options:
  --with-market-data      Create market data implementation module.
  --with-broker           Create broker implementation module.
  --with-historical       Create historical data implementation module.
  --all                   Equivalent to all three flags above.
  --no-register-modules   Do not add generated modules to parent pom.xml files.
  --update-exchange-enum  Add Exchange constant + ALL_EXCHANGES entry in Exchange.java.
  --dry-run               Print planned changes without writing files.
  -h, --help              Show this help.

Examples:
  scripts/new-exchange.sh vertex --all --update-exchange-enum
  scripts/new-exchange.sh aevo --with-market-data --dry-run
EOF
}

log_info() {
    printf '[INFO] %s\n' "$*"
}

log_warn() {
    printf '[WARN] %s\n' "$*" >&2
}

die() {
    printf '[ERROR] %s\n' "$*" >&2
    exit 1
}

to_camel_case() {
    local value="$1"
    awk -F- '{
        for (i = 1; i <= NF; i++) {
            printf toupper(substr($i, 1, 1)) substr($i, 2)
        }
    }' <<<"${value}"
}

create_file() {
    local path="$1"
    if [[ -e "${path}" ]]; then
        log_warn "File already exists, skipping: ${path}"
        cat >/dev/null
        return
    fi
    if [[ "${DRY_RUN}" == "true" ]]; then
        log_info "[dry-run] create ${path}"
        cat >/dev/null
        return
    fi
    mkdir -p "$(dirname "${path}")"
    cat >"${path}"
    log_info "Created ${path}"
}

add_module_to_pom() {
    local pom_path="$1"
    local module_name="$2"

    if ! [[ -f "${pom_path}" ]]; then
        log_warn "Cannot update modules, missing ${pom_path}"
        return
    fi
    if grep -Fq "<module>${module_name}</module>" "${pom_path}"; then
        log_info "Module already registered in ${pom_path}: ${module_name}"
        return
    fi
    if [[ "${DRY_RUN}" == "true" ]]; then
        log_info "[dry-run] add <module>${module_name}</module> to ${pom_path}"
        return
    fi
    if ! grep -Fq "</modules>" "${pom_path}"; then
        log_warn "No </modules> block found in ${pom_path}. Skipping module registration."
        return
    fi

    local tmp_file
    tmp_file="$(mktemp)"
    awk -v module_name="${module_name}" '
        /<\/modules>/ && !done {
            print "    <module>" module_name "</module>"
            done = 1
        }
        { print }
    ' "${pom_path}" >"${tmp_file}"
    mv "${tmp_file}" "${pom_path}"
    log_info "Updated ${pom_path} with module ${module_name}"
}

update_exchange_enum_file() {
    local exchange_file="commons/fueledbychai-commons-api/src/main/java/com/fueledbychai/data/Exchange.java"
    if ! [[ -f "${exchange_file}" ]]; then
        log_warn "Cannot update Exchange enum. Missing ${exchange_file}"
        return
    fi

    if grep -Fq "public static final Exchange ${EXCHANGE_CONST} = new Exchange(\"${EXCHANGE_CONST}\");" "${exchange_file}"; then
        log_info "Exchange constant already exists: ${EXCHANGE_CONST}"
    else
        if [[ "${DRY_RUN}" == "true" ]]; then
            log_info "[dry-run] add Exchange constant ${EXCHANGE_CONST} to ${exchange_file}"
        else
            local tmp_file
            tmp_file="$(mktemp)"
            awk -v c="${EXCHANGE_CONST}" '
                /public static final Exchange\[] ALL_EXCHANGES/ && !done {
                    print "    public static final Exchange " c " = new Exchange(\"" c "\");"
                    done = 1
                }
                { print }
            ' "${exchange_file}" >"${tmp_file}"
            mv "${tmp_file}" "${exchange_file}"
            log_info "Added Exchange constant ${EXCHANGE_CONST}"
        fi
    fi

    if grep -A4 -F "ALL_EXCHANGES" "${exchange_file}" | grep -Fq "${EXCHANGE_CONST}"; then
        log_info "ALL_EXCHANGES already contains ${EXCHANGE_CONST}"
        return
    fi

    if [[ "${DRY_RUN}" == "true" ]]; then
        log_info "[dry-run] add ${EXCHANGE_CONST} to ALL_EXCHANGES in ${exchange_file}"
        return
    fi

    perl -0777 -i -pe "s/(public static final Exchange\\[] ALL_EXCHANGES = \\{[^}]*?)(\\s*\\};)/\${1}, ${EXCHANGE_CONST}\${2}/s" "${exchange_file}"
    log_info "Added ${EXCHANGE_CONST} to ALL_EXCHANGES"
}

if [[ $# -lt 1 ]]; then
    usage
    exit 1
fi

if [[ "${1}" == "--help" || "${1}" == "-h" ]]; then
    usage
    exit 0
fi

EXCHANGE_RAW="$1"
shift

while [[ $# -gt 0 ]]; do
    case "$1" in
    --with-market-data)
        WITH_MARKET_DATA=true
        ;;
    --with-broker)
        WITH_BROKER=true
        ;;
    --with-historical)
        WITH_HISTORICAL=true
        ;;
    --all)
        WITH_MARKET_DATA=true
        WITH_BROKER=true
        WITH_HISTORICAL=true
        ;;
    --no-register-modules)
        REGISTER_MODULES=false
        ;;
    --update-exchange-enum)
        UPDATE_EXCHANGE_ENUM=true
        ;;
    --dry-run)
        DRY_RUN=true
        ;;
    --help | -h)
        usage
        exit 0
        ;;
    *)
        die "Unknown option: $1"
        ;;
    esac
    shift
done

EXCHANGE_SLUG="$(echo "${EXCHANGE_RAW}" | tr '[:upper:]' '[:lower:]' | tr ' _' '--' | sed 's/--*/-/g; s/^-//; s/-$//')"
if ! [[ "${EXCHANGE_SLUG}" =~ ^[a-z][a-z0-9-]*$ ]]; then
    die "Invalid exchange slug '${EXCHANGE_RAW}'. Use letters/numbers/hyphens, starting with a letter."
fi

PACKAGE_SEGMENT="${EXCHANGE_SLUG//-/}"
EXCHANGE_CAMEL="$(to_camel_case "${EXCHANGE_SLUG}")"
EXCHANGE_CONST="$(echo "${EXCHANGE_SLUG}" | tr '[:lower:]-' '[:upper:]_')"

COMMON_MODULE="${EXCHANGE_SLUG}-common-api"
COMMON_DIR="commons/${COMMON_MODULE}"
COMMON_BASE_JAVA="${COMMON_DIR}/src/main/java/com/fueledbychai/${PACKAGE_SEGMENT}/common"
COMMON_API_JAVA="${COMMON_BASE_JAVA}/api"
COMMON_SERVICES_DIR="${COMMON_DIR}/src/main/resources/META-INF/services"

MARKET_MODULE="${EXCHANGE_SLUG}-market-data-impl"
MARKET_DIR="implementations/market-data-api/${MARKET_MODULE}"
MARKET_JAVA_DIR="${MARKET_DIR}/src/main/java/com/fueledbychai/marketdata/${PACKAGE_SEGMENT}"
MARKET_SERVICES_DIR="${MARKET_DIR}/src/main/resources/META-INF/services"

BROKER_MODULE="${EXCHANGE_SLUG}-broker-api-impl"
BROKER_DIR="implementations/broker-api/${BROKER_MODULE}"
BROKER_JAVA_DIR="${BROKER_DIR}/src/main/java/com/fueledbychai/broker/${PACKAGE_SEGMENT}"
BROKER_SERVICES_DIR="${BROKER_DIR}/src/main/resources/META-INF/services"

HIST_MODULE="${EXCHANGE_SLUG}-historical-data-api-impl"
HIST_DIR="implementations/historical-data-api/${HIST_MODULE}"
HIST_JAVA_DIR="${HIST_DIR}/src/main/java/com/fueledbychai/${PACKAGE_SEGMENT}/historical"
HIST_SERVICES_DIR="${HIST_DIR}/src/main/resources/META-INF/services"

if [[ -d "${COMMON_DIR}" ]]; then
    die "Target common module already exists: ${COMMON_DIR}"
fi
if [[ "${WITH_MARKET_DATA}" == "true" && -d "${MARKET_DIR}" ]]; then
    die "Target market data module already exists: ${MARKET_DIR}"
fi
if [[ "${WITH_BROKER}" == "true" && -d "${BROKER_DIR}" ]]; then
    die "Target broker module already exists: ${BROKER_DIR}"
fi
if [[ "${WITH_HISTORICAL}" == "true" && -d "${HIST_DIR}" ]]; then
    die "Target historical module already exists: ${HIST_DIR}"
fi

log_info "Scaffolding exchange '${EXCHANGE_SLUG}'"
log_info "  Exchange constant: ${EXCHANGE_CONST}"
log_info "  Java prefix: ${EXCHANGE_CAMEL}"
log_info "  Package segment: ${PACKAGE_SEGMENT}"

create_file "${COMMON_DIR}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fueledbychai</groupId>
        <artifactId>fueledbychai-commons</artifactId>
        <version>0.2.0-SNAPSHOT</version>
    </parent>

    <artifactId>${COMMON_MODULE}</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>fueledbychai-commons-api</artifactId>
            <version>\${project.version}</version>
        </dependency>
    </dependencies>
</project>
EOF

create_file "${COMMON_API_JAVA}/I${EXCHANGE_CAMEL}RestApi.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common.api;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

public interface I${EXCHANGE_CAMEL}RestApi {

    InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType);

    InstrumentDescriptor getInstrumentDescriptor(String symbol);

    boolean isPublicApiOnly();
}
EOF

create_file "${COMMON_API_JAVA}/I${EXCHANGE_CAMEL}WebSocketApi.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common.api;

public interface I${EXCHANGE_CAMEL}WebSocketApi {

    void connect();
}
EOF

create_file "${COMMON_API_JAVA}/${EXCHANGE_CAMEL}Configuration.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common.api;

public class ${EXCHANGE_CAMEL}Configuration {

    private static volatile ${EXCHANGE_CAMEL}Configuration instance;
    private static final Object LOCK = new Object();

    public static ${EXCHANGE_CAMEL}Configuration getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new ${EXCHANGE_CAMEL}Configuration();
                }
            }
        }
        return instance;
    }

    public static void reset() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    public String getEnvironment() {
        return System.getProperty("${EXCHANGE_SLUG}.environment", "prod");
    }

    public String getRestUrl() {
        return System.getProperty("${EXCHANGE_SLUG}.rest.url", "https://api.${EXCHANGE_SLUG}.example");
    }

    public String getWebSocketUrl() {
        return System.getProperty("${EXCHANGE_SLUG}.ws.url", "wss://ws.${EXCHANGE_SLUG}.example");
    }

    public String getAccountAddress() {
        return System.getProperty("${EXCHANGE_SLUG}.account.address");
    }

    public String getPrivateKey() {
        return System.getProperty("${EXCHANGE_SLUG}.private.key");
    }

    public boolean hasPrivateKeyConfiguration() {
        String account = getAccountAddress();
        String key = getPrivateKey();
        return account != null && !account.trim().isEmpty() && key != null && !key.trim().isEmpty();
    }
}
EOF

create_file "${COMMON_API_JAVA}/${EXCHANGE_CAMEL}RestApi.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common.api;

import com.fueledbychai.data.InstrumentDescriptor;
import com.fueledbychai.data.InstrumentType;

public class ${EXCHANGE_CAMEL}RestApi implements I${EXCHANGE_CAMEL}RestApi {

    protected final String baseUrl;
    protected final String accountAddress;
    protected final String privateKey;
    protected final boolean publicApiOnly;

    public ${EXCHANGE_CAMEL}RestApi(String baseUrl) {
        this(baseUrl, null, null);
    }

    public ${EXCHANGE_CAMEL}RestApi(String baseUrl, String accountAddress, String privateKey) {
        this.baseUrl = baseUrl;
        this.accountAddress = accountAddress;
        this.privateKey = privateKey;
        this.publicApiOnly = accountAddress == null || privateKey == null || accountAddress.isBlank() || privateKey.isBlank();
    }

    @Override
    public InstrumentDescriptor[] getAllInstrumentsForType(InstrumentType instrumentType) {
        throw new UnsupportedOperationException("TODO: Implement " + getClass().getSimpleName()
                + ".getAllInstrumentsForType");
    }

    @Override
    public InstrumentDescriptor getInstrumentDescriptor(String symbol) {
        throw new UnsupportedOperationException("TODO: Implement " + getClass().getSimpleName()
                + ".getInstrumentDescriptor");
    }

    @Override
    public boolean isPublicApiOnly() {
        return publicApiOnly;
    }
}
EOF

create_file "${COMMON_API_JAVA}/${EXCHANGE_CAMEL}WebSocketApi.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common.api;

public class ${EXCHANGE_CAMEL}WebSocketApi implements I${EXCHANGE_CAMEL}WebSocketApi {

    protected final String webSocketUrl;

    public ${EXCHANGE_CAMEL}WebSocketApi(String webSocketUrl) {
        this.webSocketUrl = webSocketUrl;
    }

    @Override
    public void connect() {
        throw new UnsupportedOperationException("TODO: Implement " + getClass().getSimpleName() + ".connect");
    }
}
EOF

create_file "${COMMON_API_JAVA}/${EXCHANGE_CAMEL}RestApiProvider.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeRestApiProvider;

public class ${EXCHANGE_CAMEL}RestApiProvider implements ExchangeRestApiProvider<I${EXCHANGE_CAMEL}RestApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.${EXCHANGE_CONST};
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<I${EXCHANGE_CAMEL}RestApi> getApiType() {
        return (Class<I${EXCHANGE_CAMEL}RestApi>) (Class<?>) I${EXCHANGE_CAMEL}RestApi.class;
    }

    @Override
    public I${EXCHANGE_CAMEL}RestApi getPublicApi() {
        return new ${EXCHANGE_CAMEL}RestApi(${EXCHANGE_CAMEL}Configuration.getInstance().getRestUrl());
    }

    @Override
    public I${EXCHANGE_CAMEL}RestApi getApi() {
        return isPrivateApiAvailable() ? getPrivateApi() : getPublicApi();
    }

    @Override
    public boolean isPrivateApiAvailable() {
        return ${EXCHANGE_CAMEL}Configuration.getInstance().hasPrivateKeyConfiguration();
    }

    @Override
    public I${EXCHANGE_CAMEL}RestApi getPrivateApi() {
        ${EXCHANGE_CAMEL}Configuration config = ${EXCHANGE_CAMEL}Configuration.getInstance();
        if (!config.hasPrivateKeyConfiguration()) {
            throw new IllegalStateException("${EXCHANGE_CAMEL} private API requires account and private key configuration.");
        }
        return new ${EXCHANGE_CAMEL}RestApi(config.getRestUrl(), config.getAccountAddress(), config.getPrivateKey());
    }
}
EOF

create_file "${COMMON_API_JAVA}/${EXCHANGE_CAMEL}WebSocketApiProvider.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common.api;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ExchangeWebSocketApiProvider;

public class ${EXCHANGE_CAMEL}WebSocketApiProvider implements ExchangeWebSocketApiProvider<I${EXCHANGE_CAMEL}WebSocketApi> {

    @Override
    public Exchange getExchange() {
        return Exchange.${EXCHANGE_CONST};
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<I${EXCHANGE_CAMEL}WebSocketApi> getApiType() {
        return (Class<I${EXCHANGE_CAMEL}WebSocketApi>) (Class<?>) I${EXCHANGE_CAMEL}WebSocketApi.class;
    }

    @Override
    public I${EXCHANGE_CAMEL}WebSocketApi getWebSocketApi() {
        return new ${EXCHANGE_CAMEL}WebSocketApi(${EXCHANGE_CAMEL}Configuration.getInstance().getWebSocketUrl());
    }
}
EOF

create_file "${COMMON_BASE_JAVA}/${EXCHANGE_CAMEL}TickerRegistry.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.data.TickerTranslator;
import com.fueledbychai.${PACKAGE_SEGMENT}.common.api.I${EXCHANGE_CAMEL}RestApi;
import com.fueledbychai.util.AbstractTickerRegistry;
import com.fueledbychai.util.ExchangeRestApiFactory;
import com.fueledbychai.util.ITickerRegistry;

public class ${EXCHANGE_CAMEL}TickerRegistry extends AbstractTickerRegistry implements ITickerRegistry {

    protected static ITickerRegistry instance;
    protected final I${EXCHANGE_CAMEL}RestApi restApi;

    public static ITickerRegistry getInstance(I${EXCHANGE_CAMEL}RestApi restApi) {
        if (instance == null) {
            instance = new ${EXCHANGE_CAMEL}TickerRegistry(restApi);
        }
        return instance;
    }

    public static ITickerRegistry getInstance() {
        if (instance == null) {
            instance = new ${EXCHANGE_CAMEL}TickerRegistry(
                    ExchangeRestApiFactory.getPublicApi(Exchange.${EXCHANGE_CONST}, I${EXCHANGE_CAMEL}RestApi.class));
        }
        return instance;
    }

    protected ${EXCHANGE_CAMEL}TickerRegistry(I${EXCHANGE_CAMEL}RestApi restApi) {
        super(new TickerTranslator());
        if (restApi == null) {
            throw new IllegalArgumentException("restApi is required");
        }
        this.restApi = restApi;
        initialize();
    }

    protected void initialize() {
        registerDescriptors(restApi.getAllInstrumentsForType(InstrumentType.PERPETUAL_FUTURES));
    }

    @Override
    protected boolean supportsInstrumentType(InstrumentType instrumentType) {
        return instrumentType == InstrumentType.PERPETUAL_FUTURES;
    }

    @Override
    public String commonSymbolToExchangeSymbol(InstrumentType instrumentType, String commonSymbol) {
        requireSupportedInstrumentType(instrumentType);
        return commonSymbol == null ? null : commonSymbol.trim();
    }
}
EOF

create_file "${COMMON_BASE_JAVA}/${EXCHANGE_CAMEL}TickerRegistryProvider.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.util.ITickerRegistry;
import com.fueledbychai.util.TickerRegistryProvider;

public class ${EXCHANGE_CAMEL}TickerRegistryProvider implements TickerRegistryProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.${EXCHANGE_CONST};
    }

    @Override
    public ITickerRegistry getRegistry() {
        return ${EXCHANGE_CAMEL}TickerRegistry.getInstance();
    }
}
EOF

create_file "${COMMON_BASE_JAVA}/${EXCHANGE_CAMEL}ExchangeCapabilitiesProvider.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.common;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.data.InstrumentType;
import com.fueledbychai.util.DefaultExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilities;
import com.fueledbychai.util.ExchangeCapabilitiesProvider;

public class ${EXCHANGE_CAMEL}ExchangeCapabilitiesProvider implements ExchangeCapabilitiesProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.${EXCHANGE_CONST};
    }

    @Override
    public ExchangeCapabilities getCapabilities() {
        return DefaultExchangeCapabilities.builder(Exchange.${EXCHANGE_CONST})
                .supportsStreaming(false)
                .supportsBrokerage(false)
                .supportsHistoricalData(false)
                .addInstrumentType(InstrumentType.PERPETUAL_FUTURES)
                .build();
    }
}
EOF

create_file "${COMMON_SERVICES_DIR}/com.fueledbychai.util.ExchangeRestApiProvider" <<EOF
com.fueledbychai.${PACKAGE_SEGMENT}.common.api.${EXCHANGE_CAMEL}RestApiProvider
EOF

create_file "${COMMON_SERVICES_DIR}/com.fueledbychai.util.ExchangeWebSocketApiProvider" <<EOF
com.fueledbychai.${PACKAGE_SEGMENT}.common.api.${EXCHANGE_CAMEL}WebSocketApiProvider
EOF

create_file "${COMMON_SERVICES_DIR}/com.fueledbychai.util.TickerRegistryProvider" <<EOF
com.fueledbychai.${PACKAGE_SEGMENT}.common.${EXCHANGE_CAMEL}TickerRegistryProvider
EOF

create_file "${COMMON_SERVICES_DIR}/com.fueledbychai.util.ExchangeCapabilitiesProvider" <<EOF
com.fueledbychai.${PACKAGE_SEGMENT}.common.${EXCHANGE_CAMEL}ExchangeCapabilitiesProvider
EOF

if [[ "${WITH_MARKET_DATA}" == "true" ]]; then
    create_file "${MARKET_DIR}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <artifactId>market-data-api</artifactId>
        <groupId>com.fueledbychai</groupId>
        <version>0.2.0-SNAPSHOT</version>
    </parent>

    <artifactId>${MARKET_MODULE}</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>fueledbychai-market-data-api</artifactId>
            <version>\${project.version}</version>
        </dependency>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>${COMMON_MODULE}</artifactId>
            <version>\${project.version}</version>
        </dependency>
    </dependencies>
</project>
EOF

    create_file "${MARKET_JAVA_DIR}/${EXCHANGE_CAMEL}QuoteEngine.java" <<EOF
package com.fueledbychai.marketdata.${PACKAGE_SEGMENT};

import java.util.Date;
import java.util.Properties;

import com.fueledbychai.marketdata.QuoteEngine;

public class ${EXCHANGE_CAMEL}QuoteEngine extends QuoteEngine {

    protected volatile boolean started = false;

    @Override
    public String getDataProviderName() {
        return "${EXCHANGE_CAMEL}";
    }

    @Override
    public Date getServerTime() {
        return new Date();
    }

    @Override
    public boolean isConnected() {
        return started;
    }

    @Override
    public void startEngine() {
        started = true;
    }

    @Override
    public void startEngine(Properties props) {
        startEngine();
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void stopEngine() {
        started = false;
    }

    @Override
    public void useDelayedData(boolean useDelayed) {
        // no-op placeholder
    }
}
EOF

    create_file "${MARKET_JAVA_DIR}/${EXCHANGE_CAMEL}QuoteEngineProvider.java" <<EOF
package com.fueledbychai.marketdata.${PACKAGE_SEGMENT};

import com.fueledbychai.data.Exchange;
import com.fueledbychai.marketdata.QuoteEngine;
import com.fueledbychai.marketdata.QuoteEngineProvider;

public class ${EXCHANGE_CAMEL}QuoteEngineProvider implements QuoteEngineProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.${EXCHANGE_CONST};
    }

    @Override
    public Class<? extends QuoteEngine> getQuoteEngineClass() {
        return ${EXCHANGE_CAMEL}QuoteEngine.class;
    }
}
EOF

    create_file "${MARKET_SERVICES_DIR}/com.fueledbychai.marketdata.QuoteEngineProvider" <<EOF
com.fueledbychai.marketdata.${PACKAGE_SEGMENT}.${EXCHANGE_CAMEL}QuoteEngineProvider
EOF
fi

if [[ "${WITH_BROKER}" == "true" ]]; then
    create_file "${BROKER_DIR}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fueledbychai</groupId>
        <artifactId>broker-api</artifactId>
        <version>0.2.0-SNAPSHOT</version>
    </parent>

    <artifactId>${BROKER_MODULE}</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>fueledbychai-broker-api</artifactId>
            <version>\${project.version}</version>
        </dependency>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>fueledbychai-commons-api</artifactId>
            <version>\${project.version}</version>
        </dependency>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>${COMMON_MODULE}</artifactId>
            <version>\${project.version}</version>
        </dependency>
    </dependencies>
</project>
EOF

    create_file "${BROKER_JAVA_DIR}/${EXCHANGE_CAMEL}Broker.java" <<EOF
package com.fueledbychai.broker.${PACKAGE_SEGMENT};

import java.util.Collections;
import java.util.List;

import com.fueledbychai.broker.AbstractBasicBroker;
import com.fueledbychai.broker.BrokerRequestResult;
import com.fueledbychai.broker.BrokerStatus;
import com.fueledbychai.broker.Position;
import com.fueledbychai.broker.order.OrderTicket;
import com.fueledbychai.data.Ticker;

public class ${EXCHANGE_CAMEL}Broker extends AbstractBasicBroker {

    protected volatile boolean connected = false;
    protected int nextOrderId = 1;

    @Override
    protected void onDisconnect() {
        connected = false;
    }

    @Override
    public String getBrokerName() {
        return "${EXCHANGE_CAMEL}";
    }

    @Override
    public BrokerRequestResult cancelOrder(String id) {
        throw new UnsupportedOperationException("TODO: Implement cancelOrder");
    }

    @Override
    public BrokerRequestResult cancelOrder(OrderTicket order) {
        throw new UnsupportedOperationException("TODO: Implement cancelOrder");
    }

    @Override
    public BrokerRequestResult cancelAllOrders(Ticker ticker) {
        throw new UnsupportedOperationException("TODO: Implement cancelAllOrders by ticker");
    }

    @Override
    public BrokerRequestResult cancelAllOrders() {
        throw new UnsupportedOperationException("TODO: Implement cancelAllOrders");
    }

    @Override
    public BrokerRequestResult placeOrder(OrderTicket order) {
        throw new UnsupportedOperationException("TODO: Implement placeOrder");
    }

    @Override
    public String getNextOrderId() {
        return Integer.toString(nextOrderId++);
    }

    @Override
    public void connect() {
        connected = true;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public BrokerStatus getBrokerStatus() {
        return connected ? BrokerStatus.UP : BrokerStatus.UNKNOWN;
    }

    @Override
    public OrderTicket requestOrderStatus(String orderId) {
        return null;
    }

    @Override
    public List<OrderTicket> getOpenOrders() {
        return Collections.emptyList();
    }

    @Override
    public void cancelAndReplaceOrder(String originalOrderId, OrderTicket newOrder) {
        throw new UnsupportedOperationException("TODO: Implement cancelAndReplaceOrder");
    }

    @Override
    public List<Position> getAllPositions() {
        return Collections.emptyList();
    }
}
EOF

    create_file "${BROKER_JAVA_DIR}/${EXCHANGE_CAMEL}BrokerProvider.java" <<EOF
package com.fueledbychai.broker.${PACKAGE_SEGMENT};

import com.fueledbychai.broker.BrokerProvider;
import com.fueledbychai.broker.IBroker;
import com.fueledbychai.data.Exchange;

public class ${EXCHANGE_CAMEL}BrokerProvider implements BrokerProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.${EXCHANGE_CONST};
    }

    @Override
    public IBroker getBroker() {
        return new ${EXCHANGE_CAMEL}Broker();
    }
}
EOF

    create_file "${BROKER_SERVICES_DIR}/com.fueledbychai.broker.BrokerProvider" <<EOF
com.fueledbychai.broker.${PACKAGE_SEGMENT}.${EXCHANGE_CAMEL}BrokerProvider
EOF
fi

if [[ "${WITH_HISTORICAL}" == "true" ]]; then
    create_file "${HIST_DIR}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fueledbychai</groupId>
        <artifactId>historical-data-api</artifactId>
        <version>0.2.0-SNAPSHOT</version>
    </parent>

    <artifactId>${HIST_MODULE}</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>fueledbychai-historical-data-api</artifactId>
            <version>\${project.version}</version>
        </dependency>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>${COMMON_MODULE}</artifactId>
            <version>\${project.version}</version>
        </dependency>
    </dependencies>
</project>
EOF

    create_file "${HIST_JAVA_DIR}/${EXCHANGE_CAMEL}HistoricalDataProvider.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.historical;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import com.fueledbychai.data.BarData;
import com.fueledbychai.data.Ticker;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;

public class ${EXCHANGE_CAMEL}HistoricalDataProvider implements IHistoricalDataProvider {

    protected volatile boolean connected = false;

    @Override
    public void init(Properties props) {
        // no-op placeholder
    }

    @Override
    public List<BarData> requestHistoricalData(Ticker ticker, Date endDateTime, int duration,
            BarData.LengthUnit durationLengthUnit, int barSize, BarData.LengthUnit barSizeUnit, ShowProperty whatToShow,
            boolean useRTH) throws IOException {
        throw new UnsupportedOperationException("TODO: Implement requestHistoricalData");
    }

    @Override
    public List<BarData> requestHistoricalData(Ticker ticker, int duration, BarData.LengthUnit durationLengthUnit,
            int barSize, BarData.LengthUnit barSizeUnit, ShowProperty whatToShow, boolean useRTH) {
        return Collections.emptyList();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void connect() {
        connected = true;
    }
}
EOF

    create_file "${HIST_JAVA_DIR}/${EXCHANGE_CAMEL}HistoricalDataProviderProvider.java" <<EOF
package com.fueledbychai.${PACKAGE_SEGMENT}.historical;

import com.fueledbychai.data.Exchange;
import com.fueledbychai.historicaldata.HistoricalDataProviderProvider;
import com.fueledbychai.historicaldata.IHistoricalDataProvider;

public class ${EXCHANGE_CAMEL}HistoricalDataProviderProvider implements HistoricalDataProviderProvider {

    @Override
    public Exchange getExchange() {
        return Exchange.${EXCHANGE_CONST};
    }

    @Override
    public IHistoricalDataProvider getProvider() {
        return new ${EXCHANGE_CAMEL}HistoricalDataProvider();
    }
}
EOF

    create_file "${HIST_SERVICES_DIR}/com.fueledbychai.historicaldata.HistoricalDataProviderProvider" <<EOF
com.fueledbychai.${PACKAGE_SEGMENT}.historical.${EXCHANGE_CAMEL}HistoricalDataProviderProvider
EOF
fi

if [[ "${REGISTER_MODULES}" == "true" ]]; then
    add_module_to_pom "commons/pom.xml" "${COMMON_MODULE}"
    if [[ "${WITH_MARKET_DATA}" == "true" ]]; then
        add_module_to_pom "implementations/market-data-api/pom.xml" "${MARKET_MODULE}"
    fi
    if [[ "${WITH_BROKER}" == "true" ]]; then
        add_module_to_pom "implementations/broker-api/pom.xml" "${BROKER_MODULE}"
    fi
    if [[ "${WITH_HISTORICAL}" == "true" ]]; then
        add_module_to_pom "implementations/historical-data-api/pom.xml" "${HIST_MODULE}"
    fi
fi

if [[ "${UPDATE_EXCHANGE_ENUM}" == "true" ]]; then
    update_exchange_enum_file
else
    log_warn "Exchange enum was not updated. Add Exchange.${EXCHANGE_CONST} manually if needed."
fi

cat <<EOF

Scaffold complete for '${EXCHANGE_SLUG}'.

Next steps:
1. Implement REST + WebSocket logic in:
   - ${COMMON_API_JAVA}/${EXCHANGE_CAMEL}RestApi.java
   - ${COMMON_API_JAVA}/${EXCHANGE_CAMEL}WebSocketApi.java
2. Implement symbol normalization and descriptor hydration in:
   - ${COMMON_BASE_JAVA}/${EXCHANGE_CAMEL}TickerRegistry.java
3. Set real capabilities in:
   - ${COMMON_BASE_JAVA}/${EXCHANGE_CAMEL}ExchangeCapabilitiesProvider.java
4. If you generated implementation modules, fill in TODO methods there.
5. Run:
   mvn test -pl ${COMMON_DIR}${WITH_MARKET_DATA:+,${MARKET_DIR}}${WITH_BROKER:+,${BROKER_DIR}}${WITH_HISTORICAL:+,${HIST_DIR}} -am

EOF
