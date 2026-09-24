package com.claimpilot.claim;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClaimConfig {

    /** The claim form used for second-plan claims (one fictional form for now). */
    @Bean
    FormTemplate secondaryClaimForm() {
        return FormTemplate.classpath(FormTemplate.SECONDARY_CLAIM_FORM);
    }
}
