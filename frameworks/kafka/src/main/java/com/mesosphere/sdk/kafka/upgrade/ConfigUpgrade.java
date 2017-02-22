package com.mesosphere.sdk.kafka.upgrade;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.kafka.api.BrokerController;
import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import com.mesosphere.sdk.kafka.api.TopicController;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.kafka.scheduler.KafkaService;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.kafka.api.BrokerController;
import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import com.mesosphere.sdk.kafka.api.TopicController;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Overwrite existing ConfigState
 */
public class ConfigUpgrade {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ConfigUpgrade.class);

    public static void update(ConfigStore configStore, StateStore stateStore) {
        // Generate a ConfigStore<SpecStore> from existing ConfigStore<KafkaSchedulerConfiguration>

        DefaultServiceSpec.newBuilder().name("kafka");

    }

    public static boolean checkUpdate(){
        if (System.getenv("CONFIG_UPGRADE") != null) {
            LOGGER.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n"
                     + "        Kafka Configuration Update Mode !"
                     + "++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
            return true;
        }
        return false;
    }


}
