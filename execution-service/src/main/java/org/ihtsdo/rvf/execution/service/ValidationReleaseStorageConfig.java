package org.ihtsdo.rvf.execution.service;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="rvf.release.storage")
@EnableAutoConfiguration
public class ValidationReleaseStorageConfig extends ResourceConfiguration {
	
}
