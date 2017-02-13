package com.mesosphere.sdk.couchbase.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "");
        ENV_VARS.set("LIBMESOS_URI", "");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("FRAMEWORK_NAME", "couchbase");
        ENV_VARS.set("COUCHBASE_VERSION", "4.5.1");
        ENV_VARS.set("COUCHBASE_INSTALLER_LOCATION", "https://packages.couchbase.com/releases/4.5.1/couchbase-server-enterprise-4.5.1-centos7.x86_64.rpm");
        ENV_VARS.set("DATA_SERVICE_COUNT", "1");
        ENV_VARS.set("DATA_SERVICE_CPUS", "4");
        ENV_VARS.set("DATA_SERVICE_MEM", "4096");
        ENV_VARS.set("DATA_SERVICE_MEM_USABLE", "3686");
        ENV_VARS.set("DATA_SERVICE_DISK", "15360");
        ENV_VARS.set("INDEX_SERVICE_COUNT", "5000");        
        ENV_VARS.set("INDEX_SERVICE_CPUS", "1");
        ENV_VARS.set("INDEX_SERVICE_ENABLED","true");
        ENV_VARS.set("INDEX_SERVICE_INDEX_TYPE", "default");
        ENV_VARS.set("INDEX_SERVICE_MEM", "4096");
        ENV_VARS.set("INDEX_SERVICE_MEM_USABLE", "3686");
        ENV_VARS.set("INDEX_SERVICE_DISK", "15360");
        ENV_VARS.set("QUERY_SERVICE_ENABLED", "true");
        ENV_VARS.set("QUERY_SERVICE_COUNT", "1");
        ENV_VARS.set("QUERY_SERVICE_CPUS", "4");
        ENV_VARS.set("QUERY_SERVICE_MEM", "4096");
        ENV_VARS.set("QUERY_SERVICE_DISK", "15360");        
        ENV_VARS.set("SLEEP_DURATION", "1000");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
