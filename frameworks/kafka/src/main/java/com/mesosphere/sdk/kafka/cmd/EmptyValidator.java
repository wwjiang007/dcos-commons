package com.mesosphere.sdk.kafka.cmd;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;


/**
 * Sample configuration validator which validates that a ServiceSpecification's number of PodSpecs
 * and number of tasks within those PodSpecs never go down.
 */
public class EmptyValidator implements ConfigValidator<ServiceSpec> {

    @Override
    public Collection<ConfigValidationError> validate(ServiceSpec nullableOldConfig, ServiceSpec newConfig) {
        return  new ArrayList<>();
    }
}
